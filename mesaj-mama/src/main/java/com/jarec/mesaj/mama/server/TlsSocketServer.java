package com.jarec.mesaj.mama.server;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class TlsSocketServer implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(TlsSocketServer.class.getName());
    private final SSLServerSocketFactory socketFactory;
    private final int port;
    private final ClientConnectionHandler connectionHandler;
    private final ExecutorService clientExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final Set<Socket> clientSockets = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final CountDownLatch listening = new CountDownLatch(1);
    private final AtomicReference<ServerSocket> serverSocket = new AtomicReference<>();

    public TlsSocketServer(SSLServerSocketFactory socketFactory, int port,
                           ClientConnectionHandler connectionHandler) {
        if (socketFactory == null || connectionHandler == null || port < 0 || port > 65535) {
            throw new IllegalArgumentException("socketFactory, connectionHandler and a valid port are required");
        }
        this.socketFactory = socketFactory;
        this.port = port;
        this.connectionHandler = connectionHandler;
    }

    public void start() throws IOException {
        if (!started.compareAndSet(false, true) || closed.get()) {
            throw new IllegalStateException("server cannot be started more than once or after closing");
        }
        try (SSLServerSocket listeningSocket =
                     (SSLServerSocket) socketFactory.createServerSocket(port)) {
            serverSocket.set(listeningSocket);
            listening.countDown();
            acceptClients(listeningSocket);
        } finally {
            close();
        }
    }

    private void acceptClients(SSLServerSocket listeningSocket) throws IOException {
        while (!closed.get()) {
            Socket client = acceptClient(listeningSocket);
            if (client != null) {
                dispatchClient(client);
            }
        }
    }

    private Socket acceptClient(SSLServerSocket listeningSocket) throws IOException {
        try {
            return listeningSocket.accept();
        } catch (SocketException exception) {
            if (!closed.get()) {
                throw exception;
            }
            return null;
        }
    }

    private void dispatchClient(Socket client) throws IOException {
        clientSockets.add(client);
        try {
            clientExecutor.execute(() -> handleClient(client));
        } catch (RejectedExecutionException exception) {
            clientSockets.remove(client);
            client.close();
            if (!closed.get()) {
                throw exception;
            }
        }
    }

    private void handleClient(Socket socket) {
        try {
            connectionHandler.handle(socket);
        } catch (IOException | RuntimeException exception) {
            LOGGER.log(exception instanceof IOException ? Level.FINE : Level.WARNING,
                    "Client connection ended with an error", exception);
        } finally {
            clientSockets.remove(socket);
        }
    }

    public int localPort() {
        ServerSocket listeningSocket = serverSocket.get();
        return listeningSocket == null ? port : listeningSocket.getLocalPort();
    }

    public boolean awaitListening(long timeout, TimeUnit unit) throws InterruptedException {
        if (unit == null || timeout < 0) {
            throw new IllegalArgumentException("unit must not be null and timeout must not be negative");
        }
        return listening.await(timeout, unit);
    }

    @Override
    public void close() throws IOException {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        ServerSocket listeningSocket = serverSocket.get();
        IOException closeFailure = null;
        if (listeningSocket != null) {
            try {
                listeningSocket.close();
            } catch (IOException exception) {
                closeFailure = exception;
            }
        }
        for (Socket client : clientSockets) {
            try {
                client.close();
            } catch (IOException exception) {
                if (closeFailure == null) {
                    closeFailure = exception;
                } else {
                    closeFailure.addSuppressed(exception);
                }
            }
        }
        clientExecutor.close();
        if (closeFailure != null) {
            throw closeFailure;
        }
    }
}

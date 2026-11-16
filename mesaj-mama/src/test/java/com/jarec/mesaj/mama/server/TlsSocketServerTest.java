package com.jarec.mesaj.mama.server;

import com.jarec.mesaj.common.PacketDecoder;
import com.jarec.mesaj.common.PacketEncoder;
import com.jarec.mesaj.common.protocol.Login;
import com.jarec.mesaj.common.protocol.LoginSuccess;
import com.jarec.mesaj.mama.auth.AuthService;
import com.jarec.mesaj.mama.auth.User;
import com.jarec.mesaj.mama.auth.UserRepository;
import com.jarec.mesaj.mama.message.InMemoryMessageRepository;
import com.jarec.mesaj.mama.session.SessionRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TlsSocketServerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void authenticatesFiftyConcurrentTlsClientsAndRejectsPlaintext() throws Exception {
        Path keyStorePath = createTestKeyStore();
        char[] password = "test-password".toCharArray();
        SSLContext serverContext = TlsContextFactory.fromPkcs12(keyStorePath, password);
        SSLContext clientContext = trustingTestCertificateContext();
        Map<String, User> users = new ConcurrentHashMap<>();
        UserRepository repository = new UserRepository() {
            @Override
            public User findOrCreate(String username) {
                return users.computeIfAbsent(username, name -> new User(name, UUID.randomUUID()));
            }

            @Override
            public Map<String, String> listUsers() {
                return users.values().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                        User::username, user -> user.permanentUuid().toString()));
            }
        };
        SessionRegistry registry = new SessionRegistry();
        TlsSocketServer server = new TlsSocketServer(serverContext.getServerSocketFactory(), 0,
                new ClientConnectionHandler(new AuthService(repository), registry, new InMemoryMessageRepository()));
        AtomicReference<IOException> serverFailure = new AtomicReference<>();
        Thread serverThread = Thread.ofVirtual().start(() -> {
            try {
                server.start();
            } catch (IOException exception) {
                serverFailure.set(exception);
            }
        });
        int port = waitForPort(server);

        try (var clients = Executors.newVirtualThreadPerTaskExecutor()) {
            List<java.util.concurrent.Future<LoginSuccess>> results =
                    java.util.stream.IntStream.range(0, 50)
                            .mapToObj(index -> clients.submit(() -> login(clientContext, port, "client-" + index)))
                            .toList();
            for (int index = 0; index < results.size(); index++) {
                assertThat(results.get(index).get(15, TimeUnit.SECONDS).username()).isEqualTo("client-" + index);
            }
        } finally {
            try {
                rejectPlaintextConnection(port);
            } finally {
                server.close();
                serverThread.join(TimeUnit.SECONDS.toMillis(10));
            }
        }

        assertThat(serverThread.isAlive()).isFalse();
        assertThat(serverFailure.get()).isNull();
        assertThat(users).hasSize(50).doesNotContainKey("plaintext");
        assertThat(registry.activeUserCount()).isZero();
    }

    private Path createTestKeyStore() throws Exception {
        Path keyStore = temporaryDirectory.resolve("test-keystore.p12");
        String executable = System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool";
        Path keytool = Path.of(System.getProperty("java.home"), "bin", executable);
        Process process = new ProcessBuilder(keytool.toString(), "-genkeypair", "-alias", "mama-test",
                "-keyalg", "RSA", "-keysize", "2048", "-storetype", "PKCS12",
                "-keystore", keyStore.toString(), "-storepass", "test-password",
                "-keypass", "test-password", "-dname", "CN=localhost", "-validity", "1",
                "-noprompt").redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
        return keyStore;
    }

    private static void rejectPlaintextConnection(int port) throws Exception {
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(PacketEncoder.encode(new Login("plaintext")));
            socket.getOutputStream().flush();
            try {
                assertThat(socket.getInputStream().read()).isIn(-1, 21);
            } catch (IOException expectedDisconnect) {
                assertThat(expectedDisconnect).isInstanceOf(IOException.class);
            }
        }
    }

    private static SSLContext trustingTestCertificateContext() throws Exception {
        TrustManager[] trustManagers = {new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] certificates, String authenticationType) {
                // This test server does not request client certificates.
            }

            @Override
            public void checkServerTrusted(X509Certificate[] certificates, String authenticationType) {
                // Trust only the ephemeral self-signed certificate created by this test.
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }};
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, trustManagers, new SecureRandom());
        return context;
    }

    private static int waitForPort(TlsSocketServer server) throws InterruptedException {
        assertThat(server.awaitListening(10, TimeUnit.SECONDS)).isTrue();
        assertThat(server.localPort()).isBetween(1, 65535);
        return server.localPort();
    }

    private static LoginSuccess login(SSLContext clientContext, int port, String username) throws Exception {
        try (SSLSocket socket = (SSLSocket) clientContext.getSocketFactory().createSocket("localhost", port)) {
            socket.startHandshake();
            socket.getOutputStream().write(PacketEncoder.encode(new Login(username)));
            socket.getOutputStream().flush();
            return (LoginSuccess) PacketDecoder.decode(socket.getInputStream());
        }
    }
}

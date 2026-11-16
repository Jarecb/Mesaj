package com.jarec.mesaj.mama.server;

import com.jarec.mesaj.common.PacketDecoder;
import com.jarec.mesaj.common.protocol.GetUsers;
import com.jarec.mesaj.common.protocol.Login;
import com.jarec.mesaj.common.protocol.LoginSuccess;
import com.jarec.mesaj.common.protocol.ProtocolMessage;
import com.jarec.mesaj.common.protocol.UserList;
import com.jarec.mesaj.mama.auth.AuthService;
import com.jarec.mesaj.mama.session.ClientSession;
import com.jarec.mesaj.mama.session.SessionRegistry;

import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.util.Map;
import java.util.UUID;

public final class ClientConnectionHandler {
    private final AuthService authService;
    private final SessionRegistry sessionRegistry;

    public ClientConnectionHandler(AuthService authService, SessionRegistry sessionRegistry) {
        if (authService == null || sessionRegistry == null) {
            throw new IllegalArgumentException("authService and sessionRegistry must not be null");
        }
        this.authService = authService;
        this.sessionRegistry = sessionRegistry;
    }

    public void handle(Socket socket) throws IOException {
        if (socket == null) {
            throw new IllegalArgumentException("socket must not be null");
        }
        ConnectionContext context = null;
        try (socket; DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {
            context = new ConnectionContext(output);
            PacketDispatcher dispatcher = createDispatcher();
            while (!socket.isClosed()) {
                ProtocolMessage request;
                try {
                    request = PacketDecoder.decode(socket.getInputStream());
                } catch (EOFException _) {
                    break;
                }
                context.send(dispatcher.dispatch(request, context));
            }
        } finally {
            if (context != null && context.session() != null) {
                ClientSession session = context.session();
                sessionRegistry.unregister(session.permanentUuid(), session.sessionUuid());
            }
        }
    }

    private PacketDispatcher createDispatcher() {
        return new PacketDispatcher(Map.of(
                Login.class, (message, context) -> handleLogin((Login) message, context),
                GetUsers.class, (_, context) -> handleGetUsers(context)));
    }

    private LoginSuccess handleLogin(Login login, ConnectionContext context) {
        if (context.isAuthenticated()) {
            throw new IllegalStateException("connection is already authenticated");
        }
        LoginSuccess result = authService.login(login);
        ClientSession session = new ClientSession(UUID.fromString(result.permanentUuid()),
                UUID.fromString(result.sessionUuid()), context.output());
        sessionRegistry.register(session);
        context.authenticate(session);
        return result;
    }

    private UserList handleGetUsers(ConnectionContext context) {
        if (!context.isAuthenticated()) {
            throw new IllegalStateException("login is required before requesting the user directory");
        }
        return authService.getUsers();
    }
}

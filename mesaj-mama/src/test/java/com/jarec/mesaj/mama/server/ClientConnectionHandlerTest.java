package com.jarec.mesaj.mama.server;

import com.jarec.mesaj.common.PacketDecoder;
import com.jarec.mesaj.common.PacketEncoder;
import com.jarec.mesaj.common.protocol.GetUsers;
import com.jarec.mesaj.common.protocol.Login;
import com.jarec.mesaj.common.protocol.LoginSuccess;
import com.jarec.mesaj.common.protocol.UserList;
import com.jarec.mesaj.mama.auth.AuthService;
import com.jarec.mesaj.mama.auth.User;
import com.jarec.mesaj.mama.auth.UserRepository;
import com.jarec.mesaj.mama.session.SessionRegistry;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientConnectionHandlerTest {
    @Test
    void handlesLoginAndDirectoryPacketsAndCleansUpDisconnectedSession() throws Exception {
        UserRepository repository = new UserRepository() {
            private final Map<String, User> users = new ConcurrentHashMap<>();

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
        ByteArrayOutputStream requests = new ByteArrayOutputStream();
        requests.write(PacketEncoder.encode(new Login("alex")));
        requests.write(PacketEncoder.encode(new GetUsers()));
        TestSocket socket = new TestSocket(new ByteArrayInputStream(requests.toByteArray()));

        new ClientConnectionHandler(new AuthService(repository), registry).handle(socket);

        ByteArrayInputStream responses = new ByteArrayInputStream(socket.output.toByteArray());
        assertThat(PacketDecoder.decode(responses)).isInstanceOf(LoginSuccess.class);
        assertThat(((UserList) PacketDecoder.decode(responses)).users()).containsKey("alex");
        assertThat(registry.isOnline(UUID.fromString(((LoginSuccess) decodeLogin(socket.output.toByteArray()))
                .permanentUuid()))).isFalse();
        assertThat(socket.closed).isTrue();
    }

    @Test
    void requiresLoginBeforeReturningDirectory() throws Exception {
        ByteArrayOutputStream requests = new ByteArrayOutputStream();
        requests.write(PacketEncoder.encode(new GetUsers()));
        TestSocket socket = new TestSocket(new ByteArrayInputStream(requests.toByteArray()));
        UserRepository repository = new UserRepository() {
            @Override
            public User findOrCreate(String username) {
                return new User(username, UUID.randomUUID());
            }

            @Override
            public Map<String, String> listUsers() {
                return Map.of();
            }
        };

        ClientConnectionHandler handler = new ClientConnectionHandler(
                new AuthService(repository), new SessionRegistry());
        assertThatThrownBy(() -> handler.handle(socket))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("login is required");
        assertThat(socket.closed).isTrue();
    }

    private static Object decodeLogin(byte[] bytes) throws Exception {
        return PacketDecoder.decode(new ByteArrayInputStream(bytes));
    }

    private static final class TestSocket extends Socket {
        private final InputStream input;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private boolean closed;

        private TestSocket(InputStream input) {
            this.input = input;
        }

        @Override
        public InputStream getInputStream() {
            return input;
        }

        @Override
        public OutputStream getOutputStream() {
            return output;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public synchronized void close() {
            closed = true;
        }
    }
}

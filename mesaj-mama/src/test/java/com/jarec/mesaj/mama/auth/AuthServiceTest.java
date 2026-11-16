package com.jarec.mesaj.mama.auth;

import com.jarec.mesaj.common.protocol.Login;
import com.jarec.mesaj.common.protocol.LoginSuccess;
import com.jarec.mesaj.common.protocol.UserList;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class AuthServiceTest {
    @Test
    void repeatedLoginsReusePermanentIdAndIssueFreshSessionIds() {
        InMemoryUserRepository repository = new InMemoryUserRepository();
        AuthService authService = new AuthService(repository);

        LoginSuccess first = authService.login(new Login("alex"));
        LoginSuccess second = authService.login(new Login("alex"));

        assertThat(first.permanentUuid()).isEqualTo(second.permanentUuid());
        assertThat(first.sessionUuid()).isNotEqualTo(second.sessionUuid());
        assertThat(repository.listUsers()).containsOnlyKeys("alex");
    }

    @Test
    void directoryContainsRegisteredAndOfflineUsers() {
        InMemoryUserRepository repository = new InMemoryUserRepository();
        repository.findOrCreate("alex");
        repository.findOrCreate("sam");

        UserList users = new AuthService(repository).getUsers();

        assertThat(users.users()).containsKeys("alex", "sam");
        assertThat(users.users()).hasSize(2);
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    void rejectsMissingRepositoryOrLogin() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AuthService(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new AuthService(new InMemoryUserRepository()).login(null));
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    void mapsValidAndInvalidMongoUserDocuments() {
        UUID id = UUID.randomUUID();

        assertThat(UserDocumentMapper.toUser(new Document("username", "alex")
                .append("permanentUuid", id.toString()))).isEqualTo(new User("alex", id));
        assertThat(UserDocumentMapper.toDocument(new User("alex", id)))
                .containsEntry("username", "alex")
                .containsEntry("permanentUuid", id.toString());
        assertThatIllegalArgumentException().isThrownBy(() -> UserDocumentMapper.toUser(null));
        assertThatIllegalArgumentException().isThrownBy(() -> UserDocumentMapper.toDocument(null));
        assertThatIllegalStateException().isThrownBy(() -> UserDocumentMapper.toUser(new Document("username", "alex")
                .append("permanentUuid", "not-a-uuid")));
    }

    private static final class InMemoryUserRepository implements UserRepository {
        private final ConcurrentHashMap<String, User> users = new ConcurrentHashMap<>();

        @Override
        public User findOrCreate(String username) {
            return users.computeIfAbsent(username, name -> new User(name, UUID.randomUUID()));
        }

        @Override
        public Map<String, String> listUsers() {
            return users.values().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                    User::username, user -> user.permanentUuid().toString()));
        }
    }
}

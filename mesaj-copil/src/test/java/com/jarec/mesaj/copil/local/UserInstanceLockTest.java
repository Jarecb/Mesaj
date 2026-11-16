package com.jarec.mesaj.copil.local;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class UserInstanceLockTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void preventsDuplicateLockButAllowsDifferentUsers() throws IOException {
        try (var _ = UserInstanceLock.tryAcquire(temporaryDirectory, "alex").orElseThrow();
             var _ = UserInstanceLock.tryAcquire(temporaryDirectory, "maria").orElseThrow()) {
            assertThat(UserInstanceLock.tryAcquire(temporaryDirectory, "alex")).isEmpty();
            assertThat(UserInstanceLock.tryAcquire(temporaryDirectory, "maria")).isEmpty();
        }
    }

    @Test
    void releasesLockOnCloseSoTheUserCanOpenAgain() throws IOException {
        UserInstanceLock first = UserInstanceLock.tryAcquire(temporaryDirectory, "alex").orElseThrow();
        first.close();

        Optional<UserInstanceLock> second = UserInstanceLock.tryAcquire(temporaryDirectory, "alex");

        assertThat(second).isPresent();
        second.orElseThrow().close();
    }

    @Test
    void usesDefaultLockDirectoryUnderUserHome() throws IOException {
        String originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", temporaryDirectory.toString());
        try (var _ = UserInstanceLock.tryAcquire("alex").orElseThrow()) {
            try (var lockFiles = Files.list(temporaryDirectory.resolve(".mesaj").resolve("locks"))) {
                assertThat(lockFiles.count()).isEqualTo(1);
            }
        } finally {
            System.setProperty("user.home", originalUserHome);
        }
    }

    @Test
    void usesSafeHashedPathsForArbitraryUsernames() throws IOException {
        try (var _ =
                     UserInstanceLock.tryAcquire(temporaryDirectory, "..\\outside\\alex").orElseThrow()) {
            assertThat(temporaryDirectory.toFile().listFiles())
                    .allSatisfy(file -> assertThat(file.toPath()).hasParentRaw(temporaryDirectory));
        }
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    void rejectsMissingDirectoryOrUsername() {
        assertThatIllegalArgumentException().isThrownBy(() -> UserInstanceLock.tryAcquire(null, "alex"));
        assertThatIllegalArgumentException().isThrownBy(() -> UserInstanceLock.tryAcquire(temporaryDirectory, " "));
    }
}

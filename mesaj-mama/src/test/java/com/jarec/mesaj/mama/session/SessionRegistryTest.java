package com.jarec.mesaj.mama.session;

import org.junit.jupiter.api.Test;

import java.io.DataOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SessionRegistryTest {
    @Test
    void tracksSessionsPerUserAndRemovesEmptyUserEntries() {
        SessionRegistry registry = new SessionRegistry();
        UUID permanentUuid = UUID.randomUUID();
        ClientSession first = session(permanentUuid);
        ClientSession second = session(permanentUuid);

        registry.register(first);
        registry.register(second);

        assertThat(registry.isOnline(permanentUuid)).isTrue();
        assertThat(registry.getActiveSessions(permanentUuid)).containsExactlyInAnyOrder(first, second);

        registry.unregister(permanentUuid, first.sessionUuid());

        assertThat(registry.getActiveSessions(permanentUuid)).containsExactly(second);
        assertThat(registry.activeUserCount()).isOne();

        registry.unregister(permanentUuid, second.sessionUuid());

        assertThat(registry.isOnline(permanentUuid)).isFalse();
        assertThat(registry.activeUserCount()).isZero();
    }

    @Test
    void concurrentRegistrationAndRemovalKeepsEverySessionUntilRemoved() {
        SessionRegistry registry = new SessionRegistry();
        UUID permanentUuid = UUID.randomUUID();
        List<ClientSession> sessions = new ArrayList<>();
        for (int index = 0; index < 200; index++) {
            sessions.add(session(permanentUuid));
        }

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (ClientSession clientSession : sessions) {
                executor.submit(() -> registry.register(clientSession));
            }
        }
        assertThat(registry.getActiveSessions(permanentUuid)).hasSize(200);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (ClientSession clientSession : sessions) {
                executor.submit(() -> registry.unregister(permanentUuid, clientSession.sessionUuid()));
            }
        }
        assertThat(registry.getActiveSessions(permanentUuid)).isEmpty();
        assertThat(registry.activeUserCount()).isZero();
    }

    @Test
    void registeringWhileLastSessionIsRemovedDoesNotLoseTheNewSession() {
        SessionRegistry registry = new SessionRegistry();
        UUID permanentUuid = UUID.randomUUID();

        for (int iteration = 0; iteration < 100; iteration++) {
            ClientSession previous = session(permanentUuid);
            ClientSession replacement = session(permanentUuid);
            registry.register(previous);
            CountDownLatch start = new CountDownLatch(1);
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var removal = executor.submit(() -> {
                    await(start);
                    registry.unregister(permanentUuid, previous.sessionUuid());
                });
                var registration = executor.submit(() -> {
                    await(start);
                    registry.register(replacement);
                });
                start.countDown();
                awaitCompletion(removal);
                awaitCompletion(registration);
            }
            assertThat(registry.getActiveSessions(permanentUuid)).containsExactly(replacement);
            registry.unregister(permanentUuid, replacement.sessionUuid());
        }
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    void rejectsNullSessionsAndIdentifiers() {
        SessionRegistry registry = new SessionRegistry();

        assertThatIllegalArgumentException().isThrownBy(() -> registry.register(null));
        assertThatIllegalArgumentException().isThrownBy(() -> registry.unregister(null, UUID.randomUUID()));
        assertThatIllegalArgumentException().isThrownBy(() -> registry.unregister(UUID.randomUUID(), null));
        assertThatIllegalArgumentException().isThrownBy(() -> registry.getActiveSessions(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new ClientSession(null, UUID.randomUUID(),
                new DataOutputStream(OutputStream.nullOutputStream())));
        assertThatIllegalArgumentException().isThrownBy(() -> new ClientSession(UUID.randomUUID(), null,
                new DataOutputStream(OutputStream.nullOutputStream())));
        assertThatIllegalArgumentException().isThrownBy(() -> new ClientSession(UUID.randomUUID(), UUID.randomUUID(), null));
    }

    private static ClientSession session(UUID permanentUuid) {
        return new ClientSession(permanentUuid, UUID.randomUUID(),
                new DataOutputStream(OutputStream.nullOutputStream()));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("test operation was interrupted", exception);
        }
    }

    private static void awaitCompletion(Future<?> future) {
        try {
            future.get(1, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent registry operation did not complete", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("concurrent registry operation did not complete", exception);
        }
    }
}

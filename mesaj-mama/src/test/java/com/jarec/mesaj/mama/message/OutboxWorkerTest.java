package com.jarec.mesaj.mama.message;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxWorkerTest {
    @Test
    void marksMessagesPublishedOnlyAfterSuccessfulKafkaPublish() {
        StoredMessage message = message();
        TestRepository repository = new TestRepository(message);
        AtomicInteger publishAttempts = new AtomicInteger();
        OutboxWorker worker = new OutboxWorker(repository, _ -> publishAttempts.incrementAndGet());

        worker.publishPendingMessages();

        assertThat(publishAttempts).hasValue(1);
        assertThat(repository.published).containsExactly(message.messageId());
        worker.close();
    }

    @Test
    void leavesFailedMessagesPendingForRetryAndDoesNotAdvanceTheBatch() {
        StoredMessage first = message();
        StoredMessage second = new StoredMessage("second", first.senderPermanentUuid(),
                first.recipientPermanentUuid(), "another message", first.conversationId(), 2, Instant.now());
        TestRepository repository = new TestRepository(first, second);
        AtomicInteger publishAttempts = new AtomicInteger();
        OutboxWorker worker = new OutboxWorker(repository, _ -> {
            publishAttempts.incrementAndGet();
            throw new IOException("broker unavailable");
        });

        worker.publishPendingMessages();

        assertThat(publishAttempts).hasValue(1);
        assertThat(repository.published).isEmpty();
        worker.close();
    }

    private static StoredMessage message() {
        UUID sender = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        return new StoredMessage("first", sender, recipient, "hello",
                ConversationId.between(sender, recipient), 1, Instant.now());
    }

    private static final class TestRepository implements MessageRepository {
        private final List<StoredMessage> pending;
        private final java.util.ArrayList<String> published = new java.util.ArrayList<>();

        private TestRepository(StoredMessage... pending) {
            this.pending = List.of(pending);
        }

        @Override
        public StoredMessage save(UUID senderPermanentUuid, com.jarec.mesaj.common.protocol.SendMessage message) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<StoredMessage> pendingOutbox(int limit) {
            return pending;
        }

        @Override
        public void markPublished(String messageId) {
            published.add(messageId);
        }
    }
}

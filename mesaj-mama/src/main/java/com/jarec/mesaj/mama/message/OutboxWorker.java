package com.jarec.mesaj.mama.message;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class OutboxWorker implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(OutboxWorker.class.getName());
    private static final int BATCH_SIZE = 100;
    private final MessageRepository repository;
    private final MessagePublisher publisher;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean started = new AtomicBoolean();

    public OutboxWorker(MessageRepository repository, MessagePublisher publisher) {
        if (repository == null || publisher == null) {
            throw new IllegalArgumentException("repository and publisher must not be null");
        }
        this.repository = repository;
        this.publisher = publisher;
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("outbox worker has already started");
        }
        executor.scheduleWithFixedDelay(this::publishPendingMessages, 0, 1, TimeUnit.SECONDS);
    }

    void publishPendingMessages() {
        try {
            for (StoredMessage message : repository.pendingOutbox(BATCH_SIZE)) {
                if (!publish(message)) {
                    break;
                }
            }
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Unable to load pending message outbox records", exception);
        }
    }

    private boolean publish(StoredMessage message) {
        try {
            publisher.publish(message);
            repository.markPublished(message.messageId());
            return true;
        } catch (IOException | RuntimeException exception) {
            LOGGER.log(Level.WARNING, exception,
                    () -> "Outbox publish failed for message " + message.messageId() + "; it will be retried");
            return false;
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}

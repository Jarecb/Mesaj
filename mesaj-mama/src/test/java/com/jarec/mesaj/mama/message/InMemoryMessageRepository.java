package com.jarec.mesaj.mama.message;

import com.jarec.mesaj.common.protocol.SendMessage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryMessageRepository implements MessageRepository {
    private final ConcurrentHashMap<String, StoredMessage> messages = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ConversationId, AtomicLong> sequences = new ConcurrentHashMap<>();

    @Override
    public StoredMessage save(UUID senderPermanentUuid, SendMessage message) {
        UUID recipient = UUID.fromString(message.recipientPermanentUuid());
        ConversationId conversationId = ConversationId.between(senderPermanentUuid, recipient);
        StoredMessage existing = messages.get(message.messageId());
        if (existing != null) {
            return existing;
        }
        StoredMessage stored = new StoredMessage(message.messageId(), senderPermanentUuid, recipient, message.text(),
                conversationId, sequences.computeIfAbsent(conversationId, _ -> new AtomicLong()).incrementAndGet(),
                Instant.now());
        StoredMessage raced = messages.putIfAbsent(message.messageId(), stored);
        return raced == null ? stored : raced;
    }

    @Override
    public List<StoredMessage> pendingOutbox(int limit) {
        return new ArrayList<>(messages.values()).stream().limit(limit).toList();
    }

    @Override
    public void markPublished(String messageId) {
        messages.remove(messageId);
    }
}

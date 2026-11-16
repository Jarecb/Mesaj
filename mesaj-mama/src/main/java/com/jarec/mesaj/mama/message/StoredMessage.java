package com.jarec.mesaj.mama.message;

import java.time.Instant;
import java.util.UUID;

public record StoredMessage(
        String messageId,
        UUID senderPermanentUuid,
        UUID recipientPermanentUuid,
        String text,
        ConversationId conversationId,
        long sequenceNumber,
        Instant createdAt) {
    public StoredMessage {
        if (messageId == null || messageId.isBlank()
                || senderPermanentUuid == null || recipientPermanentUuid == null
                || text == null || text.isBlank() || conversationId == null
                || sequenceNumber < 1 || createdAt == null) {
            throw new IllegalArgumentException("all message fields are required and sequenceNumber must be positive");
        }
    }
}

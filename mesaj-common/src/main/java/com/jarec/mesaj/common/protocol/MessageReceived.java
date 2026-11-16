package com.jarec.mesaj.common.protocol;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Acknowledges server acceptance of a message and reports its conversation sequence.
 */
public record MessageReceived(String messageId, String senderPermanentUuid, long sequenceNumber) implements ProtocolMessage {
    public MessageReceived {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("messageId must not be blank");
        }
        if (senderPermanentUuid == null || senderPermanentUuid.isBlank()) {
            throw new IllegalArgumentException("senderPermanentUuid must not be blank");
        }
    }

    @Override
    @JsonIgnore
    public String type() {
        return "MESSAGE_RECEIVED";
    }
}

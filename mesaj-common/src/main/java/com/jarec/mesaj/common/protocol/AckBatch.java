package com.jarec.mesaj.common.protocol;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

public record AckBatch(String recipientPermanentUuid, List<String> messageIds) implements ProtocolMessage {
    public AckBatch {
        if (recipientPermanentUuid == null || recipientPermanentUuid.isBlank()) {
            throw new IllegalArgumentException("recipientPermanentUuid must not be blank");
        }
        messageIds = List.copyOf(messageIds);
    }

    @Override
    @JsonIgnore
    public String type() {
        return "ACK_BATCH";
    }
}

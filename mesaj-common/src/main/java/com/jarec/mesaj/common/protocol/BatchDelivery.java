package com.jarec.mesaj.common.protocol;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

public record BatchDelivery(List<String> messageIds, String recipientPermanentUuid, String senderPermanentUuid) implements ProtocolMessage {
    public BatchDelivery {
        messageIds = List.copyOf(messageIds);
        if (recipientPermanentUuid == null || recipientPermanentUuid.isBlank()) {
            throw new IllegalArgumentException("recipientPermanentUuid must not be blank");
        }
        if (senderPermanentUuid == null || senderPermanentUuid.isBlank()) {
            throw new IllegalArgumentException("senderPermanentUuid must not be blank");
        }
    }

    @Override
    @JsonIgnore
    public String type() {
        return "BATCH_DELIVERY";
    }
}

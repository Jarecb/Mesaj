package com.jarec.mesaj.common.protocol;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record SendMessage(String recipientPermanentUuid, String messageId, String text) implements ProtocolMessage {
    public SendMessage {
        if (recipientPermanentUuid == null || recipientPermanentUuid.isBlank()) {
            throw new IllegalArgumentException("recipientPermanentUuid must not be blank");
        }
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("messageId must not be blank");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
    }

    @Override
    @JsonIgnore
    public String type() {
        return "SEND_MSG";
    }
}

package com.jarec.mesaj.common.protocol;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Reports whether a message has been read within a conversation.
 */
public record ReadReceiptUpdate(String userPermanentUuid, String conversationPartnerUuid, String messageId, boolean read) implements ProtocolMessage {
    public ReadReceiptUpdate {
        if (userPermanentUuid == null || userPermanentUuid.isBlank()) {
            throw new IllegalArgumentException("userPermanentUuid must not be blank");
        }
        if (conversationPartnerUuid == null || conversationPartnerUuid.isBlank()) {
            throw new IllegalArgumentException("conversationPartnerUuid must not be blank");
        }
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("messageId must not be blank");
        }
    }

    @Override
    @JsonIgnore
    public String type() {
        return "READ_RECEIPT_UPDATE";
    }
}

package com.jarec.mesaj.mama.message;

import java.util.UUID;

public record ConversationId(String value) {
    public ConversationId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("conversation id must not be blank");
        }
    }

    public static ConversationId between(UUID first, UUID second) {
        if (first == null || second == null) {
            throw new IllegalArgumentException("participant UUIDs must not be null");
        }
        String firstValue = first.toString();
        String secondValue = second.toString();
        return new ConversationId(firstValue.compareTo(secondValue) <= 0
                ? firstValue + "_" + secondValue
                : secondValue + "_" + firstValue);
    }
}

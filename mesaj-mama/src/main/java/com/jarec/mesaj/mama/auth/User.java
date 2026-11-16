package com.jarec.mesaj.mama.auth;

import java.util.UUID;

public record User(String username, UUID permanentUuid) {
    public User {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        if (permanentUuid == null) {
            throw new IllegalArgumentException("permanentUuid must not be null");
        }
    }
}

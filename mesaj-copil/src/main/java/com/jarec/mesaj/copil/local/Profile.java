package com.jarec.mesaj.copil.local;

import java.util.UUID;

public record Profile(String username, UUID permanentUuid, UUID sessionUuid) {
    public Profile {
        if (username == null || username.isBlank() || permanentUuid == null || sessionUuid == null) {
            throw new IllegalArgumentException("username and profile UUIDs are required");
        }
    }
}

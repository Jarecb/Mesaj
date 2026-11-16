package com.jarec.mesaj.common.protocol;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record LoginSuccess(String username, String permanentUuid, String sessionUuid) implements ProtocolMessage {
    public LoginSuccess {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        if (permanentUuid == null || permanentUuid.isBlank()) {
            throw new IllegalArgumentException("permanentUuid must not be blank");
        }
        if (sessionUuid == null || sessionUuid.isBlank()) {
            throw new IllegalArgumentException("sessionUuid must not be blank");
        }
    }

    @Override
    @JsonIgnore
    public String type() {
        return "LOGIN_SUCCESS";
    }
}

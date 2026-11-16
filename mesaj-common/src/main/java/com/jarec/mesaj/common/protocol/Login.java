package com.jarec.mesaj.common.protocol;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record Login(String username) implements ProtocolMessage {
    public Login {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
    }

    @Override
    @JsonIgnore
    public String type() {
        return "LOGIN";
    }
}

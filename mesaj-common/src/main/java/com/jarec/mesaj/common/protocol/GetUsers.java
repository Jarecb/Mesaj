package com.jarec.mesaj.common.protocol;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Requests the registered user directory from the server.
 */
public record GetUsers() implements ProtocolMessage {
    @Override
    @JsonIgnore
    public String type() {
        return "GET_USERS";
    }
}

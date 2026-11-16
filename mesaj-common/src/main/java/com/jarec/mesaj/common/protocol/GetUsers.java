package com.jarec.mesaj.common.protocol;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record GetUsers() implements ProtocolMessage {
    @Override
    @JsonIgnore
    public String type() {
        return "GET_USERS";
    }
}

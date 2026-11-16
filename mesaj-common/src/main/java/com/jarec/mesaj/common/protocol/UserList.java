package com.jarec.mesaj.common.protocol;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Map;

public record UserList(Map<String, String> users) implements ProtocolMessage {
    public UserList {
        users = Map.copyOf(users);
    }

    @Override
    @JsonIgnore
    public String type() {
        return "USER_LIST";
    }
}

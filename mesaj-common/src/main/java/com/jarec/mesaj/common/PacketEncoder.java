package com.jarec.mesaj.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarec.mesaj.common.protocol.ProtocolMessage;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public final class PacketEncoder {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private PacketEncoder() {
    }

    public static byte[] encode(ProtocolMessage message) throws IOException {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }

        byte[] payload = serialize(message);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(Integer.BYTES + payload.length);
        DataOutputStream output = new DataOutputStream(buffer);
        output.writeInt(payload.length);
        output.write(payload);
        return buffer.toByteArray();
    }

    private static byte[] serialize(ProtocolMessage message) throws JsonProcessingException {
        return OBJECT_MAPPER.writeValueAsBytes(message);
    }
}

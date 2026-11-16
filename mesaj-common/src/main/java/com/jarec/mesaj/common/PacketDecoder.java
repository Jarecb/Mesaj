package com.jarec.mesaj.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarec.mesaj.common.protocol.ProtocolMessage;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Reads length-prefixed JSON frames from a stream and converts them into protocol messages.
 */
public final class PacketDecoder {
    public static final int MAX_FRAME_SIZE = 10 * 1024 * 1024;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private PacketDecoder() {
    }

    public static ProtocolMessage decode(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException("inputStream must not be null");
        }

        DataInputStream dataInput = new DataInputStream(inputStream);
        int payloadLength = dataInput.readInt();
        validateLength(payloadLength);

        byte[] payload = new byte[payloadLength];
        dataInput.readFully(payload);
        return OBJECT_MAPPER.readValue(payload, ProtocolMessage.class);
    }

    private static void validateLength(int payloadLength) {
        if (payloadLength <= 0) {
            throw new IllegalArgumentException("payload length must be positive");
        }
        if (payloadLength > MAX_FRAME_SIZE) {
            throw new IllegalArgumentException("payload length exceeds maximum allowed size");
        }
    }
}

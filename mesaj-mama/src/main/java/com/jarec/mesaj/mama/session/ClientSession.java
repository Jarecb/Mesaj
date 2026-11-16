package com.jarec.mesaj.mama.session;

import com.jarec.mesaj.common.PacketEncoder;
import com.jarec.mesaj.common.protocol.ProtocolMessage;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

public final class ClientSession {
    private final UUID permanentUuid;
    private final UUID sessionUuid;
    private final DataOutputStream output;

    public ClientSession(UUID permanentUuid, UUID sessionUuid, DataOutputStream output) {
        if (permanentUuid == null || sessionUuid == null || output == null) {
            throw new IllegalArgumentException("session identifiers and output must not be null");
        }
        this.permanentUuid = permanentUuid;
        this.sessionUuid = sessionUuid;
        this.output = output;
    }

    public UUID permanentUuid() {
        return permanentUuid;
    }

    public UUID sessionUuid() {
        return sessionUuid;
    }

    public synchronized void send(ProtocolMessage message) throws IOException {
        output.write(PacketEncoder.encode(message));
        output.flush();
    }
}

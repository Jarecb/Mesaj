package com.jarec.mesaj.mama.server;

import com.jarec.mesaj.common.PacketEncoder;
import com.jarec.mesaj.common.protocol.ProtocolMessage;
import com.jarec.mesaj.mama.session.ClientSession;

import java.io.DataOutputStream;
import java.io.IOException;

final class ConnectionContext {
    private final DataOutputStream output;
    private ClientSession session;

    ConnectionContext(DataOutputStream output) {
        this.output = output;
    }

    boolean isAuthenticated() {
        return session != null;
    }

    ClientSession session() {
        return session;
    }

    DataOutputStream output() {
        return output;
    }

    void authenticate(ClientSession clientSession) {
        if (clientSession == null || session != null) {
            throw new IllegalStateException("connection is already authenticated or session is null");
        }
        session = clientSession;
    }

    void send(ProtocolMessage message) throws IOException {
        if (session == null) {
            output.write(PacketEncoder.encode(message));
            output.flush();
            return;
        }
        session.send(message);
    }
}

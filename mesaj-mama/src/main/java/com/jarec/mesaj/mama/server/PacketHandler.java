package com.jarec.mesaj.mama.server;

import com.jarec.mesaj.common.protocol.ProtocolMessage;

import java.io.IOException;

@FunctionalInterface
interface PacketHandler {
    ProtocolMessage handle(ProtocolMessage message, ConnectionContext context) throws IOException;
}

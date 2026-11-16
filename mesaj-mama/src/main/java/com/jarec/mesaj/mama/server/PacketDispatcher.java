package com.jarec.mesaj.mama.server;

import com.jarec.mesaj.common.protocol.ProtocolMessage;

import java.io.IOException;
import java.util.Map;

final class PacketDispatcher {
    private final Map<Class<? extends ProtocolMessage>, PacketHandler> handlers;

    PacketDispatcher(Map<Class<? extends ProtocolMessage>, PacketHandler> handlers) {
        if (handlers == null || handlers.isEmpty()) {
            throw new IllegalArgumentException("handlers must not be null or empty");
        }
        this.handlers = Map.copyOf(handlers);
    }

    ProtocolMessage dispatch(ProtocolMessage message, ConnectionContext context) throws IOException {
        if (message == null || context == null) {
            throw new IllegalArgumentException("message and context must not be null");
        }
        PacketHandler handler = handlers.get(message.getClass());
        if (handler == null) {
            throw new IllegalArgumentException("unsupported packet type: " + message.getClass().getSimpleName());
        }
        return handler.handle(message, context);
    }
}

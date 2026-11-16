package com.jarec.mesaj.mama.server;

import com.jarec.mesaj.common.protocol.MessageReceived;
import com.jarec.mesaj.common.protocol.ProtocolMessage;
import com.jarec.mesaj.common.protocol.SendMessage;
import com.jarec.mesaj.mama.message.MessageRepository;
import com.jarec.mesaj.mama.message.StoredMessage;

final class SendMessageHandler implements PacketHandler {
    private final MessageRepository messageRepository;

    SendMessageHandler(MessageRepository messageRepository) {
        if (messageRepository == null) {
            throw new IllegalArgumentException("messageRepository must not be null");
        }
        this.messageRepository = messageRepository;
    }

    @Override
    public ProtocolMessage handle(ProtocolMessage message, ConnectionContext context) {
        if (!context.isAuthenticated()) {
            throw new IllegalStateException("login is required before sending messages");
        }
        StoredMessage stored = messageRepository.save(context.session().permanentUuid(), (SendMessage) message);
        return new MessageReceived(stored.messageId(),
                stored.senderPermanentUuid().toString(), stored.sequenceNumber());
    }
}

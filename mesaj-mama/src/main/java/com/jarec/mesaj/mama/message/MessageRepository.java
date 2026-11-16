package com.jarec.mesaj.mama.message;

import com.jarec.mesaj.common.protocol.SendMessage;

import java.util.List;
import java.util.UUID;

public interface MessageRepository {
    StoredMessage save(UUID senderPermanentUuid, SendMessage message);

    List<StoredMessage> pendingOutbox(int limit);

    void markPublished(String messageId);
}

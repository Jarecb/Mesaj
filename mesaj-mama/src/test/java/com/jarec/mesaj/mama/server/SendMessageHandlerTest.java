package com.jarec.mesaj.mama.server;

import com.jarec.mesaj.common.protocol.MessageReceived;
import com.jarec.mesaj.common.protocol.SendMessage;
import com.jarec.mesaj.mama.message.ConversationId;
import com.jarec.mesaj.mama.message.MessageRepository;
import com.jarec.mesaj.mama.message.StoredMessage;
import com.jarec.mesaj.mama.session.ClientSession;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SendMessageHandlerTest {
    @Test
    void persistsAuthenticatedSenderMessageAndReturnsItsSequence() {
        UUID sender = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        SendMessage request = new SendMessage(recipient.toString(), "message-1", "hello");
        AtomicInteger saves = new AtomicInteger();
        MessageRepository repository = new MessageRepository() {
            @Override
            public StoredMessage save(UUID senderPermanentUuid, SendMessage message) {
                assertThat(senderPermanentUuid).isEqualTo(sender);
                assertThat(message).isEqualTo(request);
                saves.incrementAndGet();
                return new StoredMessage("message-1", sender, recipient, "hello",
                        ConversationId.between(sender, recipient), 7, Instant.now());
            }

            @Override
            public List<StoredMessage> pendingOutbox(int limit) {
                return List.of();
            }

            @Override
            public void markPublished(String messageId) {
                throw new UnsupportedOperationException();
            }
        };
        ConnectionContext context = authenticatedContext(sender);

        MessageReceived response = (MessageReceived) new SendMessageHandler(repository).handle(request, context);

        assertThat(saves).hasValue(1);
        assertThat(response).isEqualTo(new MessageReceived("message-1", sender.toString(), 7));
    }

    @Test
    void rejectsUnauthenticatedSendWithoutPersisting() {
        AtomicInteger saves = new AtomicInteger();
        MessageRepository repository = new MessageRepository() {
            @Override
            public StoredMessage save(UUID senderPermanentUuid, SendMessage message) {
                saves.incrementAndGet();
                throw new AssertionError("unauthenticated message must not be persisted");
            }

            @Override
            public List<StoredMessage> pendingOutbox(int limit) {
                return List.of();
            }

            @Override
            public void markPublished(String messageId) {
                throw new UnsupportedOperationException();
            }
        };

        SendMessage request = new SendMessage(UUID.randomUUID().toString(), "message-1", "hello");
        ConnectionContext context = new ConnectionContext(new DataOutputStream(new ByteArrayOutputStream()));
        SendMessageHandler handler = new SendMessageHandler(repository);

        assertThatThrownBy(() -> handler.handle(request, context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("login is required");
        assertThat(saves).hasValue(0);
    }

    private static ConnectionContext authenticatedContext(UUID sender) {
        ConnectionContext context = new ConnectionContext(new DataOutputStream(new ByteArrayOutputStream()));
        context.authenticate(new ClientSession(sender, UUID.randomUUID(),
                new DataOutputStream(new ByteArrayOutputStream())));
        return context;
    }
}

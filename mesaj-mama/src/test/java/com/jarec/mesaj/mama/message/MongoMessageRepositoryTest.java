package com.jarec.mesaj.mama.message;

import com.jarec.mesaj.common.protocol.SendMessage;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class MongoMessageRepositoryTest {
    @Test
    @SuppressWarnings("unchecked")
    void storesTheMessageAndPendingOutboxStateTogetherAndReusesMatchingDuplicate() {
        AtomicInteger inserts = new AtomicInteger();
        Document[] storedDocument = new Document[1];
        MongoCollection<Document> messages = (MongoCollection<Document>) Proxy.newProxyInstance(
                MongoCollection.class.getClassLoader(),
                new Class<?>[]{MongoCollection.class},
                (_, method, arguments) -> switch (method.getName()) {
                    case "createIndex" -> "outbox.status_1_conversationId_1_sequenceNumber_1";
                    case "find" -> findIterable(storedDocument);
                    case "insertOne" -> {
                        storedDocument[0] = (Document) arguments[0];
                        inserts.incrementAndGet();
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        MongoDatabase database = (MongoDatabase) Proxy.newProxyInstance(
                MongoDatabase.class.getClassLoader(),
                new Class<?>[]{MongoDatabase.class},
                (_, method, _) -> method.getName().equals("getCollection") ? messages
                        : throwUnsupported(method.getName()));
        MongoMessageRepository repository = new MongoMessageRepository(database, _ -> 12L);
        UUID sender = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        SendMessage request = new SendMessage(recipient.toString(), "msg-1", "hello");

        StoredMessage accepted = repository.save(sender, request);
        StoredMessage duplicate = repository.save(sender, request);
        Document outbox = storedDocument[0].get("outbox", Document.class);

        assertThat(accepted).isEqualTo(duplicate).returns(12L, StoredMessage::sequenceNumber);
        assertThat(inserts).hasValue(1);
        assertThat(storedDocument[0])
                .containsEntry("_id", "msg-1")
                .containsEntry("messageId", "msg-1")
                .containsEntry("senderPermanentUuid", sender.toString())
                .containsEntry("recipientPermanentUuid", recipient.toString())
                .containsEntry("text", "hello")
                .containsEntry("sequenceNumber", 12L)
                .containsKey("createdAt");
        assertThat(storedDocument[0].get("createdAt")).isInstanceOf(Long.class);
        assertThat(outbox).containsEntry("status", "PENDING");
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsInvalidRecipientUuidBeforeAllocatingSequenceOrPersisting() {
        AtomicInteger allocatedSequences = new AtomicInteger();
        MongoCollection<Document> messages = (MongoCollection<Document>) Proxy.newProxyInstance(
                MongoCollection.class.getClassLoader(),
                new Class<?>[]{MongoCollection.class},
                (_, method, _) -> method.getName().equals("createIndex") ? "messages_index"
                        : throwUnsupported(method.getName()));
        MongoDatabase database = (MongoDatabase) Proxy.newProxyInstance(
                MongoDatabase.class.getClassLoader(),
                new Class<?>[]{MongoDatabase.class},
                (_, method, _) -> method.getName().equals("getCollection") ? messages
                        : throwUnsupported(method.getName()));
        MongoMessageRepository repository = new MongoMessageRepository(database, _ -> {
            allocatedSequences.incrementAndGet();
            return 1L;
        });

        assertThatIllegalArgumentException().isThrownBy(
                () -> repository.save(UUID.randomUUID(), new SendMessage("not-a-uuid", "msg-1", "hello")))
                .withMessageContaining("valid UUID");
        assertThat(allocatedSequences).hasValue(0);
    }

    @SuppressWarnings("unchecked")
    private static FindIterable<Document> findIterable(Document[] storedDocument) {
        return (FindIterable<Document>) Proxy.newProxyInstance(
                FindIterable.class.getClassLoader(),
                new Class<?>[]{FindIterable.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "first" -> storedDocument[0];
                    case "sort", "limit" -> proxy;
                    case "into" -> copyDocument(storedDocument[0], (Collection<Document>) arguments[0]);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static Collection<Document> copyDocument(Document document, Collection<Document> destination) {
        if (document != null) {
            destination.add(document);
        }
        return destination;
    }

    private static Object throwUnsupported(String methodName) {
        throw new UnsupportedOperationException(methodName);
    }
}

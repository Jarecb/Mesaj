package com.jarec.mesaj.mama.message;

import com.jarec.mesaj.common.protocol.SendMessage;
import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Sorts.ascending;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.set;

public final class MongoMessageRepository implements MessageRepository {
    private static final int DUPLICATE_KEY_ERROR = 11000;
    private static final String OUTBOX_STATUS_FIELD = "outbox.status";
    private static final String CONVERSATION_ID_FIELD = "conversationId";
    private static final String SEQUENCE_NUMBER_FIELD = "sequenceNumber";
    private final MongoCollection<Document> messages;
    private final SequenceGenerator sequenceGenerator;

    public MongoMessageRepository(MongoDatabase database) {
        this(database, new MongoSequenceGenerator(database));
    }

    public MongoMessageRepository(MongoDatabase database, SequenceGenerator sequenceGenerator) {
        if (database == null || sequenceGenerator == null) {
            throw new IllegalArgumentException("database and sequenceGenerator must not be null");
        }
        messages = database.getCollection("messages");
        messages.createIndex(new Document(OUTBOX_STATUS_FIELD, 1)
                .append(CONVERSATION_ID_FIELD, 1).append(SEQUENCE_NUMBER_FIELD, 1));
        this.sequenceGenerator = sequenceGenerator;
    }

    @Override
    public StoredMessage save(UUID senderPermanentUuid, SendMessage message) {
        if (senderPermanentUuid == null || message == null) {
            throw new IllegalArgumentException("senderPermanentUuid and message must not be null");
        }
        UUID recipientPermanentUuid = parseRecipient(message.recipientPermanentUuid());
        StoredMessage existing = findById(message.messageId());
        if (existing != null) {
            return verifyDuplicate(existing, senderPermanentUuid, recipientPermanentUuid, message.text());
        }

        ConversationId conversationId = ConversationId.between(senderPermanentUuid, recipientPermanentUuid);
        StoredMessage stored = new StoredMessage(message.messageId(), senderPermanentUuid, recipientPermanentUuid,
                message.text(), conversationId, sequenceGenerator.next(conversationId),
                Instant.now().truncatedTo(ChronoUnit.MILLIS));
        try {
            messages.insertOne(toDocument(stored));
            return stored;
        } catch (MongoException exception) {
            if (exception.getCode() != DUPLICATE_KEY_ERROR) {
                throw exception;
            }
            StoredMessage raced = findById(message.messageId());
            if (raced == null) {
                throw exception;
            }
            return verifyDuplicate(raced, senderPermanentUuid, recipientPermanentUuid, message.text());
        }
    }

    @Override
    public List<StoredMessage> pendingOutbox(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return messages.find(eq(OUTBOX_STATUS_FIELD, "PENDING"))
                .sort(ascending(CONVERSATION_ID_FIELD, SEQUENCE_NUMBER_FIELD))
                .limit(limit)
                .into(new ArrayList<>())
                .stream()
                .map(MongoMessageRepository::fromDocument)
                .toList();
    }

    @Override
    public void markPublished(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("messageId must not be blank");
        }
        var result = messages.updateOne(eq("_id", messageId),
                combine(set(OUTBOX_STATUS_FIELD, "PUBLISHED"),
                        set("outbox.publishedAt", Instant.now().toEpochMilli())));
        if (result.getMatchedCount() == 0) {
            throw new IllegalStateException("cannot mark missing message as published: " + messageId);
        }
    }

    private StoredMessage findById(String messageId) {
        Document document = messages.find(eq("_id", messageId)).first();
        return document == null ? null : fromDocument(document);
    }

    private static UUID parseRecipient(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("recipientPermanentUuid must be a valid UUID", exception);
        }
    }

    private static StoredMessage verifyDuplicate(
            StoredMessage existing, UUID senderUuid, UUID recipientUuid, String text) {
        if (!existing.senderPermanentUuid().equals(senderUuid)
                || !existing.recipientPermanentUuid().equals(recipientUuid)
                || !existing.text().equals(text)) {
            throw new IllegalArgumentException("messageId has already been used with different message content");
        }
        return existing;
    }

    private static Document toDocument(StoredMessage message) {
        return new Document("_id", message.messageId())
                .append("messageId", message.messageId())
                .append("senderPermanentUuid", message.senderPermanentUuid().toString())
                .append("recipientPermanentUuid", message.recipientPermanentUuid().toString())
                .append("text", message.text())
                .append(CONVERSATION_ID_FIELD, message.conversationId().value())
                .append(SEQUENCE_NUMBER_FIELD, message.sequenceNumber())
                .append("createdAt", message.createdAt().toEpochMilli())
                .append("outbox", new Document("status", "PENDING"));
    }

    private static StoredMessage fromDocument(Document document) {
        return new StoredMessage(
                document.getString("messageId"),
                UUID.fromString(document.getString("senderPermanentUuid")),
                UUID.fromString(document.getString("recipientPermanentUuid")),
                document.getString("text"),
                new ConversationId(document.getString(CONVERSATION_ID_FIELD)),
                document.getLong(SEQUENCE_NUMBER_FIELD),
                Instant.ofEpochMilli(document.getLong("createdAt")));
    }
}

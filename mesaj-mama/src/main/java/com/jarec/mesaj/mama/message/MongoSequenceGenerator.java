package com.jarec.mesaj.mama.message;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.MongoException;
import org.bson.Document;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.inc;

public final class MongoSequenceGenerator implements SequenceGenerator {
    private final MongoCollection<Document> sequences;

    public MongoSequenceGenerator(MongoDatabase database) {
        if (database == null) {
            throw new IllegalArgumentException("database must not be null");
        }
        sequences = database.getCollection("conversation_sequences");
        sequences.createIndex(new Document("conversationId", 1), new IndexOptions().unique(true));
    }

    @Override
    public long next(ConversationId conversationId) {
        if (conversationId == null) {
            throw new IllegalArgumentException("conversationId must not be null");
        }
        try {
            return sequenceNumber(increment(conversationId, true));
        } catch (MongoException exception) {
            if (exception.getCode() != 11000) {
                throw exception;
            }
            Document result = increment(conversationId, false);
            if (result == null) {
                throw exception;
            }
            return sequenceNumber(result);
        }
    }

    private Document increment(ConversationId conversationId, boolean upsert) {
        return sequences.findOneAndUpdate(
                eq("conversationId", conversationId.value()),
                inc("sequenceNumber", 1L),
                new FindOneAndUpdateOptions().upsert(upsert).returnDocument(ReturnDocument.AFTER));
    }

    private static long sequenceNumber(Document result) {
        if (result == null || !(result.get("sequenceNumber") instanceof Number sequenceNumber)
                || sequenceNumber.longValue() < 1) {
            throw new IllegalStateException("MongoDB did not return a valid conversation sequence");
        }
        return sequenceNumber.longValue();
    }
}

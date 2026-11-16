package com.jarec.mesaj.mama.auth;

import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.ReturnDocument;
import org.bson.Document;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.setOnInsert;

public final class MongoUserRepository implements UserRepository {
    private static final int DUPLICATE_KEY_ERROR = 11000;
    private static final String USERNAME_FIELD = "username";
    private final MongoCollection<Document> users;

    public MongoUserRepository(MongoDatabase database) {
        if (database == null) {
            throw new IllegalArgumentException("database must not be null");
        }
        users = database.getCollection("users");
        users.createIndex(new Document(USERNAME_FIELD, 1), new IndexOptions().unique(true));
    }

    @Override
    public User findOrCreate(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        Document result;
        try {
            result = users.findOneAndUpdate(
                    eq(USERNAME_FIELD, username),
                    combine(setOnInsert(USERNAME_FIELD, username),
                            setOnInsert("permanentUuid", UUID.randomUUID().toString())),
                    new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER));
        } catch (MongoException exception) {
            if (exception.getCode() != DUPLICATE_KEY_ERROR) {
                throw exception;
            }
            result = users.find(eq(USERNAME_FIELD, username)).first();
            if (result == null) {
                throw exception;
            }
        }
        return UserDocumentMapper.toUser(result);
    }

    @Override
    public Map<String, String> listUsers() {
        Map<String, String> result = new TreeMap<>();
        for (Document document : users.find()) {
            User user = UserDocumentMapper.toUser(document);
            result.put(user.username(), user.permanentUuid().toString());
        }
        return Map.copyOf(result);
    }
}

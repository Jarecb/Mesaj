package com.jarec.mesaj.mama.auth;

import org.bson.Document;

import java.util.UUID;

final class UserDocumentMapper {
    private UserDocumentMapper() {
    }

    static User toUser(Document document) {
        if (document == null) {
            throw new IllegalArgumentException("document must not be null");
        }
        try {
            return new User(document.getString("username"), UUID.fromString(document.getString("permanentUuid")));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("MongoDB user document has invalid fields", exception);
        }
    }

    static Document toDocument(User user) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        return new Document("username", user.username()).append("permanentUuid", user.permanentUuid().toString());
    }
}

package com.jarec.mesaj.mama.session;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionRegistry {
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, ClientSession>> sessions =
            new ConcurrentHashMap<>();

    public void register(ClientSession session) {
        if (session == null) {
            throw new IllegalArgumentException("session must not be null");
        }
        sessions.compute(session.permanentUuid(), (_, userSessions) -> {
            ConcurrentHashMap<UUID, ClientSession> activeSessions =
                    userSessions == null ? new ConcurrentHashMap<>() : userSessions;
            activeSessions.put(session.sessionUuid(), session);
            return activeSessions;
        });
    }

    public void unregister(UUID permanentUuid, UUID sessionUuid) {
        if (permanentUuid == null || sessionUuid == null) {
            throw new IllegalArgumentException("session identifiers must not be null");
        }
        sessions.computeIfPresent(permanentUuid, (_, userSessions) -> {
            userSessions.remove(sessionUuid);
            return userSessions.isEmpty() ? null : userSessions;
        });
    }

    public List<ClientSession> getActiveSessions(UUID permanentUuid) {
        if (permanentUuid == null) {
            throw new IllegalArgumentException("permanentUuid must not be null");
        }
        ConcurrentHashMap<UUID, ClientSession> userSessions = sessions.get(permanentUuid);
        return userSessions == null ? List.of() : List.copyOf(userSessions.values());
    }

    public boolean isOnline(UUID permanentUuid) {
        return !getActiveSessions(permanentUuid).isEmpty();
    }

    public int activeUserCount() {
        return sessions.size();
    }
}

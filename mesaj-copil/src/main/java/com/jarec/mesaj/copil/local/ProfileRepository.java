package com.jarec.mesaj.copil.local;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ProfileRepository {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<Profile>> PROFILE_LIST_TYPE = new TypeReference<>() {
    };
    private static final Object SAVE_LOCK = new Object();

    private final Path directory;
    private final Path profilesFile;
    private final Path writeLockFile;

    public ProfileRepository() {
        this(Path.of(System.getProperty("user.home"), ".mesaj"));
    }

    public ProfileRepository(Path directory) {
        if (directory == null) {
            throw new IllegalArgumentException("directory must not be null");
        }
        this.directory = directory;
        profilesFile = directory.resolve("profiles.json");
        writeLockFile = directory.resolve("profiles.lock");
    }

    public void save(Profile profile) throws IOException {
        if (profile == null) {
            throw new IllegalArgumentException("profile must not be null");
        }
        synchronized (SAVE_LOCK) {
            prepareDirectory();
            try (FileChannel channel = FileChannel.open(writeLockFile,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                LocalFilePermissions.restrict(writeLockFile, false);
                try (var _ = channel.lock()) {
                    Map<String, Profile> profiles = readProfiles();
                    profiles.put(profile.username(), profile);
                    writeProfiles(profiles.values().stream()
                            .sorted(Comparator.comparing(Profile::username))
                            .toList());
                }
            }
        }
    }

    public Optional<Profile> findByUsername(String username) throws IOException {
        validateUsername(username);
        return Optional.ofNullable(readProfiles().get(username));
    }

    public List<Profile> listProfiles() throws IOException {
        return readProfiles().values().stream()
                .sorted(Comparator.comparing(Profile::username))
                .toList();
    }

    private Map<String, Profile> readProfiles() throws IOException {
        if (Files.notExists(profilesFile)) {
            return new LinkedHashMap<>();
        }
        LocalFilePermissions.restrict(profilesFile, false);
        List<Profile> profiles = OBJECT_MAPPER.readValue(profilesFile.toFile(), PROFILE_LIST_TYPE);
        if (profiles == null) {
            throw new IOException("Profile file must contain a JSON array");
        }
        Map<String, Profile> result = new LinkedHashMap<>();
        for (Profile profile : profiles) {
            if (profile == null) {
                throw new IOException("Profile file contains a null entry");
            }
            if (result.putIfAbsent(profile.username(), profile) != null) {
                throw new IOException("Profile file contains duplicate usernames");
            }
        }
        return result;
    }

    private void writeProfiles(List<Profile> profiles) throws IOException {
        Path temporaryFile = Files.createTempFile(directory, "profiles-", ".tmp");
        try {
            LocalFilePermissions.restrict(temporaryFile, false);
            OBJECT_MAPPER.writeValue(temporaryFile.toFile(), new ArrayList<>(profiles));
            replaceFile(temporaryFile);
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private void replaceFile(Path temporaryFile) throws IOException {
        try {
            Files.move(temporaryFile, profilesFile, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException _) {
            Files.move(temporaryFile, profilesFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void prepareDirectory() throws IOException {
        Files.createDirectories(directory);
        LocalFilePermissions.restrict(directory, true);
    }

    private static void validateUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
    }
}

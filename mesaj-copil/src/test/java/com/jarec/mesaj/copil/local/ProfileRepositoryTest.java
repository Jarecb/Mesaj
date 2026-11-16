package com.jarec.mesaj.copil.local;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIOException;

class ProfileRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void savesListsAndFindsProfilesAcrossRepositoryInstances() throws IOException {
        ProfileRepository repository = new ProfileRepository(temporaryDirectory);
        Profile alex = profile("alex");
        Profile maria = profile("maria");

        repository.save(maria);
        repository.save(alex);

        assertThat(new ProfileRepository(temporaryDirectory).listProfiles())
                .containsExactly(alex, maria);
        assertThat(repository.findByUsername("alex")).contains(alex);
        assertThat(repository.findByUsername("unknown")).isEmpty();
    }

    @Test
    void savingExistingUsernameReplacesTheSessionUuid() throws IOException {
        ProfileRepository repository = new ProfileRepository(temporaryDirectory);
        Profile firstLogin = profile("alex");
        Profile nextLogin = new Profile("alex", firstLogin.permanentUuid(), UUID.randomUUID());

        repository.save(firstLogin);
        repository.save(nextLogin);

        assertThat(repository.listProfiles()).containsExactly(nextLogin);
    }

    @Test
    void reportsCorruptProfileFileAndDoesNotOverwriteIt() throws IOException {
        Path profilesFile = temporaryDirectory.resolve("profiles.json");
        Files.writeString(profilesFile, "{invalid json");
        ProfileRepository repository = new ProfileRepository(temporaryDirectory);

        assertThatIOException().isThrownBy(repository::listProfiles);
        assertThatIOException().isThrownBy(() -> repository.save(profile("alex")));
        assertThat(Files.readString(profilesFile)).isEqualTo("{invalid json");

        Files.writeString(profilesFile, "null");
        assertThatIOException().isThrownBy(repository::listProfiles);
        assertThatIOException().isThrownBy(() -> repository.save(profile("alex")));
        assertThat(Files.readString(profilesFile)).isEqualTo("null");
    }

    @Test
    void serializesConcurrentWritesFromSeparateRepositoryInstances() throws Exception {
        try (var executor = Executors.newFixedThreadPool(4)) {
            var saves = IntStream.range(0, 12)
                    .mapToObj(index -> executor.submit(() -> {
                        new ProfileRepository(temporaryDirectory).save(profile("user-" + index));
                        return null;
                    }))
                    .toList();
            for (Future<?> save : saves) {
                save.get();
            }
        }

        assertThat(new ProfileRepository(temporaryDirectory).listProfiles()).hasSize(12);
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    void rejectsInvalidProfilesAndLookupNames() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Profile(null, UUID.randomUUID(), UUID.randomUUID()));
        assertThatIllegalArgumentException().isThrownBy(() -> new ProfileRepository(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new ProfileRepository(temporaryDirectory)
                .findByUsername(" "));
    }

    private static Profile profile(String username) {
        return new Profile(username, UUID.randomUUID(), UUID.randomUUID());
    }
}

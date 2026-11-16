# Mesaj Copil

`mesaj-copil` is the desktop client module for Mesaj. Stage 4 currently provides local profile storage and a per-username process lock. The network client, message store, and JavaFX UI are planned but not implemented yet.

## Stage 4: local profiles

`Profile` stores a username, permanent user UUID, and session UUID. `ProfileRepository` persists these records under the current user's home directory:

- `~/.mesaj/profiles.json` — saved profiles
- `~/.mesaj/profiles.lock` — coordinates profile writes across Copil processes

Use the no-argument repository to use the default directory, or pass a directory to its constructor for tests or an alternate location:

```text
ProfileRepository profiles = new ProfileRepository();
profiles.save(new Profile("alex", permanentUuid, sessionUuid));

Optional<Profile> savedProfile = profiles.findByUsername("alex");
List<Profile> allProfiles = profiles.listProfiles();
```

Saving a profile for an existing username updates its UUID values, which allows the session UUID to be refreshed after login. Writes are serialized across processes and replace the JSON file atomically. Invalid or corrupted profile data raises an error and is not overwritten by a save. The profile directory and files are restricted to the current OS user using POSIX permissions or Windows ACLs where supported. The JSON is not encrypted, so this is filesystem protection, not cryptographic protection.

## Stage 4: per-user instance locking

Use `UserInstanceLock.tryAcquire(username)` to lock a username in the default `~/.mesaj/locks` directory. Tests and callers needing another location can pass a lock directory:

```text
Optional<UserInstanceLock> acquired = UserInstanceLock.tryAcquire("alex");
if (acquired.isEmpty()) {
    throw new IllegalStateException("Copil is already running for alex");
}

try (UserInstanceLock lock = acquired.orElseThrow()) {
    // Keep the lock for the lifetime of this user's Copil instance.
}
```

The method returns an empty `Optional` when that username is already locked; different usernames can be locked simultaneously. Lock filenames are SHA-256 hashes of usernames, so usernames cannot escape the lock directory through path characters. The lock is released by `close()` and by a JVM shutdown hook. Lock files remain in place after closing; the operating-system file lock, rather than file existence, indicates whether a user is active.

## Remaining client work

The planned client stages will add a TLS connection and background packet worker, idempotent local message persistence, and a JavaFX UI with recipient discovery and delivery/read status. Until those stages are implemented, Copil does not yet connect to Mama or provide chat functionality.

See the [build plan](../docs/mesaj_build_plan.md) for the full stage status.

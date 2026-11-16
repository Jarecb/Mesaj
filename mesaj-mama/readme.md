# Mesaj Mama

`mesaj-mama` is the backend service for Mesaj. It listens for authenticated TLS clients, validates login requests, tracks active sessions per user, and serves the roster of known users.

This module is the server side of the project and is intentionally narrow: the current implementation focuses on connection handling, authentication, and session tracking. Message persistence, Kafka routing, and client-side chat flows are still planned for later stages.

## What it does

At a high level, Mama does four things:

1. Accepts TLS socket connections from clients.
2. Decodes length-prefixed JSON packets from the wire protocol.
3. Authenticates users and manages active device sessions.
4. Returns the user directory to authenticated clients.

## Startup flow

`MamaApplication` is the startup point. It loads configuration from environment variables:

- `MAMA_PORT`
- `MONGODB_URI`
- `MAMA_TLS_KEYSTORE_PATH`
- `MAMA_TLS_KEYSTORE_PASSWORD`

It then:

- builds a PKCS12 TLS context,
- opens a MongoDB client,
- creates an `AuthService` backed by `MongoUserRepository`,
- creates a `SessionRegistry`,
- starts `TlsSocketServer` with a `ClientConnectionHandler`.

The server uses Java 25 virtual threads to handle each accepted client in its own task, which keeps the connection loop lightweight even with many simultaneous clients.

## Network model

The wire protocol is binary and framed with a simple length prefix. Each packet is:

- 4-byte big-endian payload length
- UTF-8 JSON payload

`PacketDecoder` reads that format and maps the JSON into the `ProtocolMessage` hierarchy. `PacketEncoder` writes the same format back out to clients.

The protocol is built around a sealed interface plus record-based packet types such as:

- `LOGIN`
- `LOGIN_SUCCESS`
- `GET_USERS`
- `USER_LIST`
- `SEND_MSG`
- `MESSAGE_RECEIVED`
- `BATCH_DELIVERY`
- `ACK_BATCH`
- `READ_RECEIPT_UPDATE`

Only the login and user-directory flow are currently implemented.

## Connection handling

When a client connects, `TlsSocketServer` accepts the socket and hands it to a virtual-thread task. `ClientConnectionHandler.handle(...)` then reads one framed packet at a time from the client socket.

Each connection has a `ConnectionContext` that stores:

- the socket output stream,
- whether the client is authenticated,
- the active `ClientSession` once login succeeds.

During the loop, a `PacketDispatcher` looks up the correct handler for the packet class and invokes it.

## Login and auth flow

The current server logic supports `LOGIN` and `GET_USERS`:

1. Client sends `LOGIN` with a username.
2. `AuthService.login(...)` calls `UserRepository.findOrCreate(username)`.
3. `MongoUserRepository` uses a MongoDB collection with a unique index on `username` and an atomic upsert to keep the user stable.
4. The user gets a permanent UUID reused across sessions and a fresh session UUID for that login.
5. The server returns `LOGIN_SUCCESS` containing both UUID values.

This is the important identity split in Mesaj:

- `permanentUuid`: stable identity of the user
- `sessionUuid`: unique identity of that specific connected client instance

A user may have multiple active sessions at the same time, and Mama tracks them separately.

## Session tracking

`SessionRegistry` stores active sessions as a nested map:

- key: permanent user UUID
- value: map of session UUID to `ClientSession`

That means a user can be online from multiple devices at once, and each session has its own outbound stream and login lifecycle.

When a client disconnects, the handler unregisters the session in a `finally` block, removing that socket from the active registry.

## User directory

Once a session is authenticated, a client may send `GET_USERS`.

The handler checks that the client is logged in and then calls `AuthService.getUsers()`, which delegates to `MongoUserRepository.listUsers()`. The repository reads all registered users and returns a map of username -> permanentUuid.

The response is `USER_LIST`, which is used by the client to show a roster of contacts, even when some users are offline.

## Current state of the module

This module already implements the core server foundation for Stage 3 of the project plan:

- TLS server socket
- virtual-thread per client connection
- packet decoding/dispatch
- login flow
- session registry
- authenticated user directory lookup

It does not yet implement:

- persistent message storage in MongoDB
- Kafka-based delivery and retry
- offline batch delivery
- read-receipt synchronization
- multi-device message acknowledgments

Those are planned follow-on stages for `mesaj-mama`.

## Key files

- `MamaApplication.java` — entry point and wiring
- `TlsSocketServer.java` — TLS accept loop and virtual-thread dispatch
- `ClientConnectionHandler.java` — packet read loop and auth routing
- `PacketDispatcher.java` — class-based packet dispatch
- `AuthService.java` — login and user-directory logic
- `MongoUserRepository.java` — MongoDB-backed user store
- `SessionRegistry.java` — active-session tracking
- `ClientSession.java` — per-session connection state

In short, `mesaj-mama` is the Mesaj authentication and session server: it accepts secure client sockets, verifies users, tracks live device sessions, and exposes the roster of registered users.

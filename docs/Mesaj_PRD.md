# Product Requirements Document (PRD): Mesaj Messaging System

## 1. Executive Summary & Overview

**Mesaj** is a real-time, one-to-one desktop messaging platform engineered in **Java 25** using IntelliJ IDEA. The architecture decouples backend state management, session handling, and asynchronous message queuing from the desktop client UI across two independent modules:

* **Module 1: Mama (Backend Server):** A containerized Docker service managing identity resolution, persistent storage (MongoDB), asynchronous message routing (Apache Kafka), and multi-device TCP session routing.

* **Module 2: Copil (Desktop Client):** A JavaFX graphical user interface application providing local profile management, recipient discovery, and robust offline-first message synchronization.

## 2. Technical Stack & Architecture

* **Language & Runtime:** Java 25 (leveraging Virtual Threads for high-concurrency socket handling)

* **IDE:** IntelliJ IDEA (Multi-module project structure: `mesaj-mama`, `mesaj-copil`)

* **Containerization:** Docker & Docker Compose (`Mama`, MongoDB, Apache Kafka broker)

* **Persistence Layer:** MongoDB (NoSQL database for user registry, durable message persistence, and metadata)

* **Message Broker:** Apache Kafka (for durable, order-guaranteed offline message queuing and routing)

* **Networking Protocol:** TLS-Encrypted Custom Length-Prefixed TCP Sockets (`[4-byte Length][UTF-8 JSON Payload]`)

* **Client UI Framework:** JavaFX

## 3. Core Data Models & Schemas

### 3.1 MongoDB User Collection (`mama.users`)

```json
{
  "_id": "ObjectId('65f1a2b3c4d5e6f7a8b9c0d1')",
  "username": "alex",
  "permanentUuid": "550e8400-e29b-41d4-a716-446655440000",
  "createdAt": "2026-03-30T10:00:00Z"
}
```

* **`permanentUuid`**: Generated once upon initial user registration. Acts as the permanent entity identifier and Kafka partition/routing key, ensuring offline messages accumulate reliably regardless of session disconnects.

### 3.2 In-Memory Session Registry (`Mama` Session Manager)

To support multi-device usage, `Mama` maintains a thread-safe active session registry mapping permanent identities to active socket connections:

```java
// ConcurrentHashMap<PermanentUuid, ConcurrentHashMap<SessionUuid, ClientHandler>>
```

* **`sessionUuid`**: A temporary unique ID generated on every individual client connection/login. Allows a single user (`permanentUuid`) to maintain multiple concurrent active sessions (e.g., laptop and desktop clients).

## 4. Functional Requirements: Module 1 (`Mama` Backend Service)

### 4.1 Deployment & Infrastructure (FR-M1)

* Must run as a Docker containerized service, orchestrated alongside MongoDB and Apache Kafka via Docker Compose.

### 4.2 Connection & Authentication Handshake (FR-M2)

* Listen for incoming TLS-secured TCP connections on a designated port (e.g., `9090`).

* Handle incoming `LOGIN` packets:

  * If the `username` is new, generate a new `permanentUuid`, persist it to MongoDB, and issue a temporary `sessionUuid`.

  * If the `username` exists, retrieve the existing `permanentUuid`, generate a fresh `sessionUuid`, and register the active socket channel.

* Acknowledge the handshake with a `LOGIN_SUCCESS` packet containing both UUIDs.

### 4.3 Recipient Directory Service (FR-M3)

* Expose an endpoint/packet type allowing connected clients to fetch the full roster of registered users (`username` $\leftrightarrow$ `permanentUuid` mappings) for recipient selection.

### 4.4 Message Persistence, Ordering & Queuing (FR-M4)

* **Durable Persistence & Server Acknowledgment:** Upon receiving an incoming `SEND_MSG`, `Mama` must first persist the message in MongoDB. Only after successful MongoDB persistence does `Mama` send a `MESSAGE_RECEIVED` acknowledgment back to the sender, decoupling server acceptance from asynchronous recipient delivery.

* **Strict Per-Conversation Ordering:** `Mama` assigns and validates a monotonic sequence number for every message within a given conversation to guarantee strict causal ordering.

* **Kafka Partitioning & Offline Storage:** Route 1-to-1 messages asynchronously through Apache Kafka using the recipient's `permanentUuid` as the partition key. This ensures all messages for a specific user land in the exact same Kafka partition, preserving strict ordering during horizontal scaling while durably retaining messages until delivered and acknowledged.

### 4.5 Real-Time Push & Batch Delivery (FR-M5)

* When a user connects and completes the handshake, query Kafka for all pending offline messages associated with their `permanentUuid`.

* Wrap pending messages into a `BATCH_DELIVERY` packet and push them down all active session sockets for that user.

* Maintain in-flight tracking until explicit `ACK_BATCH` packets containing processed `messageId`s are returned by all registered recipient devices, at which point Kafka offsets are committed and the sender can be notified of full delivery.

### 4.6 Multi-Device Read State Synchronization (FR-M6)

* When an active session marks messages as read, `Mama` persists the updated read state and queries the in-memory session registry for all concurrent `sessionUuid` connections associated with that `permanentUuid`.

* Instantly broadcast a `READ_RECEIPT_UPDATE` packet across all active sessions for that user (as well as the original sender) so read states remain synchronized across every desktop client window in real time.

## 5. Functional Requirements: Module 2 (`Copil` Desktop Client)

### 5.1 Local Profile Management (FR-C1)

* Persist user credentials (`username`, `permanentUuid`, `sessionUuid`) locally on the host machine using secure configuration storage.

* Allow users to select existing profiles or register new names.

### 5.2 Single-Instance Enforcement Per User (FR-C2)

* Allow multiple instances of `Copil` to run concurrently on a single PC, but enforce a local mutual exclusion check (e.g., via file channel locking or local port binding) to prevent the **same username** from running concurrently in multiple windows on the same machine.

### 5.3 Recipient Discovery & Selection (FR-C3)

* Fetch and render the global user directory from `Mama`, enabling users to initiate chats with online or offline peers.

### 5.4 Asynchronous Messaging, Ordering & Storage Idempotency (FR-C4)

* Run network socket readers and message dispatchers off the JavaFX Application Thread using background workers or Java concurrency features.

* **Database-Enforced Idempotency:** Enforce a primary key or unique constraint on `messageId` within `Copil`'s local persistence layer. If a client disconnects prior to sending `ACK_BATCH` and receives redelivered packets upon reconnection, the local database silently ignores duplicate insertions while `Copil` immediately returns a fresh `ACK_BATCH` to clear `Mama`'s queue.

* **Strict Conversation Ordering:** Render messages strictly according to the server-assigned conversation sequence number, ensuring consistent message order even if network retries or asynchronous processing cause out-of-order packet arrival.

### 5.5 Message Delivery Status Indicators (FR-C5)

* Display three distinct delivery states for outgoing messages in the JavaFX UI:

  * **`PENDING`**: Message is queued locally and waiting to be sent/accepted.

  * **`SENT`**: Message has been safely persisted by `Mama` (triggered upon receiving `MESSAGE_RECEIVED`). Note: `SENT` indicates server acceptance, not recipient receipt.

  * **`DELIVERED`**: Message has been acknowledged by all registered recipient devices.

## 6. Non-Functional Requirements (NFR)

* **NFR-1 (Concurrency & Scale):** Utilize Java 25 Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`) in `Mama` to handle thousands of concurrent client connections with minimal OS thread overhead.

* **NFR-2 (Reliability & Durability):** Guarantee at-least-once message delivery using MongoDB persistence acknowledgments (`MESSAGE_RECEIVED`) and application-level `ACK_BATCH` handshakes backed by Kafka and MongoDB persistent volumes.

* **NFR-3 (UI Responsiveness):** Ensure network I/O and database operations never block the JavaFX UI rendering thread.

* **NFR-4 (Transport Security & V1 Data Model):** Encrypt all client-server TCP socket streams using Transport Layer Security (TLS) to protect credentials and payloads in transit. For V1, messages are stored in plaintext within MongoDB and Kafka to facilitate operational visibility and debugging.

## 7. Wire Protocol Specification (Length-Prefixed JSON over TLS)

Every packet transmitted over the TLS-encrypted TCP socket follows this exact binary structure:

1. **Length Prefix:** 4-byte big-endian integer representing the exact byte length of the JSON payload.

2. **Payload:** UTF-8 encoded JSON string matching the `ProtocolMessage` schema.

### Core Packet Types:

* `LOGIN` / `LOGIN_SUCCESS`: Session establishment and credential exchange.

* `GET_USERS` / `USER_LIST`: Directory synchronization.

* `SEND_MSG`: Transmitting a direct message (`messageId`, `senderUuid`, `recipientPermanentUuid`, `payload`, `timestamp`).

* `MESSAGE_RECEIVED`: Server acknowledgment sent to the sender only after `Mama` has durably persisted the message in MongoDB (includes the assigned conversation sequence number).

* `BATCH_DELIVERY`: Pushing queued offline messages (with sequence numbers) upon reconnection.

* `ACK_BATCH`: Client confirmation containing an array of successfully processed `messageId`s.

* `READ_RECEIPT_UPDATE`: Synchronizing message read states across a user's concurrent active sessions and notifying the sender.

## 8. Acceptance Criteria

The product is accepted when the following criteria are met:

* **AC-01 — Deployable services:** Docker Compose starts Mama, MongoDB, and Kafka; Mama becomes healthy only after its dependencies are ready, and MongoDB/Kafka data survives container restarts.
* **AC-02 — Login and sessions:** A first login creates and persists one `permanentUuid`; later logins for the same username reuse it and receive a fresh `sessionUuid`. Every successful session is registered and can be removed cleanly on disconnect.
* **AC-03 — Recipient directory:** An authenticated client can retrieve every registered username and its matching `permanentUuid`, including users who are offline.
* **AC-04 — Multiple devices:** One permanent identity can have multiple concurrent sessions, and removing one session does not remove or disrupt the others.
* **AC-05 — Secure wire protocol:** Client/server traffic uses TLS and the specified 4-byte big-endian length-prefixed UTF-8 JSON framing. Plaintext connections, malformed frames, and frames above the configured size limit are rejected.
* **AC-06 — Durable message acceptance and ordering:** Mama persists each accepted message before returning `MESSAGE_RECEIVED`; a persistence failure produces no success acknowledgment. Mama assigns a strictly increasing sequence number per conversation and clients cannot override it.
* **AC-07 — Kafka routing:** Persisted messages are published asynchronously with the recipient's `permanentUuid` as the Kafka key. Failed publication is retried without losing the durably accepted message.
* **AC-08 — Batch delivery:** On connection, all pending messages for the recipient are sent in `BATCH_DELIVERY` packets to its active sessions, retaining conversation sequence numbers and order.
* **AC-09 — Delivery confirmation:** A message is marked `DELIVERED` and its Kafka offset is committed only after every active session included in that delivery batch acknowledges its processed message IDs. Disconnects before acknowledgment leave messages eligible for redelivery.
* **AC-10 — Idempotent receipt:** Reprocessing a `messageId` does not create a duplicate local message, and the client acknowledges the redelivery so Mama can clear its pending delivery.
* **AC-11 — Delivery status:** Outgoing messages display `PENDING` until server acceptance, `SENT` after `MESSAGE_RECEIVED`, and `DELIVERED` only after the required recipient acknowledgments.
* **AC-12 — Read-state synchronization:** A read update is persisted and broadcast to the user's other active sessions and the original sender.
* **AC-13 — Local profiles:** Users can select existing profiles or register new names; profile identity/session data persists across restarts in secure local configuration storage.
* **AC-14 — Per-user instance lock:** Multiple Copil instances for different usernames may run on one machine, but a second instance for the same username is rejected with a clear error; closing the first instance releases its lock.
* **AC-15 — Responsive client:** Socket reads, writes, persistence, and other network/database operations do not run on the JavaFX Application Thread.
* **AC-16 — Conversation ordering:** Chat history is rendered in server-assigned sequence order, even when packets arrive out of order or are redelivered.
* **AC-17 — Concurrent connections:** Mama uses Java virtual threads for client connection handling and sustains thousands of concurrent TLS client connections in a suitably provisioned load test.
* **AC-18 — At-least-once durability:** A message acknowledged as accepted is not lost across client/server disconnects or service restarts; unacknowledged deliveries are retried.
* **AC-19 — V1 data handling:** Credentials and message payloads are encrypted in transit. Messages remain plaintext in MongoDB and Kafka for V1; this is an explicit product constraint.
* **AC-20 — End-to-end behavior:** Automated multi-client tests verify offline delivery, exact message ordering without duplicates, delivery status transitions, and read-state synchronization across devices.
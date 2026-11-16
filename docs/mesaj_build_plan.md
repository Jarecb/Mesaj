# Mesaj Messaging System — Detailed Build Plan

This build plan breaks down the development of the **Mesaj** messaging system into small, actionable engineering tickets. It is designed to be clear, structured, and easy to follow across all development stages.

The PRD's acceptance criteria (AC-01–AC-20) are the product-level release gates. Ticket-level criteria below implement them; the traceability table in Section 10 identifies the owning tickets.

---

## 1. Engineering Rules & Quality Standards

Every ticket in this plan must follow these strict rules:

* **TDD (Red / Green / Refactor):** You must write a failing unit or integration test first (**Red**), write the simplest code to pass that test (**Green**), and clean up the code while keeping all tests green (**Refactor**).
* **Full Test Coverage (100%):** Every new line, branch, and edge case must be covered by automated tests (verified via JaCoCo).
* **Cognitive Complexity < 10:** Every method must stay simple, flat, and easy to read (verified via SonarLint/Checkstyle). Avoid deep `if/else` nesting or complex loops; extract small helper methods instead.
* **CLEAN & DRY:** Each class and method has one clear job. Shared logic (like wire protocol models and packet readers) lives in a common module so nothing is written twice.

---

## 2. Stage Dependency & Parallel Work Matrix

This table shows the order of work, which tickets block other tickets, and where multiple developers (or workstreams) can build at the same time.

| Stage | Module | Summary | Blockers (Must Finish First) | Can Run in Parallel With |
| :--- | :--- | :--- | :--- | :--- |
| **1** | `mesaj-common` | Multi-module setup, DTOs & Binary Wire Codec | None | None (Core Foundation) |
| **2** | Infrastructure | Docker Compose (MongoDB, Kafka, `Mama` container) | Stage 1 (MSJ-101) | Stage 3, Stage 4 |
| **3** | `mesaj-mama` | TLS Socket Server, Virtual Threads & User Auth | Stage 1 | Stage 2, Stage 4 |
| **4** | `mesaj-copil` | Local Profile Persistence & Single-User File Lock | Stage 1 | Stage 2, Stage 3 |
| **5** | `mesaj-mama` | MongoDB Persistence, Ordering & Kafka Producer | Stage 2, Stage 3 | Stage 6 |
| **6** | `mesaj-copil` | TLS Socket Client & Background Event Dispatcher | Stage 3, Stage 4 | Stage 5 |
| **7** | `mesaj-mama` | Kafka Consumer Batch Push & Read Receipt Sync | Stage 5 | Stage 8 |
| **8** | `mesaj-copil` | Local SQLite/H2 Idempotency & Sequence Sorting | Stage 4, Stage 6 | Stage 7 |
| **9** | `mesaj-copil` | JavaFX MVVM UI & Delivery Status Indicators | Stage 6, Stage 8 | Stage 7 |
| **10** | Full System | End-to-End Multi-Device & Offline Stress Testing | All Stages (1–9) | None (Final Release Gate) |

---

## 3. Detailed Ticket Breakdown

### Stage 1: Shared Foundation (`mesaj-common`)

Implementation notes (2026-11-16): Stage 1 was started and completed in the local workspace. The root Maven project now uses a multi-module layout (`mesaj-common`, `mesaj-mama`, `mesaj-copil`), Java 25, and JaCoCo coverage enforcement. The shared protocol layer was implemented with immutable packet records and a length-prefixed codec. Validation: `mvn test -q` passed successfully for the project state after MSJ-101 through MSJ-103.

#### **MSJ-101: Initialize Multi-Module Java 25 Project & Quality Gates**
* **Goal:** Set up the parent build with `mesaj-common`, `mesaj-mama`, and `mesaj-copil`, plus automated checks for 100% coverage and Cognitive Complexity < 10.
* **Blocker:** None.
* **TDD Cycle:**
  * **Red:** Add a dummy class with a complex nested `if` block (complexity > 10) and an untested method; run the build and verify it fails.
  * **Green:** Configure Maven/Gradle with Java 25, JUnit 5, JaCoCo (set minimum coverage to 1.00), and Sonar/PMD rules (max cognitive complexity = 9). Remove the dummy code so the build passes.
  * **Refactor:** Centralize dependency versions (Jackson, SLF4J, JUnit, Testcontainers) in the root build file (DRY).
* **Acceptance Criteria:** Build fails if test coverage drops below 100% or any method has cognitive complexity $\ge 10$.

#### **MSJ-102: Define Immutable Wire Protocol Packet Records**
* **Goal:** Create clean Java 25 `record` models for every packet type (`LOGIN`, `LOGIN_SUCCESS`, `GET_USERS`, `USER_LIST`, `SEND_MSG`, `MESSAGE_RECEIVED`, `BATCH_DELIVERY`, `ACK_BATCH`, `READ_RECEIPT_UPDATE`).
* **Blocker:** MSJ-101.
* **TDD Cycle:**
  * **Red:** Write Jackson serialization/deserialization unit tests for all 9 packet types, including invalid JSON and missing required fields.
  * **Green:** Implement a sealed `ProtocolMessage` interface and Java `record` classes with `@JsonTypeInfo` polymorphism.
  * **Refactor:** Extract common validation checks (null UUIDs, blank payloads) into compact record constructors.
* **Acceptance Criteria:** All 9 packet types serialize to UTF-8 JSON and deserialize back without data loss, with 100% branch coverage.

#### **MSJ-103: Implement Length-Prefixed Binary Frame Codec**
* **Goal:** Build the encoder/decoder that reads and writes `[4-byte big-endian length][UTF-8 JSON payload]` over streams.
* **Blocker:** MSJ-102.
* **TDD Cycle:**
  * **Red:** Write tests for normal packets, partial/slow stream reads, zero-length frames, oversized frames (e.g., > 10 MB), and sudden stream EOF.
  * **Green:** Implement `PacketEncoder` and `PacketDecoder` using `DataInputStream.readFully()` and `DataOutputStream.writeInt()`.
  * **Refactor:** Split frame size validation and JSON parsing into private helper methods to keep cognitive complexity under 5.
* **Acceptance Criteria:** Codec safely handles fragmented TCP reads and rejects malformed lengths without crashing.

---

### Stage 2: Infrastructure & Container Orchestration

#### **MSJ-201: Docker Compose Setup for MongoDB, Kafka & Mama**
* **Goal:** Configure local containers with persistent storage volumes so offline messages and user records survive restarts.
* **Blocker:** MSJ-101.
* **TDD Cycle:**
  * **Red:** Write a Testcontainers / infrastructure verification test that tries to connect to MongoDB and Kafka on configured ports and fails.
  * **Green:** Create `docker-compose.yml` defining `mongodb`, `kafka` (KRaft mode, no Zookeeper needed), and `mama`, plus a multi-stage `Dockerfile` for `mesaj-mama`.
  * **Refactor:** Move ports, credentials, and topic names into `.env` and add Docker `healthcheck` rules so `mama` waits until MongoDB and Kafka are ready.
* **Acceptance Criteria:** `docker compose up` starts all services cleanly, Mama waits for MongoDB and Kafka health checks, and persistent volumes retain user/message/broker data across restarts.

---

### Stage 3: Backend Core & Authentication (`mesaj-mama`)

#### **MSJ-301: Build Thread-Safe Multi-Device Session Registry**
* **Goal:** Track active client connections using `ConcurrentHashMap<PermanentUuid, ConcurrentHashMap<SessionUuid, ClientSession>>`.
* **Blocker:** MSJ-102.
* **TDD Cycle:**
  * **Red:** Write concurrent unit tests registering 2 sessions for `"alex"`, removing 1 session, looking up all active sessions for `"alex"`, and cleaning up empty outer map entries.
  * **Green:** Implement `SessionRegistry` using atomic `computeIfAbsent` and `computeIfPresent` operations.
  * **Refactor:** Hide raw `ConcurrentHashMap` structures behind clean domain methods (`register`, `unregister`, `getActiveSessions`, `isOnline`).
* **Acceptance Criteria:** Zero race conditions under multi-threaded stress tests; cognitive complexity < 6 per method.

#### **MSJ-302: Implement MongoDB User Repository & Auth Handshake Service**
* **Goal:** Handle `LOGIN` packets: create a new `permanentUuid` for new usernames, reuse existing `permanentUuid` for returning usernames, and generate a new `sessionUuid` on every login.
* **Blocker:** MSJ-102, MSJ-301.
* **TDD Cycle:**
  * **Red:** Write tests for new user registration, returning user login, blank username rejection, and `GET_USERS` directory listing.
  * **Green:** Implement `UserRepository` (MongoDB `mama.users` collection with unique index on `username`) and `AuthService` returning `LOGIN_SUCCESS`; implement the directory handler for `GET_USERS` / `USER_LIST`.
  * **Refactor:** Separate database mapping logic (`UserDocumentMapper`) from authentication rules.
* **Acceptance Criteria:** Duplicate usernames never create duplicate `permanentUuid` entries; each login reuses the permanent UUID and issues a fresh session UUID; `GET_USERS` returns every registered username-to-UUID mapping, including offline users; 100% test coverage.

#### **MSJ-303: Build TLS TCP Server with Java 25 Virtual Threads**
* **Goal:** Accept incoming TLS socket connections on port `9090` and spawn one Virtual Thread per client.
* **Blocker:** MSJ-103, MSJ-301, MSJ-302.
* **TDD Cycle:**
  * **Red:** Write an integration test with self-signed test certificates where 50 TLS clients connect simultaneously, send `LOGIN`, and receive `LOGIN_SUCCESS`.
  * **Green:** Implement `TlsSocketServer` using `SSLServerSocketFactory` and `Executors.newVirtualThreadPerTaskExecutor()`, delegating each connection to `ClientConnectionHandler`.
  * **Refactor:** Extract packet routing into a `PacketDispatcher` map (`Map<Class<? extends ProtocolMessage>, PacketHandler>`) to avoid large `switch` or `if/else` blocks (keeps cognitive complexity < 5).
* **Acceptance Criteria:** Plaintext TCP connections are rejected; TLS connections authenticate cleanly and close sockets properly on disconnect. Connection handling uses virtual threads and passes a configurable load test with thousands of concurrent TLS clients in a suitably provisioned environment.

---

### Stage 4: Client Local Setup & Instance Locking (`mesaj-copil`)

#### **MSJ-401: Implement Local User Profile Storage**
* **Goal:** Save and load local user profiles (`username`, `permanentUuid`, `sessionUuid`) on disk.
* **Blocker:** MSJ-102.
* **TDD Cycle:**
  * **Red:** Write tests for saving a new profile, listing saved profiles, updating `sessionUuid` on re-login, and handling corrupted config files.
  * **Green:** Implement `ProfileRepository` storing profiles in secure local configuration storage, with an injectable storage adapter for tests using temporary directories.
  * **Refactor:** Abstract file system paths so tests run in temporary JUnit `@TempDir` folders without touching real user directories.
* **Acceptance Criteria:** Profiles persist across application restarts and are accessible only to the current OS user; corrupted profiles are reported without silently replacing valid data; 100% unit test coverage.

#### **MSJ-402: Enforce Single-Instance Lock Per Username**
* **Goal:** Allow multiple `Copil` windows on one PC for *different* users, but block opening two windows for the *same* username.
* **Blocker:** MSJ-401.
* **TDD Cycle:**
  * **Red:** Write tests proving `"alex"` acquires a lock, a second lock attempt for `"alex"` fails immediately, `"maria"` acquires a lock at the same time, and releasing `"alex"` allows re-locking.
  * **Green:** Implement `UserInstanceLock` using `FileChannel.tryLock()` on `~/.mesaj/locks/<username>.lock`.
  * **Refactor:** Implement `AutoCloseable` and register a JVM shutdown hook so locks are always released cleanly.
* **Acceptance Criteria:** Running two instances as the same user shows a clear error and exits gracefully without corrupting local data.

---

### Stage 5: Backend Message Persistence & Kafka Routing (`mesaj-mama`)

#### **MSJ-501: Implement MongoDB Message Store & Monotonic Sequence Generator**
* **Goal:** Persist incoming messages durably in MongoDB with a strictly increasing sequence number per 1-to-1 conversation.
* **Blocker:** MSJ-102, MSJ-201.
* **TDD Cycle:**
  * **Red:** Write concurrent tests where 20 threads send messages in the same conversation (`userA` $\leftrightarrow$ `userB`) and verify sequence numbers are strictly `1, 2, 3... 20` with zero duplicates.
  * **Green:** Implement `SequenceGenerator` (using MongoDB atomic `findOneAndUpdate` `$inc` on a deterministic conversation key `min(uuidA, uuidB) + "_" + max(uuidA, uuidB)`), `MessageRepository`, and a durable outbox record written atomically with each accepted message.
  * **Refactor:** Extract conversation key generation into a dedicated `ConversationId` value object.
* **Acceptance Criteria:** Sequence numbers never collide or go out of order under concurrent load; the server assigns sequence numbers and rejects invalid or duplicate message submissions; message and outbox persistence are atomic.

#### **MSJ-502: Publish Messages to Kafka Partitioned by Recipient UUID**
* **Goal:** Route durably saved messages asynchronously to Kafka using `recipientPermanentUuid` as the partition key, and return `MESSAGE_RECEIVED` only after MongoDB persistence succeeds.
* **Blocker:** MSJ-303, MSJ-501.
* **TDD Cycle:**
  * **Red:** Write tests verifying: (1) if MongoDB fails, `MESSAGE_RECEIVED` is NOT sent and nothing is published to Kafka; (2) when MongoDB succeeds, `MESSAGE_RECEIVED` (with sequence number) goes to the sender and Kafka receives the message keyed by `recipientPermanentUuid`.
  * **Green:** Implement `KafkaMessagePublisher` and `SendMessageHandler`; publish pending outbox records asynchronously and retry failures.
  * **Refactor:** Keep handler logic linear (persist $\rightarrow$ acknowledge; independently publish/retry) with cognitive complexity $\le 4$.
* **Acceptance Criteria:** The sender is acknowledged only after MongoDB persistence succeeds, regardless of Kafka timing; persistence failure sends no acknowledgment and publishes nothing; outbox retries prevent accepted messages from being lost; messages for the same recipient always use the same Kafka partition key.

---

### Stage 6: Client Network Worker (`mesaj-copil`)

#### **MSJ-601: Build Non-Blocking TLS Client & Background Packet Loop**
* **Goal:** Connect `Copil` to `Mama` over TLS and read/write packets on Virtual Threads without touching the JavaFX UI thread.
* **Blocker:** MSJ-103, MSJ-303, MSJ-401.
* **TDD Cycle:**
  * **Red:** Write tests against an embedded mock TLS server verifying `connect()`, `send(Packet)`, async listener callbacks on incoming packets, and clean disconnect handling.
  * **Green:** Implement `MesajNetworkClient` with a dedicated Virtual Thread reader loop and a thread-safe outbound write queue.
  * **Refactor:** Decouple socket lifecycle management from packet listener notifications using a simple `PacketEventBus`.
* **Acceptance Criteria:** Network I/O never blocks the caller thread; 100% branch coverage on disconnect/error paths.

---

### Stage 7: Offline Batch Push & Read Receipt Sync (`mesaj-mama`)

#### **MSJ-701: Implement Offline Kafka Batch Delivery & Multi-Device ACK Tracking**
* **Goal:** Push pending offline messages via `BATCH_DELIVERY` on login, and commit Kafka offsets + notify the sender (`DELIVERED`) only after all active recipient devices return `ACK_BATCH`.
* **Blocker:** MSJ-502.
* **TDD Cycle:**
  * **Red:** Write tests for: (1) user logs in with 5 queued messages $\rightarrow$ receives `BATCH_DELIVERY`; (2) user has 2 active devices $\rightarrow$ both must send `ACK_BATCH` before Kafka offset commits and sender gets delivery confirmation; (3) user disconnects before `ACK_BATCH` $\rightarrow$ messages remain uncommitted for next login.
  * **Green:** Implement `PendingAckTracker` and `OfflineDeliveryCoordinator`.
  * **Refactor:** Break multi-device ACK checking into small, pure functions in `PendingAckTracker` to keep cognitive complexity under 7.
* **Acceptance Criteria:** All pending messages are delivered to active sessions in sequence order. Track the active-session cohort for each batch; zero messages are lost on sudden disconnect, and Kafka offsets plus sender delivery confirmation occur only after every session in that cohort acknowledges the message IDs. Disconnect before acknowledgment leaves the delivery eligible for retry.

#### **MSJ-702: Implement Multi-Device Read State Synchronization**
* **Goal:** When one device marks messages as read, update MongoDB and broadcast `READ_RECEIPT_UPDATE` to all other active sessions of that user and the original sender.
* **Blocker:** MSJ-301, MSJ-501.
* **TDD Cycle:**
  * **Red:** Write tests where User B has Device 1 and Device 2 online; Device 1 sends `READ_RECEIPT_UPDATE` $\rightarrow$ MongoDB updates read flag, Device 2 receives `READ_RECEIPT_UPDATE`, and original sender User A receives `READ_RECEIPT_UPDATE`.
  * **Green:** Implement `ReadReceiptService` and `ReadReceiptHandler`.
  * **Refactor:** Reuse `SessionRegistry.broadcastToUser()` helper so broadcast logic is not duplicated (DRY).
* **Acceptance Criteria:** Read status is persisted and stays synchronized across all of the user's open windows and the original sender in real time.

---

### Stage 8: Client Storage Idempotency & Ordering (`mesaj-copil`)

#### **MSJ-801: Build Idempotent Local Message Store & Sequence Sorter**
* **Goal:** Store received and sent messages in an embedded local database (SQLite or H2), ignoring duplicate `messageId`s and sorting strictly by server sequence number.
* **Blocker:** MSJ-401, MSJ-601.
* **TDD Cycle:**
  * **Red:** Write tests verifying: (1) inserting the same `messageId` twice does not throw an error and keeps only 1 row; (2) receiving sequence `#3` before `#2` still returns `[#1, #2, #3]` when querying chat history; (3) updating message status (`PENDING` $\rightarrow$ `SENT` $\rightarrow$ `DELIVERED`).
  * **Green:** Implement `LocalMessageRepository` with `message_id VARCHAR(36) PRIMARY KEY` and `ORDER BY sequence_number ASC`.
  * **Refactor:** Extract SQL row-to-record mapping into a dedicated mapper class.
* **Acceptance Criteria:** Redelivered `BATCH_DELIVERY` packets never create duplicate chat bubbles, and `ACK_BATCH` is sent after local processing even when the message already exists; chat history is ordered by server sequence number.

---

### Stage 9: JavaFX Desktop UI (`mesaj-copil`)

#### **MSJ-901: Build Login & Profile Picker ViewModel and View**
* **Goal:** Let users select an existing local profile or register a new username, enforcing the single-instance lock before connecting.
* **Blocker:** MSJ-402, MSJ-601.
* **TDD Cycle:**
  * **Red:** Write ViewModel unit tests (without starting JavaFX toolkit) for valid login, locked username error state, and network connection failure state.
  * **Green:** Implement `LoginViewModel` and `LoginView` (FXML/JavaFX) binding UI properties cleanly.
  * **Refactor:** Ensure zero network or file lock calls exist inside the JavaFX Controller class.
* **Acceptance Criteria:** UI shows clear status feedback and prevents duplicate logins for the same username.

#### **MSJ-902: Build Chat View, Directory Sidebar & Delivery Status Badges**
* **Goal:** Display the user directory, active conversation messages in strict sequence order, and `PENDING` / `SENT` / `DELIVERED` + `READ` badges.
* **Blocker:** MSJ-801, MSJ-901.
* **TDD Cycle:**
  * **Red:** Write ViewModel tests verifying message lifecycle transitions: user clicks Send (`PENDING`) $\rightarrow$ `MESSAGE_RECEIVED` arrives (`SENT`) $\rightarrow$ recipient acknowledges (`DELIVERED`) $\rightarrow$ `READ_RECEIPT_UPDATE` arrives (`READ`), plus sorting out-of-order incoming messages.
  * **Green:** Implement `ChatViewModel` and `ChatView`, wrapping background updates in `Platform.runLater()` only at the UI boundary.
  * **Refactor:** Extract message cell rendering into a clean custom `ListCell` component with cognitive complexity < 6.
* **Acceptance Criteria:** UI remains smooth and responsive during rapid message bursts; `PENDING`, `SENT`, and `DELIVERED` reflect the specified server/recipient acknowledgments, and read receipts update accurately.

---

### Stage 10: End-to-End Integration & Stress Validation

#### **MSJ-1001: Automated Multi-Client Offline & Concurrency Verification**
* **Goal:** Prove the full system (`Mama` + MongoDB + Kafka + multiple `Copil` instances) satisfies all functional and non-functional requirements.
* **Blocker:** All tickets MSJ-101 through MSJ-902.
* **TDD Cycle:**
  * **Red:** Write an automated end-to-end test suite covering:
    1. User A sends 100 messages while User B is offline.
    2. User B logs in from two separate sessions simultaneously.
    3. Both sessions receive all 100 messages in exact sequence `1..100` with zero duplicates.
    4. User A's messages transition `PENDING` $\rightarrow$ `SENT` $\rightarrow$ `DELIVERED`.
    5. User B reads messages on Device 1 $\rightarrow$ Device 2 and User A immediately show read receipts.
    6. An accepted message survives a Mama restart, and a message not fully acknowledged is redelivered.
    7. A configurable load profile sustains thousands of concurrent TLS client connections in a suitably provisioned environment.
  * **Green:** Resolve any integration edge cases, TLS truststore config issues, or timing bugs uncovered by the suite.
  * **Refactor:** Run full JaCoCo coverage report (100%) and Sonar complexity scan (< 10 across all modules) and finalize `README.md`.
* **Acceptance Criteria:** All end-to-end scenarios pass reliably, test coverage is 100%, and every method in the repository has cognitive complexity < 10.

---

## 10. PRD Acceptance Criteria Traceability

| PRD criterion | Implementing tickets |
| :--- | :--- |
| AC-01 — Deployable services and persistent data | MSJ-201, MSJ-1001 |
| AC-02 — Login and session lifecycle | MSJ-301, MSJ-302, MSJ-303 |
| AC-03 — Recipient directory | MSJ-302, MSJ-303, MSJ-902 |
| AC-04 — Multiple concurrent devices | MSJ-301, MSJ-303, MSJ-701 |
| AC-05 — TLS and framed wire protocol | MSJ-102, MSJ-103, MSJ-303, MSJ-601 |
| AC-06 — Durable acceptance and conversation ordering | MSJ-501, MSJ-502 |
| AC-07 — Asynchronous Kafka routing and retry | MSJ-201, MSJ-501, MSJ-502 |
| AC-08 — Ordered batch delivery | MSJ-701, MSJ-801 |
| AC-09 — Multi-session delivery confirmation | MSJ-701 |
| AC-10 — Idempotent receipt | MSJ-801 |
| AC-11 — Delivery status lifecycle | MSJ-502, MSJ-701, MSJ-801, MSJ-902 |
| AC-12 — Read-state synchronization | MSJ-702, MSJ-902 |
| AC-13 — Secure local profiles | MSJ-401, MSJ-901 |
| AC-14 — Per-user instance lock | MSJ-402, MSJ-901 |
| AC-15 — Non-blocking client | MSJ-601, MSJ-902 |
| AC-16 — Client conversation ordering | MSJ-801, MSJ-902 |
| AC-17 — Concurrent TLS connections | MSJ-303, MSJ-1001 |
| AC-18 — At-least-once durability | MSJ-201, MSJ-501, MSJ-502, MSJ-701, MSJ-1001 |
| AC-19 — V1 transport and storage handling | MSJ-201, MSJ-303, MSJ-601, MSJ-1001 |
| AC-20 — End-to-end behavior | MSJ-1001 |
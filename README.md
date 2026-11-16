# Mesaj

Mesaj is a Java desktop messaging project in development. Its planned architecture pairs **Mama**, a backend server, with **Copil**, a desktop client. The shared `mesaj-common` module contains the wire protocol and framing code used by both.

> **Development status:** The shared foundation is implemented: the Maven multi-module structure, protocol packet records, and length-prefixed JSON codec. The Mama server, Copil application, and supporting infrastructure are not implemented yet, so the project is not currently a usable messaging application.

## Project modules

| Module | Purpose | Status |
| --- | --- | --- |
| `mesaj-common` | Shared protocol records and binary frame codec | Foundation implemented |
| `mesaj-mama` | Backend server, authentication, persistence, and routing | Planned |
| `mesaj-copil` | Desktop client, local profiles, and messaging UI | Planned |

The target system is described in the [product requirements](docs/Mesaj_PRD.md), and implementation work is tracked in the [build plan](docs/mesaj_build_plan.md).

## Technology

- Java 25
- Maven multi-module build
- Jackson for JSON protocol messages
- JUnit 5 and AssertJ for tests
- JaCoCo for code coverage
- Planned: TLS TCP transport, MongoDB, Kafka, Docker Compose, and JavaFX

## Build and test

Install a JDK 25 and Maven, then run commands from the repository root.

JaCoCo 0.8.16 is configured and supports Java 25 class files. Run the unit tests with coverage instrumentation using:

```shell
mvn clean test
```

To run the full verification, including the configured coverage gate:

```shell
mvn clean verify
```

The JaCoCo gate requires 100% line and branch coverage. Unit tests currently pass, but `mvn clean verify` will fail until the shared module's tests meet that threshold.

## Wire protocol

Packets use a four-byte big-endian payload length followed by a UTF-8 JSON `ProtocolMessage`. The decoder rejects empty and oversized frames; the current maximum frame size is 10 MiB.

Supported packet types:

- `LOGIN` and `LOGIN_SUCCESS`
- `GET_USERS` and `USER_LIST`
- `SEND_MSG` and `MESSAGE_RECEIVED`
- `BATCH_DELIVERY` and `ACK_BATCH`
- `READ_RECEIPT_UPDATE`

The shared implementation and its contract tests are in `mesaj-common`.

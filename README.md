# Mesaj

Mesaj is a Java desktop messaging project in development. Its planned architecture pairs **Mama**, a backend server, with **Copil**, a desktop client. The shared `mesaj-common` module contains the wire protocol and framing code used by both.

> **Development status (2026-11-16):** Stages 1–4 are partially implemented. Mama has a TLS server, login, a registered-user directory, and multi-device session tracking; Copil has local profile persistence and per-username instance locking. Docker Compose scaffolding for MongoDB and Kafka is available. The MongoDB repository has not been integration-tested against a live database, the container stack has not been brought up in this environment, and Stages 5–10 remain unimplemented. This is not yet a usable end-to-end messaging application.

## Project modules

| Module | Purpose | Status |
| --- | --- | --- |
| `mesaj-common` | Shared protocol records and binary frame codec | Implemented; contract and coverage gaps remain |
| `mesaj-mama` | Backend server, authentication, persistence, and routing | TLS, login, directory, and in-memory session registry implemented; persistence and message routing remain |
| `mesaj-copil` | Desktop client, local profiles, and messaging UI | Profile persistence and per-user instance lock implemented; network and UI remain |

The [product requirements](docs/Mesaj_PRD.md) describe the target system. The [build plan](docs/mesaj_build_plan.md) tracks stage and ticket status.

## Technology

- Java 25
- Maven multi-module build
- Jackson for JSON protocol messages
- JUnit 5 and AssertJ for tests
- JaCoCo for code coverage
- Stage 2 infrastructure: Docker Compose for MongoDB and Kafka (configuration only; runtime startup not verified)
- Remaining: MongoDB message persistence and Kafka routing, Copil networking, local message storage, and JavaFX UI

## Build and test

Install a JDK 25 and Maven, then run commands from the repository root.

JaCoCo 0.8.16 is configured and supports Java 25 class files. Run all unit tests with:

```shell
mvn clean test
```

The Copil module and its shared dependency tests were verified with:

```shell
mvn --batch-mode --no-transfer-progress -pl mesaj-copil -am test
```

The Stage 3 module and its shared dependency were verified with:

```shell
mvn --batch-mode --no-transfer-progress -pl mesaj-mama -am package
```

This runs the shared-module tests and 13 Mama tests, including a TLS integration test with 50 concurrent logins. Compose configuration also validates with `docker compose --env-file .env.example config -q`. These checks do not verify a live MongoDB repository or start the containers. The configured JaCoCo gate requires 100% line and branch coverage; `mvn clean verify` remains blocked by incomplete shared-module coverage. The planned cognitive-complexity gate is not configured.

## Local infrastructure

Docker Compose provides local MongoDB and Kafka services; Mama is available through the optional `mama` profile. Copy `.env.example` to `.env`, set unique local MongoDB credentials and a keystore password there, then create the PKCS12 TLS keystore at the configured path:

```powershell
Copy-Item .env.example .env
# Set local values in .env. Use the same keystore password in keytool and MAMA_TLS_KEYSTORE_PASSWORD.
New-Item -ItemType Directory -Force .secrets
keytool -genkeypair -alias mama -keyalg RSA -keysize 3072 -storetype PKCS12 `
  -keystore .secrets/mama-keystore.p12 -storepass YOUR_LOCAL_KEYSTORE_PASSWORD `
  -keypass YOUR_LOCAL_KEYSTORE_PASSWORD -dname "CN=localhost" `
  -ext "SAN=dns:localhost,ip:127.0.0.1" -validity 365 -noprompt
# Start MongoDB and Kafka:
docker compose up -d
# To also start Mama:
docker compose --profile mama up --build -d
docker compose --profile mama ps
```

`.env` and `/.secrets/` are ignored by Git; `.env.example` contains placeholders, not usable credentials. Mama requires the PKCS12 keystore and only accepts TLS connections; configure client trust for the certificate before connecting. These development settings are not production-ready: Kafka has no authentication or TLS, and the sample MongoDB account is a local root account. Never commit real credentials. Container startup, MongoDB/Kafka health checks, and volume persistence have not been verified here; see [Local Infrastructure](docs/infrastructure.md) for details.

## Wire protocol

Packets use a four-byte big-endian payload length followed by a UTF-8 JSON `ProtocolMessage`. The decoder rejects empty and oversized frames; the current maximum frame size is 10 MiB.

Supported packet types:

- `LOGIN` and `LOGIN_SUCCESS`
- `GET_USERS` and `USER_LIST`
- `SEND_MSG` and `MESSAGE_RECEIVED`
- `BATCH_DELIVERY` and `ACK_BATCH`
- `READ_RECEIPT_UPDATE`

The shared packet records and frame codec, with their contract tests, are in `mesaj-common`. Mama currently handles `LOGIN` and `GET_USERS`: login reuses a user's permanent UUID and issues a fresh session UUID; authenticated clients can request all registered users, whether online or offline. Active TLS connections are tracked per permanent user UUID. Message sending, persistence, delivery, acknowledgments, read receipts, and the Copil client are not implemented.

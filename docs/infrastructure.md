# Local Infrastructure

Stage 2 provides Docker Compose configuration for a local MongoDB instance, a single-node Kafka broker in KRaft mode, and a Mama container build.

## Requirements

- Docker Engine with Docker Compose v2
- JDK 25 and Maven for builds outside Docker

## Start MongoDB and Kafka

Copy `.env.example` to `.env` and replace the MongoDB username, MongoDB password, and TLS keystore password placeholders with unique local-only values. Create a PKCS12 server keystore at the configured path before starting Mama:

```powershell
Copy-Item .env.example .env
New-Item -ItemType Directory -Force .secrets
keytool -genkeypair -alias mama -keyalg RSA -keysize 3072 -storetype PKCS12 `
  -keystore .secrets/mama-keystore.p12 -storepass YOUR_LOCAL_KEYSTORE_PASSWORD `
  -keypass YOUR_LOCAL_KEYSTORE_PASSWORD -dname "CN=localhost" `
  -ext "SAN=dns:localhost,ip:127.0.0.1" -validity 365 -noprompt
docker compose up -d
docker compose ps
```

MongoDB and Kafka publish ports only on `127.0.0.1`. MongoDB uses the local-development root credentials from `.env`; Kafka is configured without authentication or TLS and is for local development only. Do not expose these ports on an untrusted network.

The root `.gitignore` excludes `.env`, other `.env.*` files, and `/.secrets/` while allowing `.env.example`. The Docker build context also excludes local keystores. Verify locally with `git check-ignore -v .env .secrets/mama-keystore.p12`. Never commit actual credentials or use local development credentials in production.

Compose waits for MongoDB and Kafka health checks. The `kafka-init` one-shot service creates the configured message topic before Mama starts. Inside the Compose network, applications use `mongodb:27017` and `kafka:29092`; host tools use the ports configured in `.env`.

MongoDB and Kafka data persist in the `mongodb-data` and `kafka-data` named volumes. `docker compose down` preserves those volumes. `docker compose down --volumes` deletes them and permanently removes local database and broker data.

## Mama container

The Mama service is in the `mama` Compose profile and builds from `mesaj-mama/Dockerfile`. The Dockerfile packages the Maven modules and runtime dependencies, then runs `com.jarec.mesaj.mama.MamaApplication`. It opens a TLS listener and requires a PKCS12 keystore; `.env` points to a local keystore, mounted read-only into the container.

Start the full stack with:

```shell
docker compose --profile mama up --build -d
docker compose --profile mama ps
```

The service is configured to wait for healthy MongoDB and Kafka topic initialization. Its TCP health check reports healthy after Mama listens on `MAMA_PORT`; it does not perform a TLS handshake. MongoDB credentials are passed through `MONGODB_URI`; URI-reserved characters in credentials must be percent-encoded. For a self-signed local certificate, configure client trust before connecting; this local setup is not intended for production.

On each TLS connection, send `LOGIN` first. The server responds with `LOGIN_SUCCESS` containing the stable permanent user UUID and a new session UUID. Once authenticated, `GET_USERS` returns all registered users, whether they are online or offline. An authenticated `SEND_MSG` is durably stored in MongoDB before Mama returns `MESSAGE_RECEIVED`; a background worker publishes the outbox record to Kafka, keyed by recipient permanent UUID.

## Configuration

`.env.example` lists supported local settings:

| Variable | Default | Description |
| --- | --- | --- |
| `MONGO_ROOT_USERNAME` | Replace placeholder | MongoDB development root username |
| `MONGO_ROOT_PASSWORD` | Replace placeholder | MongoDB development root password |
| `MONGODB_PORT` | `27017` | MongoDB port published to localhost |
| `KAFKA_EXTERNAL_PORT` | `9092` | Kafka port published to localhost |
| `KAFKA_ADVERTISED_HOST` | `localhost` | Host address advertised to clients outside Compose |
| `KAFKA_CLUSTER_ID` | sample cluster ID | Stable identity for this local KRaft broker |
| `KAFKA_MESSAGES_TOPIC` | `mesaj.messages` | Topic created for message routing |
| `KAFKA_TOPIC_PARTITIONS` | `3` | Number of partitions in the message topic |
| `MAMA_PORT` | `9090` | Mama TCP port |
| `MAMA_TLS_KEYSTORE_FILE` | `./.secrets/mama-keystore.p12` | Host path to the Mama PKCS12 TLS keystore |
| `MAMA_TLS_KEYSTORE_PASSWORD` | Replace placeholder | Local keystore password |

This single-broker configuration uses replication factor 1 and plaintext Kafka listeners; it is not suitable for production.

## Current validation limits

Stage 3's automated TLS integration test verifies 50 concurrent TLS login clients. Container health checks, topic initialization, and volume persistence still require running the Compose stack with a local keystore and available container images.

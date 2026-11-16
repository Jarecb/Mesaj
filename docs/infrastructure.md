# Local Infrastructure

Stage 2 provides Docker Compose configuration for a local MongoDB instance, a single-node Kafka broker in KRaft mode, and a Mama container build.

## Requirements

- Docker Engine with Docker Compose v2
- JDK 25 and Maven for builds outside Docker

## Start MongoDB and Kafka

Copy `.env.example` to `.env` and replace the MongoDB username and password placeholders with unique local-only values. The example file intentionally contains no usable credentials. Then start the default infrastructure:

```shell
Copy-Item .env.example .env
docker compose up -d
docker compose ps
```

MongoDB and Kafka publish ports only on `127.0.0.1`. MongoDB uses the local-development root credentials from `.env`; Kafka is configured without authentication or TLS and is for local development only. Do not expose these ports on an untrusted network.

The root `.gitignore` excludes `.env` and other `.env.*` files while allowing `.env.example`. Verify locally with `git check-ignore -v .env`. Never commit actual credentials or use local development credentials in production.

Compose waits for MongoDB and Kafka health checks. The `kafka-init` one-shot service creates the configured message topic before Mama starts. Inside the Compose network, applications use `mongodb:27017` and `kafka:29092`; host tools use the ports configured in `.env`.

MongoDB and Kafka data persist in the `mongodb-data` and `kafka-data` named volumes. `docker compose down` preserves those volumes. `docker compose down --volumes` deletes them and permanently removes local database and broker data.

## Mama container

The Mama service is in the `mama` Compose profile and builds from `mesaj-mama/Dockerfile`. The Dockerfile packages the Maven modules and runs `com.jarec.mesaj.mama.MamaApplication`.

**Stage 3 has not implemented `MamaApplication` yet**, so do not start the `mama` profile until that server entry point exists. When implemented, start the full stack with:

```shell
docker compose --profile mama up --build -d
docker compose --profile mama ps
```

The service is configured to wait for healthy MongoDB and Kafka topic initialization. Its TCP health check will report healthy only after Mama listens on `MAMA_PORT`. Database credentials are passed through `MONGODB_URI`; URI-reserved characters in credentials must be percent-encoded.

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

This single-broker configuration uses replication factor 1 and plaintext Kafka listeners; it is not suitable for production.

## Current validation limits

`docker compose config -q` passed in the implementation environment, confirming the Compose file and local environment-variable interpolation are valid. A subsequent `docker compose up -d` was blocked while pulling the public MongoDB and Kafka images: Docker Hub returned `authentication required - incorrect username or password`. No services started, so health checks, topic initialization, volume persistence, and restart behavior have not been verified. Check Docker Desktop's registry sign-in or stored Docker Hub credentials, then rerun the startup commands above. The Mama image also cannot run until Stage 3 adds the configured Java entry point and server implementation.

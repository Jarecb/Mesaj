package com.jarec.mesaj.mama.server;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

public record MamaConfiguration(
        int port,
        String mongoUri,
        Path tlsKeyStore,
        char[] tlsKeyStorePassword,
        String kafkaBootstrapServers,
        String kafkaMessagesTopic) {
    public MamaConfiguration {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("MAMA_PORT must be between 1 and 65535");
        }
        if (mongoUri == null || mongoUri.isBlank() || tlsKeyStore == null
                || tlsKeyStorePassword == null || tlsKeyStorePassword.length == 0) {
            throw new IllegalArgumentException("MongoDB URI and TLS keystore configuration are required");
        }
        if (kafkaBootstrapServers == null || kafkaBootstrapServers.isBlank()
                || kafkaMessagesTopic == null || kafkaMessagesTopic.isBlank()) {
            throw new IllegalArgumentException("Kafka bootstrap servers and topic are required");
        }
        tlsKeyStorePassword = tlsKeyStorePassword.clone();
    }

    @Override
    public char[] tlsKeyStorePassword() {
        return tlsKeyStorePassword.clone();
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof MamaConfiguration(
                int otherPort,
                String otherMongoUri,
                Path otherTlsKeyStore,
                char[] otherTlsKeyStorePassword,
                String otherKafkaBootstrapServers,
                String otherKafkaMessagesTopic)
                && port == otherPort
                && Objects.equals(mongoUri, otherMongoUri)
                && Objects.equals(tlsKeyStore, otherTlsKeyStore)
                && Arrays.equals(tlsKeyStorePassword, otherTlsKeyStorePassword)
                && Objects.equals(kafkaBootstrapServers, otherKafkaBootstrapServers)
                && Objects.equals(kafkaMessagesTopic, otherKafkaMessagesTopic);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(port, mongoUri, tlsKeyStore, kafkaBootstrapServers, kafkaMessagesTopic);
        return 31 * result + Arrays.hashCode(tlsKeyStorePassword);
    }

    @Override
    public String toString() {
        return "MamaConfiguration[port=" + port + ", mongoUri=[redacted], tlsKeyStore=" + tlsKeyStore
                + ", kafkaBootstrapServers=" + kafkaBootstrapServers + ", kafkaMessagesTopic=" + kafkaMessagesTopic
                + ", tlsKeyStorePassword=[redacted]]";
    }

    public static MamaConfiguration fromEnvironment(Map<String, String> environment) {
        if (environment == null) {
            throw new IllegalArgumentException("environment must not be null");
        }
        return new MamaConfiguration(
                Integer.parseInt(environment.getOrDefault("MAMA_PORT", "9090")),
                required(environment, "MONGODB_URI"),
                Path.of(required(environment, "MAMA_TLS_KEYSTORE_PATH")),
                required(environment, "MAMA_TLS_KEYSTORE_PASSWORD").toCharArray(),
                environment.getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"),
                environment.getOrDefault("KAFKA_MESSAGES_TOPIC", "mesaj.messages"));
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be set");
        }
        return value;
    }
}

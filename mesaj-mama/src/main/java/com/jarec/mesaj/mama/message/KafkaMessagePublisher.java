package com.jarec.mesaj.mama.message;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

public final class KafkaMessagePublisher implements MessagePublisher, AutoCloseable {
    private final KafkaProducer<String, String> producer;
    private final String topic;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KafkaMessagePublisher(String bootstrapServers, String topic) {
        if (bootstrapServers == null || bootstrapServers.isBlank() || topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("Kafka bootstrap servers and topic must not be blank");
        }
        Properties properties = new Properties();
        properties.put("bootstrap.servers", bootstrapServers);
        properties.put("key.serializer", StringSerializer.class.getName());
        properties.put("value.serializer", StringSerializer.class.getName());
        properties.put("acks", "all");
        properties.put("enable.idempotence", "true");
        producer = new KafkaProducer<>(properties);
        this.topic = topic;
    }

    @Override
    public void publish(StoredMessage message) throws IOException {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        String payload = serialize(message);
        ProducerRecord<String, String> producerRecord =
                new ProducerRecord<>(topic, message.recipientPermanentUuid().toString(), payload);
        try {
            producer.send(producerRecord).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while publishing message " + message.messageId(), exception);
        } catch (ExecutionException exception) {
            throw new IOException("failed to publish message " + message.messageId(), exception.getCause());
        }
    }

    private String serialize(StoredMessage message) throws IOException {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "messageId", message.messageId(),
                    "senderPermanentUuid", message.senderPermanentUuid().toString(),
                    "recipientPermanentUuid", message.recipientPermanentUuid().toString(),
                    "text", message.text(),
                    "conversationId", message.conversationId().value(),
                    "sequenceNumber", message.sequenceNumber(),
                    "createdAt", message.createdAt().toString()));
        } catch (JsonProcessingException exception) {
            throw new IOException("failed to serialize message " + message.messageId(), exception);
        }
    }

    @Override
    public void close() {
        producer.close();
    }
}

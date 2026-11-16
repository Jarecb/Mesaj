package com.jarec.mesaj.mama.message;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class MongoSequenceGeneratorTest {
    @Test
    @SuppressWarnings("unchecked")
    void assignsUniqueMonotonicNumbersForConcurrentMessages() throws Exception {
        AtomicLong sequence = new AtomicLong();
        MongoCollection<Document> collection = (MongoCollection<Document>) Proxy.newProxyInstance(
                MongoCollection.class.getClassLoader(),
                new Class<?>[]{MongoCollection.class},
                (_, method, _) -> switch (method.getName()) {
                    case "createIndex" -> "conversationId_1";
                    case "findOneAndUpdate" -> new Document("sequenceNumber", sequence.incrementAndGet());
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        MongoDatabase database = (MongoDatabase) Proxy.newProxyInstance(
                MongoDatabase.class.getClassLoader(),
                new Class<?>[]{MongoDatabase.class},
                (_, method, _) -> method.getName().equals("getCollection") ? collection
                        : throwUnsupported(method.getName()));
        MongoSequenceGenerator generator = new MongoSequenceGenerator(database);
        ConversationId conversation = ConversationId.between(UUID.randomUUID(), UUID.randomUUID());

        try (var executor = Executors.newFixedThreadPool(20)) {
            List<Long> results = executor.invokeAll(
                            java.util.stream.IntStream.range(0, 20)
                                    .<java.util.concurrent.Callable<Long>>mapToObj(
                                            _ -> () -> generator.next(conversation))
                                    .toList())
                    .stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .toList();

            assertThat(results).containsExactlyInAnyOrder(
                    1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L,
                    11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L, 20L);
        }
    }

    private static Object throwUnsupported(String methodName) {
        throw new UnsupportedOperationException(methodName);
    }
}

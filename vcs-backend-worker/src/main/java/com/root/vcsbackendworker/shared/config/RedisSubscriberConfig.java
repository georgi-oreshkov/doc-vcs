package com.root.vcsbackendworker.shared.config;

import com.root.vcsbackendworker.shared.messaging.WorkerTaskStreamListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;

import java.time.Duration;

/**
 * Configures Redis Stream consumption for the worker.
 * <p>
 * The backend publishes tasks via {@code XADD} to the jobs stream.
 * This config creates a consumer group (idempotent) and registers a
 * {@link StreamMessageListenerContainer} with {@code concurrency} concurrent
 * consumers, each running on its own virtual thread.
 * <p>
 * Each consumer independently calls {@code XREADGROUP} — Redis ensures every
 * message is delivered to exactly one consumer within the group.
 * ACK-after-processing semantics are preserved: the listener calls {@code XACK}
 * only after the use case completes (success or failure).
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(WorkerRedisProperties.class)
public class RedisSubscriberConfig {

    private final WorkerRedisProperties workerRedisProperties;

    /**
     * Ensures the consumer group exists on the stream before the listener starts.
     * Idempotent — handles the "BUSYGROUP Consumer Group name already exists" error.
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.worker.redis", name = "listener-enabled", havingValue = "true", matchIfMissing = true)
    ApplicationRunner ensureConsumerGroup(StringRedisTemplate redisTemplate) {
        return args -> {
            String streamKey = workerRedisProperties.stream();
            String group = workerRedisProperties.consumerGroup();
            try {
                redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.from("0"), group);
                log.info("Created consumer group '{}' on stream '{}'", group, streamKey);
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("BUSYGROUP")) {
                    log.debug("Consumer group '{}' already exists on stream '{}'", group, streamKey);
                } else {
                    log.warn("Could not create consumer group '{}' on stream '{}': {}",
                            group, streamKey, e.getMessage());
                }
            }
        };
    }

    @Bean(destroyMethod = "stop")
    @ConditionalOnProperty(prefix = "app.worker.redis", name = "listener-enabled", havingValue = "true", matchIfMissing = true)
    StreamMessageListenerContainer<String, MapRecord<String, String, String>> streamListenerContainer(
            RedisConnectionFactory connectionFactory,
            WorkerTaskStreamListener listener) {

        // Virtual-thread executor — each consumer subscription gets its own lightweight thread.
        // Ideal for this worker: most time is spent on I/O (S3 fetches, DB queries).
        var executor = new SimpleAsyncTaskExecutor("worker-");
        executor.setVirtualThreads(true);

        var options = StreamMessageListenerContainer
                .StreamMessageListenerContainerOptions.<String, MapRecord<String, String, String>>builder()
                .pollTimeout(Duration.ofSeconds(2))
                .executor(executor)
                .build();

        var container = StreamMessageListenerContainer.create(connectionFactory, options);

        // Register N concurrent consumers within the same consumer group.
        // Each runs its own XREADGROUP loop on a virtual thread.
        // Redis distributes messages across consumers — no duplicates.
        String baseName = workerRedisProperties.consumerName();
        int concurrency = workerRedisProperties.concurrency();
        for (int i = 0; i < concurrency; i++) {
            container.receive(
                    Consumer.from(workerRedisProperties.consumerGroup(), baseName + "-" + i),
                    StreamOffset.create(workerRedisProperties.stream(), ReadOffset.lastConsumed()),
                    listener);
        }

        container.start();
        log.info("Started {} concurrent stream consumers on stream '{}', group '{}'",
                concurrency, workerRedisProperties.stream(), workerRedisProperties.consumerGroup());
        return container;
    }
}

package com.root.vcsbackend.shared.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Configures Redis infrastructure for communicating with the
 * {@code vcs-backend-worker} service.
 *
 * <p>{@link StringRedisTemplate} is used by
 * {@link com.root.vcsbackend.shared.redis.DiffTaskPublisher} to publish task
 * messages to the jobs stream via {@code XADD}. The worker reads with
 * {@code XREADGROUP} from the {@value #WORKER_CONSUMER_GROUP} consumer group.
 *
 * <p>The worker writes results directly to PostgreSQL; the backend learns
 * about them via the {@code pg_notify} trigger on the {@code notifications}
 * table — no Redis results channel/stream is needed.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(RedisProperties.class)
public class RedisConfig {

    /** Consumer group name — must match the worker's XREADGROUP group parameter. */
    public static final String WORKER_CONSUMER_GROUP = "workers";

    @Bean
    StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * Ensures the Redis Stream and consumer group exist before any task is published.
     * {@code MKSTREAM} creates the stream key if absent; the catch block handles
     * the "consumer group already exists" error (idempotent on restart).
     */
    @Bean
    ApplicationRunner ensureWorkerConsumerGroup(
            StringRedisTemplate redisTemplate,
            RedisProperties redisProperties) {
        return args -> {
            String streamKey = redisProperties.diffJobsStream();
            try {
                redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.from("0"), WORKER_CONSUMER_GROUP);
                log.info("Created consumer group '{}' on stream '{}'", WORKER_CONSUMER_GROUP, streamKey);
            } catch (Exception e) {
                // "BUSYGROUP Consumer Group name already exists" — expected on restart
                if (e.getMessage() != null && e.getMessage().contains("BUSYGROUP")) {
                    log.debug("Consumer group '{}' already exists on stream '{}'", WORKER_CONSUMER_GROUP, streamKey);
                } else {
                    log.warn("Could not create consumer group '{}' on stream '{}': {}",
                            WORKER_CONSUMER_GROUP, streamKey, e.getMessage());
                }
            }
        };
    }
}

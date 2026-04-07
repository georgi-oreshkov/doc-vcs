package com.root.vcsbackend.shared.config;

import com.root.vcsbackend.shared.redis.DiffResultListener;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Configures Redis Pub/Sub infrastructure for communicating with the
 * {@code vcs-backend-worker} service.
 *
 * <ul>
 *   <li>{@link StringRedisTemplate} — used by {@link com.root.vcsbackend.shared.redis.DiffTaskPublisher}
 *       to publish task messages to the jobs channel.</li>
 *   <li>{@link RedisMessageListenerContainer} — subscribes to the results channel
 *       and delegates incoming messages to {@link DiffResultListener}.</li>
 * </ul>
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(RedisProperties.class)
public class RedisConfig {

    private final RedisProperties redisProperties;

    @Bean
    StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            DiffResultListener diffResultListener
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
                diffResultListener,
                new ChannelTopic(redisProperties.diffResultsChannel())
        );
        return container;
    }
}

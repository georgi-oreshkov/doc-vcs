package com.root.vcsbackendworker.messaging.config;

import com.root.vcsbackendworker.messaging.inbound.DiffJobRedisListener;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(WorkerRedisProperties.class)
public class RedisSubscriberConfig {

    private final WorkerRedisProperties workerRedisProperties;

    @Bean
    @ConditionalOnProperty(prefix = "app.worker.redis", name = "listener-enabled", havingValue = "true", matchIfMissing = true)
    RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory redisConnectionFactory,
            DiffJobRedisListener diffJobRedisListener
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener(diffJobRedisListener, new ChannelTopic(workerRedisProperties.channel()));
        return container;
    }
}


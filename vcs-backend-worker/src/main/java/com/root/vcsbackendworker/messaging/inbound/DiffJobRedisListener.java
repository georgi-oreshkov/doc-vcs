package com.root.vcsbackendworker.messaging.inbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.root.vcsbackendworker.messaging.application.DiffJobHandler;
import com.root.vcsbackendworker.messaging.contract.inbound.DiffJobMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class DiffJobRedisListener implements MessageListener {

    private final ObjectMapper objectMapper;
    private final DiffJobHandler diffJobHandler;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String payload = StringRedisSerializer.UTF_8.deserialize(message.getBody());

        try {
            DiffJobMessage jobMessage = objectMapper.readValue(payload, DiffJobMessage.class);
            diffJobHandler.handle(jobMessage);
        } catch (Exception ex) {
            log.error("Failed to parse/process Redis message on channel {}. Payload={}", channel, payload, ex);
        }
    }
}


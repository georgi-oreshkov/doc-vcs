package com.root.vcsbackendworker.workerjob.inbound.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.root.vcsbackendworker.workerjob.api.inbound.WorkerTaskMessage;
import com.root.vcsbackendworker.workerjob.application.WorkerTaskDispatcher;
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
public class WorkerTaskRedisListener implements MessageListener {

    private final ObjectMapper objectMapper;
    private final WorkerTaskDispatcher workerTaskDispatcher;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String payload = StringRedisSerializer.UTF_8.deserialize(message.getBody());

        try {
            WorkerTaskMessage workerTaskMessage = objectMapper.readValue(payload, WorkerTaskMessage.class);
            workerTaskDispatcher.dispatch(workerTaskMessage);
        } catch (Exception ex) {
            log.error("Failed to parse/process Redis worker task on channel {}. Payload={}", channel, payload, ex);
        }
    }
}




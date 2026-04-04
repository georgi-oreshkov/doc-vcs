package com.root.vcsbackendworker.shared.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.root.vcsbackendworker.shared.config.WorkerRedisProperties;
import com.root.vcsbackendworker.shared.messaging.inbound.VerifyTaskMessage;
import com.root.vcsbackendworker.shared.messaging.outbound.ProcessingStatus;
import com.root.vcsbackendworker.shared.messaging.outbound.ReconstructionResultMessage;
import com.root.vcsbackendworker.shared.messaging.outbound.VerificationResultMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkerResultPublisherTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock WorkerRedisProperties properties;

    WorkerResultPublisher publisher;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        // lenient: only publish() tests invoke resultChannel(); buildMetadata() tests do not
        lenient().when(properties.resultChannel()).thenReturn("vcs.diff.results");
        publisher = new WorkerResultPublisher(redisTemplate, objectMapper, properties);
    }

    // ── buildMetadata ─────────────────────────────────────────────────────────

    @Test
    void buildMetadata_preservesCorrelationId() {
        UUID correlationId = UUID.randomUUID();
        VerifyTaskMessage task = VerifyTaskMessage.builder()
                .metadata(MessageMetadata.builder().correlationId(correlationId).build())
                .build();

        MessageMetadata metadata = publisher.buildMetadata(task);

        assertThat(metadata.getCorrelationId()).isEqualTo(correlationId);
    }

    @Test
    void buildMetadata_stampsProducer() {
        VerifyTaskMessage task = VerifyTaskMessage.builder()
                .metadata(MessageMetadata.builder().build())
                .build();

        MessageMetadata metadata = publisher.buildMetadata(task);

        assertThat(metadata.getProducer()).isEqualTo("vcs-backend-worker");
    }

    @Test
    void buildMetadata_setsEmittedAt() {
        VerifyTaskMessage task = VerifyTaskMessage.builder()
                .metadata(MessageMetadata.builder().build())
                .build();

        MessageMetadata metadata = publisher.buildMetadata(task);

        assertThat(metadata.getEmittedAt()).isNotNull();
    }

    @Test
    void buildMetadata_nullCorrelationId_whenTaskHasNoMetadata() {
        VerifyTaskMessage task = VerifyTaskMessage.builder().build();

        MessageMetadata metadata = publisher.buildMetadata(task);

        assertThat(metadata.getCorrelationId()).isNull();
    }

    // ── publish ───────────────────────────────────────────────────────────────

    @Test
    void publish_sendsVerificationResult_toResultChannel() {
        VerificationResultMessage result = VerificationResultMessage.builder()
                .status(ProcessingStatus.SUCCEEDED)
                .build();

        publisher.publish(result);

        verify(redisTemplate).convertAndSend(eq("vcs.diff.results"), anyString());
    }

    @Test
    void publish_sendsReconstructionResult_toResultChannel() {
        ReconstructionResultMessage result = ReconstructionResultMessage.builder()
                .status(ProcessingStatus.SUCCEEDED)
                .build();

        publisher.publish(result);

        verify(redisTemplate).convertAndSend(eq("vcs.diff.results"), anyString());
    }
}



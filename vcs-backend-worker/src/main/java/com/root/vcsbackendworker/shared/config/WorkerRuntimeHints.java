package com.root.vcsbackendworker.shared.config;

import com.root.vcsbackendworker.shared.db.VersionRow;
import com.root.vcsbackendworker.shared.messaging.FailureReason;
import com.root.vcsbackendworker.shared.messaging.MessageMetadata;
import com.root.vcsbackendworker.shared.messaging.inbound.ReconstructTaskMessage;
import com.root.vcsbackendworker.shared.messaging.inbound.VerifyTaskMessage;
import com.root.vcsbackendworker.shared.messaging.inbound.WorkerTaskMessage;
import com.root.vcsbackendworker.shared.messaging.inbound.WorkerTaskType;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

import java.util.stream.Stream;

/**
 * GraalVM native-image reflection hints for types that Spring AOT cannot detect automatically.
 *
 * <p>Two categories of types need explicit registration:
 * <ol>
 *   <li><b>Jackson message DTOs</b> — polymorphic deserialization via {@code @JsonTypeInfo} /
 *       {@code @JsonSubTypes} requires reflection for constructor invocation, field access, and
 *       getter/setter calls.  Spring AOT resolves the {@code @JsonSubTypes} on
 *       {@link WorkerTaskMessage} but does not automatically follow all nested types.</li>
 *   <li><b>{@link VersionRow}</b> — mapped from JDBC {@code ResultSet} by Spring's
 *       {@code DataClassRowMapper} (used by {@code JdbcClient.query(VersionRow.class)}).
 *       This call site is not visible to Spring AOT's static analysis, so the type must be
 *       registered explicitly.</li>
 * </ol>
 *
 * <p>Registered via {@code @ImportRuntimeHints} on {@link com.root.vcsbackendworker.VcsBackendWorkerApplication}.
 */
public class WorkerRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        // Jackson-serialized inbound message types
        Stream.of(
                WorkerTaskMessage.class,
                VerifyTaskMessage.class,
                ReconstructTaskMessage.class,
                MessageMetadata.class
        ).forEach(type -> hints.reflection().registerType(type, MemberCategory.values()));

        // Enums referenced in message DTOs — needed for Jackson enum deserialization
        Stream.of(
                WorkerTaskType.class,
                FailureReason.class
        ).forEach(type -> hints.reflection().registerType(type, MemberCategory.values()));

        // JDBC DataClassRowMapper target — JdbcClient.query(VersionRow.class) is a dynamic call
        hints.reflection().registerType(VersionRow.class, MemberCategory.values());
    }
}

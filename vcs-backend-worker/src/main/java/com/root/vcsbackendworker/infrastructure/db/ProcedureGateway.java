package com.root.vcsbackendworker.infrastructure.db;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Map;

/**
 * Thin JDBC gateway for calling stored procedures / functions in the vcs_core schema.
 * <p>
 * Procedures themselves are maintained as Flyway migrations in vcs-backend
 * (the schema owner). This worker only invokes them.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ProcedureGateway {

    private final JdbcClient jdbcClient;

    /**
     * Calls a stored procedure that accepts named parameters and returns nothing.
     *
     * @param procedureCall SQL call statement, e.g. {@code "CALL vcs_core.promote_version(:versionId, :s3Key, :checksum)"}
     * @param params        named parameter map
     */
    public void callProcedure(String procedureCall, Map<String, ?> params) {
        var statement = jdbcClient.sql(procedureCall);
        params.forEach(statement::param);
        statement.update();
        log.debug("Executed procedure: {}", procedureCall);
    }

    /**
     * Calls a stored function that returns a single mapped result.
     *
     * @param functionCall SQL select statement wrapping the function, e.g. {@code "SELECT * FROM vcs_core.some_fn(:id)"}
     * @param params       named parameter map
     * @param resultType   expected row type
     */
    public <T> T callFunction(String functionCall, Map<String, ?> params, Class<T> resultType) {
        var statement = jdbcClient.sql(functionCall);
        params.forEach(statement::param);
        return statement.query(resultType).single();
    }
}


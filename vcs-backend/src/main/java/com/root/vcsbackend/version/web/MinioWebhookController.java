package com.root.vcsbackend.version.web;

import com.root.vcsbackend.version.service.VersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Receives MinIO bucket-notification webhooks and dispatches diff verification tasks.
 * <p>
 * MinIO must be configured to POST to {@code /internal/webhook/minio} on
 * {@code s3:ObjectCreated:*} events filtered to prefix {@code tmp/} and suffix
 * {@code .diff}. The shared secret set in MinIO must match {@code app.minio.webhook-token}.
 * <p>
 * Endpoint is {@code permitAll} in SecurityConfig — MinIO cannot present a JWT.
 * Auth is handled by comparing the {@code Authorization} header to the configured token.
 */
@Slf4j
@RestController
@RequestMapping("/internal/webhook/minio")
@RequiredArgsConstructor
public class MinioWebhookController {

    // Matches: tmp/{uuid}/v{int}.diff  (after URL-decoding)
    private static final Pattern STAGING_DIFF_KEY =
            Pattern.compile("^tmp/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})/v(\\d+)\\.diff$");

    // Matches: documents/{uuid}/v{int}  — permanent snapshot upload
    private static final Pattern SNAPSHOT_KEY =
            Pattern.compile("^documents/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})/v(\\d+)$");

    private final VersionService versionService;
    private final JsonMapper jsonMapper;

    @Value("${app.minio.webhook-token:}")
    private String expectedToken;

    @PostMapping
    public ResponseEntity<Void> handleEvent(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody String body) {

        // MinIO may send the token with or without "Bearer " prefix
        String receivedToken = authHeader;
        if (receivedToken != null && receivedToken.startsWith("Bearer ")) {
            receivedToken = receivedToken.substring(7); // Remove "Bearer " prefix
        }
        
        if (expectedToken.isBlank() || !expectedToken.equals(receivedToken)) {
            log.warn("MinIO webhook rejected — invalid or missing Authorization token (received: {})", 
                    authHeader != null ? (authHeader.length() > 20 ? authHeader.substring(0, 20) + "..." : authHeader) : "null");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            JsonNode root = jsonMapper.readTree(body);
            JsonNode records = root.path("Records");
            if (records.isMissingNode() || !records.isArray()) {
                return ResponseEntity.ok().build();
            }

            for (JsonNode record : records) {
                String eventName = record.path("eventName").asString("");
                if (eventName == null || !eventName.startsWith("s3:ObjectCreated")) {
                    continue;
                }

                String rawKey = record.path("s3").path("object").path("key").asString("");
                if (rawKey == null || rawKey.isBlank()) continue;

                String key = URLDecoder.decode(rawKey, StandardCharsets.UTF_8);

                // Staging diff uploaded — dispatch VERIFY_DIFF worker task
                Matcher diffMatcher = STAGING_DIFF_KEY.matcher(key);
                if (diffMatcher.matches()) {
                    UUID docId = UUID.fromString(diffMatcher.group(1));
                    int versionNumber = Integer.parseInt(diffMatcher.group(2));
                    log.debug("Staging diff uploaded: docId={}, versionNumber={}", docId, versionNumber);
                    versionService.handleStagingDiffUploaded(docId, versionNumber);
                    continue;
                }

                // Permanent snapshot uploaded — clear isUploading flag
                Matcher snapshotMatcher = SNAPSHOT_KEY.matcher(key);
                if (snapshotMatcher.matches()) {
                    UUID docId = UUID.fromString(snapshotMatcher.group(1));
                    int versionNumber = Integer.parseInt(snapshotMatcher.group(2));
                    log.debug("Snapshot uploaded: docId={}, versionNumber={}", docId, versionNumber);
                    versionService.handleSnapshotUploaded(docId, versionNumber);
                }
            }
        } catch (Exception e) {
            log.error("Failed to process MinIO webhook body", e);
            // Always return 200 so MinIO doesn't retry on our parse errors
        }

        return ResponseEntity.ok().build();
    }
}


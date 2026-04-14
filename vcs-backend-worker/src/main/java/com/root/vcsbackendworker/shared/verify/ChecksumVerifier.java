package com.root.vcsbackendworker.shared.verify;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Slf4j
@Component
public class ChecksumVerifier {

    private static final String ALGORITHM = "SHA-256";

    /**
     * Computes the SHA-256 hash of {@code bytes} and compares it
     * against {@code expectedChecksum} (hex string, case-insensitive).
     *
     * @param bytes            raw content to hash
     * @param expectedChecksum lowercase or uppercase hex SHA-256 string from the task message
     * @return {@code true} if the computed hash matches the expected value
     */
    public boolean matches(byte[] bytes, String expectedChecksum) {
        String actual = sha256Hex(bytes);
        return actual.equalsIgnoreCase(expectedChecksum);
    }

    /**
     * Returns the SHA-256 hex digest of the given bytes
     * Public only for testing
     */
    public String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hash = digest.digest(bytes);
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the Java spec — this cannot happen at runtime
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}


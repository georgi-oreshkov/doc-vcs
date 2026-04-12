package com.root.vcsbackendworker.verify.domain;

import com.root.vcsbackendworker.shared.verify.ChecksumVerifier;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ChecksumVerifierTest {

    private final ChecksumVerifier verifier = new ChecksumVerifier();

    @Test
    void sha256Hex_producesCorrectHex_forKnownInput() {
        // SHA-256("hello") is a well-known constant
        byte[] input = "hello".getBytes(StandardCharsets.UTF_8);
        assertThat(verifier.sha256Hex(input))
                .isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    }

    @Test
    void sha256Hex_producesCorrectHex_forEmptyBytes() {
        // SHA-256("") is a well-known constant
        assertThat(verifier.sha256Hex(new byte[0]))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void sha256Hex_produces64CharLowercaseHex() {
        String hex = verifier.sha256Hex("any input".getBytes(StandardCharsets.UTF_8));
        assertThat(hex).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    void matches_returnsTrue_whenChecksumMatchesExactly() {
        byte[] bytes = "test content".getBytes(StandardCharsets.UTF_8);
        String hash = verifier.sha256Hex(bytes);
        assertThat(verifier.matches(bytes, hash)).isTrue();
    }

    @Test
    void matches_returnsTrue_whenExpectedChecksumIsUpperCase() {
        byte[] bytes = "test content".getBytes(StandardCharsets.UTF_8);
        String hash = verifier.sha256Hex(bytes).toUpperCase();
        assertThat(verifier.matches(bytes, hash)).isTrue();
    }

    @Test
    void matches_returnsFalse_whenChecksumIsWrong() {
        byte[] bytes = "test content".getBytes(StandardCharsets.UTF_8);
        assertThat(verifier.matches(bytes, "deadbeefdeadbeef")).isFalse();
    }
}


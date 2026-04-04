package com.root.vcsbackendworker.shared.diff;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DiffApplicatorTest {

    private final DiffApplicator applicator = new DiffApplicator();

    @Test
    void apply_producesExpectedResult_fromTestFiles() throws IOException {
        byte[] base = readResource("Test_v1.txt");
        byte[] diff = readResource("Test_diff_v1-2.txt");
        byte[] expected = readResource("Test_v2.txt");

        byte[] result = applicator.apply(base, diff);

        assertThat(new String(result, StandardCharsets.UTF_8))
                .isEqualTo(new String(expected, StandardCharsets.UTF_8));
    }

    private byte[] readResource(String name) throws IOException {
        Path path = Path.of("src/test/resources/" + name);
        return Files.readAllBytes(path);
    }
}


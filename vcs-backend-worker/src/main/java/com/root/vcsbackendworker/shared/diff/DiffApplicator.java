package com.root.vcsbackendworker.shared.diff;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import com.github.difflib.patch.PatchFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class DiffApplicator {

    /**
     * Applies a unified-diff patch to {@code base} and returns the resulting bytes.
     * <p>
     * Both {@code base} and {@code diff} are expected to be UTF-8 encoded text.
     * The diff must be in standard unified diff format (produced by {@code diff -u}).
     *
     * @param base raw bytes of the current (old) version
     * @param diff raw bytes of the unified diff
     * @return raw bytes of the new version after the patch is applied
     * @throws DiffApplicationException if the patch cannot be applied cleanly
     */
    public byte[] apply(byte[] base, byte[] diff) {
        List<String> baseLines = toLines(base);
        List<String> diffLines = toLines(diff);

        log.debug("Applying diff: {} base lines, {} diff lines", baseLines.size(), diffLines.size());

        Patch<String> patch = UnifiedDiffUtils.parseUnifiedDiff(diffLines);

        try {
            List<String> resultLines = DiffUtils.patch(baseLines, patch);
            return fromLines(resultLines);
        } catch (PatchFailedException e) {
            throw new DiffApplicationException("Patch could not be applied cleanly", e);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private List<String> toLines(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        // Strip a single trailing newline before splitting so the list doesn't
        // end with a spurious empty string; fromLines adds it back.
        if (text.endsWith("\n")) {
            text = text.substring(0, text.length() - 1);
        }
        return Arrays.asList(text.split("\n", -1));
    }

    private byte[] fromLines(List<String> lines) {
        String result = String.join("\n", lines) + "\n";
        return result.getBytes(StandardCharsets.UTF_8);
    }
}



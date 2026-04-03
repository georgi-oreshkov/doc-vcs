package com.root.vcsbackendworker.verify.domain;

import org.springframework.stereotype.Component;

@Component
public class ChecksumVerifier {

    public boolean matches(byte[] bytes, String expectedChecksum) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}


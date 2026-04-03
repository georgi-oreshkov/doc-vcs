package com.root.vcsbackendworker.shared.s3;

import org.springframework.stereotype.Component;

@Component
public class S3DocumentStorage {

    public byte[] fetchBytes(String s3Key) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public void uploadBytes(String s3Key, byte[] bytes) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}


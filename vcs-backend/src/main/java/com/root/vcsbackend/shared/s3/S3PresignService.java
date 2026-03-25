package com.root.vcsbackend.shared.s3;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class S3PresignService {

    // TODO: inject S3Presigner / AmazonS3 client

    public String generateUploadUrl(String s3Key) {
        // TODO: implement
        return null;
    }

    public String generateDownloadUrl(String s3Key) {
        // TODO: implement
        return null;
    }
}


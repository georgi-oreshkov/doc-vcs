package com.root.vcsbackend.shared.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * Creates the AWS SDK S3 beans.
 * Works against both real AWS S3 and a local MinIO instance
 * (configured via {@code app.s3.endpoint}).
 */
@Configuration
@EnableConfigurationProperties(S3Properties.class)
public class S3Config {

    @Bean
    S3Client s3Client(S3Properties props) {
        var credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(props.accessKey(), props.secretKey()));

        return S3Client.builder()
                .endpointOverride(URI.create(props.endpoint()))
                .region(Region.of(props.region()))
                .credentialsProvider(credentials)
                .forcePathStyle(true) // required for MinIO
                .build();
    }

    @Bean
    S3Presigner s3Presigner(S3Properties props) {
        String presignEndpoint = props.publicEndpoint() != null && !props.publicEndpoint().isBlank()
                ? props.publicEndpoint()
                : props.endpoint();

        var credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(props.accessKey(), props.secretKey()));

        return S3Presigner.builder()
                .endpointOverride(URI.create(presignEndpoint))
                .region(Region.of(props.region()))
                .credentialsProvider(credentials)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }
}


package com.amalitech.gallery.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class S3Config {

    /**
     * DefaultCredentialsProvider picks up the ECS task role from the container
     * credentials endpoint. There is no access key anywhere in this application.
     */
    @Bean
    S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(System.getenv().getOrDefault("AWS_REGION", "eu-west-1")))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}

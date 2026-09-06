package com.amalitech.gallery.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Everything environment-specific arrives as an environment variable set by the
 * ECS task definition. Nothing is hard-coded and nothing is baked into the image.
 */
@ConfigurationProperties(prefix = "gallery")
public record AppProperties(
        String bucket,
        String cloudfrontDomain,
        int maxUploadMb
) {
    public long maxUploadBytes() {
        return maxUploadMb * 1024L * 1024L;
    }
}

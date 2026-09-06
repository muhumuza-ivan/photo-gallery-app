package com.amalitech.gallery.storage;

import com.amalitech.gallery.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Writes an upload to the private bucket. Reads always go through CloudFront. */
@Service
public class ImageStore {

    private static final Logger log = LoggerFactory.getLogger(ImageStore.class);

    private static final Map<String, String> ALLOWED = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif"
    );

    private final S3Client s3;
    private final AppProperties props;

    public ImageStore(S3Client s3, AppProperties props) {
        this.s3 = s3;
        this.props = props;
    }

    public StoredImage store(MultipartFile file) throws IOException {
        String contentType = file.getContentType() == null
                ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String extension = ALLOWED.get(contentType);
        if (extension == null) {
            throw new UnsupportedImageException(
                    "That file type is not supported. Use JPEG, PNG, WebP or GIF.");
        }
        if (file.getSize() > props.maxUploadBytes()) {
            throw new UnsupportedImageException(
                    "That file is larger than the " + props.maxUploadMb() + " MB limit.");
        }

        String key = "photos/" + UUID.randomUUID() + "." + extension;

        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(props.bucket())
                        .key(key)
                        .contentType(contentType)
                        .cacheControl("public, max-age=31536000, immutable")
                        .build(),
                RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        log.info("Stored upload key={} bytes={} type={}", key, file.getSize(), contentType);
        return new StoredImage(key, contentType, file.getSize());
    }

    public record StoredImage(String key, String contentType, long sizeBytes) { }

    public static class UnsupportedImageException extends RuntimeException {
        public UnsupportedImageException(String message) { super(message); }
    }
}

package com.amalitech.gallery.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "photos")
public class Photo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String description;

    /** Key inside the private S3 bucket, e.g. photos/9f2c....jpg */
    @Column(name = "s3_key", nullable = false, unique = true, length = 255)
    private String s3Key;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Photo() { }

    public Photo(String description, String s3Key, String contentType, long sizeBytes) {
        this.description = description;
        this.s3Key = s3Key;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getDescription() { return description; }
    public String getS3Key() { return s3Key; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public Instant getCreatedAt() { return createdAt; }

    /** Rendered in the frame margin. Access type is FIELD, so this is not mapped. */
    public String getDisplayDate() {
        return DateTimeFormatter.ofPattern("dd MMM").withZone(ZoneOffset.UTC).format(createdAt);
    }
}

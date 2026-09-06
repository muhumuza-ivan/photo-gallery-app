CREATE TABLE photos (
    id           BIGSERIAL PRIMARY KEY,
    description  VARCHAR(500) NOT NULL,
    s3_key       VARCHAR(255) NOT NULL UNIQUE,
    content_type VARCHAR(100) NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_photos_created_at ON photos (created_at DESC);

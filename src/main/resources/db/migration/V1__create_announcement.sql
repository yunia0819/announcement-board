CREATE TABLE announcement (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    title VARCHAR(200) NOT NULL,
    publisher VARCHAR(100) NOT NULL,
    publish_date DATE NOT NULL,
    deadline_date DATE NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT ck_announcement_dates CHECK (deadline_date >= publish_date)
);
CREATE INDEX idx_announcement_published ON announcement (publish_date DESC, id DESC);

CREATE TABLE attachment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    announcement_id BIGINT NOT NULL UNIQUE,
    original_filename VARCHAR(255) NOT NULL,
    storage_name VARCHAR(80) NOT NULL UNIQUE,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    CONSTRAINT fk_attachment_announcement FOREIGN KEY (announcement_id) REFERENCES announcement(id) ON DELETE CASCADE
);

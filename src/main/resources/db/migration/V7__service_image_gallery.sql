-- Existing services retain their cover in services.image_url.
CREATE TABLE IF NOT EXISTS service_images (
    service_id BIGINT NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    PRIMARY KEY (service_id, position)
);

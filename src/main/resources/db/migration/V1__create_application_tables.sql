CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(16) NOT NULL
);

CREATE TABLE tracks (
    id BIGSERIAL PRIMARY KEY,
    display_name VARCHAR(255),
    original_filename VARCHAR(255),
    file_path VARCHAR(255),
    genres_json TEXT,
    created_at TIMESTAMP,
    user_id BIGINT REFERENCES users(id)
);

CREATE INDEX idx_tracks_user_created_at ON tracks (user_id, created_at DESC);

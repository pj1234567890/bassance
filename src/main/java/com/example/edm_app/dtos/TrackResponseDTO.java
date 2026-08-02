package com.example.edm_app.dtos;

import java.time.LocalDateTime;

public class TrackResponseDTO {

    private Long id;
    private String displayName;
    private String originalFilename;
    private String genresJson;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getGenresJson() {
        return genresJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public void setGenresJson(String genresJson) {
        this.genresJson = genresJson;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

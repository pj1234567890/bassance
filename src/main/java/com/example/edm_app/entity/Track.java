package com.example.edm_app.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tracks")
public class Track {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String displayName;        // editable
    private String originalFilename;
    private String filePath;

    @Column(columnDefinition = "TEXT")
    private String genresJson;         // immutable ML output

    private LocalDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    public Long getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getGenresJson() {
        return genresJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public User getUser() {
        return user;
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

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public void setGenresJson(String genresJson) {
        this.genresJson = genresJson;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setUser(User user) {
        this.user = user;
    }
}

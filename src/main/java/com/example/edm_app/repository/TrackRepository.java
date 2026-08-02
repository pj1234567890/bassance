package com.example.edm_app.repository;

import com.example.edm_app.entity.Track;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrackRepository extends JpaRepository<Track, Long> {
    List<Track> findByUserEmailOrderByCreatedAtDesc(String email);
}

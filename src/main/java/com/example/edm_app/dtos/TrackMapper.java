package com.example.edm_app.dtos;

import com.example.edm_app.entity.Track;

public class TrackMapper {

    public static TrackResponseDTO toDTO(Track t) {
        TrackResponseDTO dto = new TrackResponseDTO();
        dto.setId(t.getId());
        dto.setDisplayName(t.getDisplayName());
        dto.setOriginalFilename(t.getOriginalFilename());
        dto.setGenresJson(t.getGenresJson());
        dto.setCreatedAt(t.getCreatedAt());
        return dto;
    }
}

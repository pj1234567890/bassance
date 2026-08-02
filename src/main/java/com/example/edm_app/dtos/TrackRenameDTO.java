package com.example.edm_app.dtos;

import jakarta.validation.constraints.NotBlank;

public class TrackRenameDTO {
    @NotBlank
    private String displayName;

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}

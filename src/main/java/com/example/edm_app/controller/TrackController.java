package com.example.edm_app.controller;

import com.example.edm_app.dtos.TrackRenameDTO;
import com.example.edm_app.dtos.TrackResponseDTO;
import com.example.edm_app.service.TrackService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/tracks")
public class TrackController {

    @Autowired
    private TrackService trackService;

    @PostMapping("/upload")
    public TrackResponseDTO upload(@RequestParam("file") MultipartFile file) {
        String email = (String) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();
        return trackService.upload(email, file);
    }


    @GetMapping
    public List<TrackResponseDTO> myTracks() {
        String email = (String) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();

        return trackService.getMyTracks(email);
    }

    @PutMapping("/{id}/rename")
    public TrackResponseDTO rename(@PathVariable Long id,
                                   @Valid @RequestBody TrackRenameDTO dto) {
        String email = (String) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();

        return trackService.rename(email, id, dto);
    }
}

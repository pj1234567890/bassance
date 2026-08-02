package com.example.edm_app.service;

import com.example.edm_app.dtos.TrackMapper;
import com.example.edm_app.dtos.TrackRenameDTO;
import com.example.edm_app.dtos.TrackResponseDTO;
import com.example.edm_app.entity.Track;
import com.example.edm_app.entity.User;
import com.example.edm_app.repository.TrackRepository;
import com.example.edm_app.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class TrackService {

    @Autowired
    private TrackRepository trackRepository;

    @Autowired
    private UserRepository userRepository;

    public TrackResponseDTO upload(String email, MultipartFile file) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("user not found"));

        try {
            String dir = "uploads";
            new File(dir).mkdirs();

            String storedName = UUID.randomUUID() + "_" + file.getOriginalFilename();
            File dest = new File(dir, storedName);
            file.transferTo(dest);

            // ---- Call Python ML Service ----
            RestTemplate rest = new RestTemplate();

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new FileSystemResource(dest));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> req =
                    new HttpEntity<>(body, headers);

            ResponseEntity<String> resp = rest.postForEntity(
                    "http://127.0.0.1:8001/predict",
                    req,
                    String.class
            );

            String genresJson = resp.getBody();
            // --------------------------------

            Track t = new Track();
            t.setUser(user);
            t.setOriginalFilename(file.getOriginalFilename());
            t.setDisplayName(file.getOriginalFilename());
            t.setFilePath(dest.getAbsolutePath());
            t.setGenresJson(genresJson); // immutable ML output
            t.setCreatedAt(LocalDateTime.now());

            Track saved = trackRepository.save(t);
            return TrackMapper.toDTO(saved);

        } catch (Exception e) {
            throw new RuntimeException("upload failed", e);
        }
    }


    public List<TrackResponseDTO> getMyTracks(String email) {
        return trackRepository
                .findByUserEmailOrderByCreatedAtDesc(email)
                .stream()
                .map(TrackMapper::toDTO)
                .toList();
    }

    public TrackResponseDTO rename(String email, Long id, TrackRenameDTO dto) {
        Track t = trackRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("not found"));

        if (!t.getUser().getEmail().equals(email)) {
            throw new RuntimeException("forbidden");
        }

        t.setDisplayName(dto.getDisplayName());
        Track saved = trackRepository.save(t);
        return TrackMapper.toDTO(saved);
    }
}

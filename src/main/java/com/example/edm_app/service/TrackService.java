package com.example.edm_app.service;

import com.example.edm_app.dtos.TrackMapper;
import com.example.edm_app.dtos.TrackRenameDTO;
import com.example.edm_app.dtos.TrackResponseDTO;
import com.example.edm_app.entity.Track;
import com.example.edm_app.entity.User;
import com.example.edm_app.repository.TrackRepository;
import com.example.edm_app.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class TrackService {

    private static final Logger logger = LoggerFactory.getLogger(TrackService.class);

    @Autowired
    private TrackRepository trackRepository;

    @Autowired
    private UserRepository userRepository;

    @Value("${app.ml.url:http://localhost:8001}")
    private String mlServiceUrl;

    @Value("${app.ml.token}")
    private String mlServiceToken;

    @Value("${app.upload.max-bytes:52428800}")
    private long maxUploadBytes;

    public TrackResponseDTO upload(String email, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is required");
        }
        if (file.getSize() > maxUploadBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Audio file exceeds the upload limit");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && originalFilename.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filename is too long");
        }
        if (!hasSupportedAudioSignature(file)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only valid MP3, WAV, FLAC, OGG, and M4A audio files are supported");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("edm-upload-", getSafeFileSuffix(file.getOriginalFilename()));
            file.transferTo(tempFile.toFile());

            // ---- Call Python ML Service ----
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(5_000);
            requestFactory.setReadTimeout(120_000);
            RestTemplate rest = new RestTemplate(requestFactory);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new FileSystemResource(tempFile));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("X-ML-Service-Token", mlServiceToken);

            HttpEntity<MultiValueMap<String, Object>> req =
                    new HttpEntity<>(body, headers);

            ResponseEntity<String> resp = rest.postForEntity(
                    mlServiceUrl + "/predict",
                    req,
                    String.class
            );

            String genresJson = resp.getBody();
            if (genresJson == null || genresJson.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ML service returned empty response");
            }
            // --------------------------------

            Track t = new Track();
            t.setUser(user);
            String displayName = originalFilename == null || originalFilename.isBlank() ? "Audio track" : originalFilename;
            t.setOriginalFilename(displayName);
            t.setDisplayName(displayName);
            t.setGenresJson(genresJson); // immutable ML output
            t.setCreatedAt(LocalDateTime.now());

            Track saved = trackRepository.save(t);
            return TrackMapper.toDTO(saved);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Upload failed", e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException cleanupError) {
                    logger.warn("Could not delete temporary audio upload {}", tempFile, cleanupError);
                }
            }
        }
    }

    private boolean hasSupportedAudioSignature(MultipartFile file) {
        try (var input = file.getInputStream()) {
            byte[] header = input.readNBytes(16);
            String filename = file.getOriginalFilename();
            String suffix = getSafeFileSuffix(filename).toLowerCase(Locale.ROOT);
            return switch (suffix) {
                case ".mp3" -> startsWith(header, "ID3") || (header.length > 1
                        && (header[0] & 0xFF) == 0xFF && (header[1] & 0xE0) == 0xE0);
                case ".wav" -> startsWith(header, "RIFF") && header.length >= 12
                        && new String(header, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WAVE");
                case ".flac" -> startsWith(header, "fLaC");
                case ".ogg" -> startsWith(header, "OggS");
                case ".m4a" -> header.length >= 8
                        && new String(header, 4, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("ftyp");
                default -> false;
            };
        } catch (IOException e) {
            return false;
        }
    }

    private boolean startsWith(byte[] data, String prefix) {
        if (data.length < prefix.length()) {
            return false;
        }
        for (int i = 0; i < prefix.length(); i++) {
            if (data[i] != (byte) prefix.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private String getSafeFileSuffix(String originalFilename) {
        if (originalFilename == null) {
            return ".audio";
        }

        int extensionStart = originalFilename.lastIndexOf('.');
        if (extensionStart < 0) {
            return ".audio";
        }

        String suffix = originalFilename.substring(extensionStart);
        return suffix.matches("\\.[A-Za-z0-9]{1,10}") ? suffix : ".audio";
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Track not found"));

        if (!t.getUser().getEmail().equals(email)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }

        t.setDisplayName(dto.getDisplayName());
        Track saved = trackRepository.save(t);
        return TrackMapper.toDTO(saved);
    }
}

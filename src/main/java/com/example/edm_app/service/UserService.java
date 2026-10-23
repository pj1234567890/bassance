package com.example.edm_app.service;
import com.example.edm_app.dtos.LoginDTO;
import com.example.edm_app.dtos.LoginResponseDTO;
import com.example.edm_app.dtos.RegistrationDTO;
import com.example.edm_app.entity.User;
import com.example.edm_app.jwt.JwtUtil;
import com.example.edm_app.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public void register(RegistrationDTO dto) {
        validateBcryptLength(dto.getPassword());
        String email = normalizeEmail(dto.getEmail());
        userRepository.findByEmail(email)
                .ifPresent(u -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "User already exists");
                });

        User user = new User();
        user.setEmail(email);
        user.setName(dto.getName());
        user.setPassword(encoder.encode(dto.getPassword()));

        userRepository.save(user);
    }

    public LoginResponseDTO login(LoginDTO dto) {
        validateBcryptLength(dto.getPassword());
        User user = userRepository.findByEmail(normalizeEmail(dto.getEmail()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!encoder.matches(dto.getPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole());

        LoginResponseDTO res = new LoginResponseDTO();
        res.setEmail(user.getEmail());
        res.setToken(token);
        return res;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void validateBcryptLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at most 72 UTF-8 bytes");
        }
    }
}


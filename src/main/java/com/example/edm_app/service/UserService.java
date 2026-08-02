package com.example.edm_app.service;
import com.example.edm_app.dtos.LoginDTO;
import com.example.edm_app.dtos.LoginResponseDTO;
import com.example.edm_app.dtos.RegistrationDTO;
import com.example.edm_app.entity.User;
import com.example.edm_app.jwt.JwtUtil;
import com.example.edm_app.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    private BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public void register(RegistrationDTO dto) {
        userRepository.findByEmail(dto.getEmail())
                .ifPresent(u -> { throw new RuntimeException("User already exists"); });

        User user = new User();
        user.setEmail(dto.getEmail());
        user.setName(dto.getName());
        user.setPassword(encoder.encode(dto.getPassword()));

        userRepository.save(user);
    }

    public LoginResponseDTO login(LoginDTO dto) {
        User user = userRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        if (!encoder.matches(dto.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        String token = JwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole());

        LoginResponseDTO res = new LoginResponseDTO();
        res.setEmail(user.getEmail());
        res.setToken(token);
        return res;
    }
}


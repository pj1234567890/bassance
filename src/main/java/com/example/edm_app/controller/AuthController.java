package com.example.edm_app.controller;

import com.example.edm_app.dtos.LoginDTO;
import com.example.edm_app.dtos.LoginResponseDTO;
import com.example.edm_app.dtos.RegistrationDTO;
import com.example.edm_app.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String AUTH_COOKIE = "EDM_AUTH";

    @Value("${app.cookie.secure:true}")
    private boolean secureCookie;

    @Value("${jwt.expiry-ms:3600000}")
    private long tokenExpiryMs;

    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public void register(@Valid @RequestBody RegistrationDTO dto) {
        userService.register(dto);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginDTO dto) {
        LoginResponseDTO result = userService.login(dto);
        ResponseCookie cookie = ResponseCookie.from(AUTH_COOKIE, result.getToken())
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ofMillis(tokenExpiryMs))
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .cacheControl(CacheControl.noStore())
                .body(result);
    }

    @GetMapping("/csrf")
    public ResponseEntity<CsrfToken> csrf(CsrfToken csrfToken) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(csrfToken);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        ResponseCookie cookie = ResponseCookie.from(AUTH_COOKIE, "")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .cacheControl(CacheControl.noStore())
                .build();
    }
}

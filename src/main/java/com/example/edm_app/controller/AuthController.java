package com.example.edm_app.controller;

import com.example.edm_app.dtos.LoginDTO;
import com.example.edm_app.dtos.LoginResponseDTO;
import com.example.edm_app.dtos.RegistrationDTO;
import com.example.edm_app.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public void register(@Valid @RequestBody RegistrationDTO dto) {
        userService.register(dto);
    }

    @PostMapping("/login")
    public LoginResponseDTO login(@Valid @RequestBody LoginDTO dto) {
        return userService.login(dto);
    }
}

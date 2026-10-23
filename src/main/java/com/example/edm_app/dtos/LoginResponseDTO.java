package com.example.edm_app.dtos;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class LoginResponseDTO {
    private String email;
    private String token;

    public String getEmail() { return email; }
    @JsonIgnore
    public String getToken() { return token; }

    public void setEmail(String email) { this.email = email; }
    public void setToken(String token) { this.token = token; }
}

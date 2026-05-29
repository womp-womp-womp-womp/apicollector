package com.example.jsonparser.dto;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record AuthRequest(String username, String password, String apiKey) {
    }

    public record AuthResponse(String username, String role, String token) {
    }

    public record UserDto(String username, String role) {
    }

    public record UpdateRequest(String apiKey) {
    }
}

package com.streaks.api.auth;

public record AuthResponse(String token, String type) {
    public AuthResponse(String token) {
        this(token, "Bearer");
    }
}

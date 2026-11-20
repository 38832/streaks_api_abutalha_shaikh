package com.streaks.api.common;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HomeController {

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> home() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "name", "Streaks API",
                "version", "1.0.0",
                "message", "Streaks API is running and healthy. Send requests to the endpoints listed below.",
                "endpoints", Map.of(
                        "signup", "POST /auth/signup",
                        "login", "POST /auth/login",
                        "habits", "GET, POST /habits",
                        "checkIn", "POST /habits/{id}/check-ins",
                        "wallet", "GET /wallet",
                        "redeem", "POST /wallet/redeem"
                ),
                "documentation", "https://github.com/38832/streaks_api_abutalha_shaikh#readme"
        ));
    }
}

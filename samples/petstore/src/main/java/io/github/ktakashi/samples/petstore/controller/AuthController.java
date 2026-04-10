package io.github.ktakashi.samples.petstore.controller;

import io.github.ktakashi.samples.petstore.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * REST controller for authentication operations.
 * <p> <p />
 * This controller is mounted at /auth/api/v1 to demonstrate multi-host API testing.
 * In a real microservices environment, the auth service would run on a separate
 * host/port, but for this sample we use a different path prefix to simulate that.
 * <p> <p />
 * The petstore API uses /api/v1 while auth API uses /auth/api/v1, allowing us to
 * test with different base URLs:
 * - default (petstore): http://localhost:PORT/api/v1
 * - auth: http://localhost:PORT/auth/api/v1
 * <p> <p />
 * Note: This is a simplified mock implementation for demonstration purposes.
 * In a real application, you would use Spring Security with proper authentication.
 */
@RestController
@RequestMapping("/auth/api/v1")
public class AuthController {

    /**
     * Login endpoint.
     * POST /auth/api/v1/login
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        String username = credentials.get("username");
        String password = credentials.get("password");

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "Invalid request",
                    java.util.List.of("username and password are required")));
        }

        // Mock authentication - accept admin/admin or test/test
        if (("admin".equals(username) && "admin".equals(password)) ||
            ("test".equals(username) && "test".equals(password))) {
            
            String token = UUID.randomUUID().toString();
            return ResponseEntity.ok(Map.of(
                "token", token,
                "expiresIn", 3600
            ));
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse.of(401, "Invalid credentials"));
    }

    /**
     * Logout endpoint.
     * POST /auth/api/v1/logout
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        // Mock logout - in a real app this would invalidate the token/session
        return ResponseEntity.ok(Map.of(
            "message", "Logout successful"
        ));
    }
}

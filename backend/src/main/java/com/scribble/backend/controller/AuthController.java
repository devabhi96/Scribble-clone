package com.scribble.backend.controller;

import com.scribble.backend.dto.LoginRequest;
import com.scribble.backend.dto.RegisterRequest;
import com.scribble.backend.dto.TokenResponse;
import com.scribble.backend.model.User;
import com.scribble.backend.repository.UserRepository;
import com.scribble.backend.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(JwtService jwtService, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/guest")
    public TokenResponse guestToken() {
        String guestId = "guest-" + UUID.randomUUID();
        String token = jwtService.generateGuestToken(guestId);
        return new TokenResponse(token, guestId, "GUEST");
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        if (userRepository.existsByUsername(req.username())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Username already taken"));
        }
        User user = new User();
        user.setUsername(req.username());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user = userRepository.save(user);

        String token = jwtService.generateUserToken(user.getId().toString());
        return ResponseEntity.ok(new TokenResponse(token, user.getId().toString(), "USER"));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        return userRepository.findByUsername(req.username())
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .<ResponseEntity<?>>map(u -> {
                    String token = jwtService.generateUserToken(u.getId().toString());
                    return ResponseEntity.ok(new TokenResponse(token, u.getId().toString(), "USER"));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid username or password")));
    }
}
package com.example.controller;

import com.example.db.entities.UserEntity;
import com.example.db.repository.UserRepository;
import com.example.exception.NotFoundException;
import com.example.jsonparser.dto.AuthDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private static final String ROLE_USER = "USER";
    private static final String ROLE_ADMIN = "ADMIN";

    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, AuthDtos.UserDto> sessions = new ConcurrentHashMap<>();

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.AuthRequest request) {
        String username = normalizeUsername(request.username());
        String password = validatePassword(request.password());

        if (userRepository.existsById(username)) {
            throw new IllegalArgumentException("User already exists");
        }

        String salt = randomSalt();
        String role = userRepository.count() == 0 ? ROLE_ADMIN : ROLE_USER;
        UserEntity user = new UserEntity(username, hashPassword(password, salt), salt, role);
        userRepository.save(user);

        return createSession(user);
    }

    @Transactional(readOnly = true)
    public AuthDtos.AuthResponse login(AuthDtos.AuthRequest request) {
        String username = normalizeUsername(request.username());
        String password = validatePassword(request.password());
        UserEntity user = userRepository.findById(username)
                .orElseThrow(() -> new NotFoundException("User was not found"));

        if (!hashPassword(password, user.getSalt()).equals(user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        return createSession(user);
    }

    @Transactional(readOnly = true)
    public List<AuthDtos.UserDto> listUsers() {
        return userRepository.findAll()
                .stream()
                .map(user -> new AuthDtos.UserDto(user.getUsername(), user.getRole()))
                .toList();
    }

    public AuthDtos.UserDto requireUser(String token) {
        AuthDtos.UserDto user = sessions.get(normalizeToken(token));

        if (user == null) {
            throw new IllegalArgumentException("Authentication token is missing or invalid");
        }

        return user;
    }

    public void requireAdmin(String token) {
        AuthDtos.UserDto user = requireUser(token);

        if (!ROLE_ADMIN.equals(user.role())) {
            throw new IllegalArgumentException("Admin role is required");
        }
    }

    private AuthDtos.AuthResponse createSession(UserEntity user) {
        String token = UUID.randomUUID().toString();
        AuthDtos.UserDto dto = new AuthDtos.UserDto(user.getUsername(), user.getRole());
        sessions.put(token, dto);
        return new AuthDtos.AuthResponse(user.getUsername(), user.getRole(), token);
    }

    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username must not be empty");
        }

        String normalized = username.trim().toLowerCase(Locale.ROOT);

        if (normalized.length() < 3 || normalized.length() > 64) {
            throw new IllegalArgumentException("Username length must be between 3 and 64");
        }

        if (!normalized.matches("[a-z0-9_.-]+")) {
            throw new IllegalArgumentException("Username can contain latin letters, digits, dot, dash and underscore");
        }

        return normalized;
    }

    private String validatePassword(String password) {
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("Password length must be at least 6");
        }

        return password;
    }

    private String normalizeToken(String token) {
        return token == null ? "" : token.trim();
    }

    private String randomSalt() {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private String hashPassword(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((salt + ":" + password).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}

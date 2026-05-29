package com.example.controller;

import com.example.db.entities.UserEntity;
import com.example.db.repository.UserRepository;
import com.example.exception.NotFoundException;
import com.example.apiparser.InspectionApiClient;
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
    private final InspectionApiClient inspectionApiClient;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public AuthService(UserRepository userRepository, InspectionApiClient inspectionApiClient) {
        this.userRepository = userRepository;
        this.inspectionApiClient = inspectionApiClient;
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.AuthRequest request) {
        String username = normalizeUsername(request.username());

        if (userRepository.existsById(username)) {
            throw new IllegalArgumentException("User already exists");
        }

        boolean firstUser = userRepository.count() == 0;
        String role = firstUser ? ROLE_ADMIN : ROLE_USER;
        String credential = firstUser
                ? validatePassword(request.password())
                : validateApiKey(request.apiKey());
        verifyUserApiKeyIfNeeded(role, credential);
        String salt = randomSalt();
        UserEntity user = new UserEntity(username, hashCredential(credential, salt), salt, role);
        userRepository.save(user);

        return createSession(user, ROLE_USER.equals(role) ? credential : null);
    }

    @Transactional(readOnly = true)
    public AuthDtos.AuthResponse login(AuthDtos.AuthRequest request) {
        String username = normalizeUsername(request.username());
        UserEntity user = userRepository.findById(username)
                .orElseThrow(() -> new NotFoundException("User was not found"));
        String credential = ROLE_ADMIN.equals(user.getRole())
                ? validatePassword(request.password())
                : validateApiKey(request.apiKey());
        verifyUserApiKeyIfNeeded(user.getRole(), credential);

        if (!hashCredential(credential, user.getSalt()).equals(user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        return createSession(user, ROLE_USER.equals(user.getRole()) ? credential : null);
    }

    @Transactional(readOnly = true)
    public List<AuthDtos.UserDto> listUsers() {
        return userRepository.findAll()
                .stream()
                .map(user -> new AuthDtos.UserDto(user.getUsername(), user.getRole()))
                .toList();
    }

    public AuthDtos.UserDto requireUser(String token) {
        Session session = sessions.get(normalizeToken(token));

        if (session == null) {
            throw new IllegalArgumentException("Authentication token is missing or invalid");
        }

        return session.user();
    }

    public void requireAdmin(String token) {
        AuthDtos.UserDto user = requireUser(token);

        if (!ROLE_ADMIN.equals(user.role())) {
            throw new IllegalArgumentException("Admin role is required");
        }
    }

    public String requireUserApiKey(String token) {
        Session session = sessions.get(normalizeToken(token));

        if (session == null) {
            throw new IllegalArgumentException("Authentication token is missing or invalid");
        }

        if (!ROLE_USER.equals(session.user().role())) {
            throw new IllegalArgumentException("User API key is required for this operation");
        }

        if (session.apiKey() == null || session.apiKey().isBlank()) {
            throw new IllegalArgumentException("User API key is missing. Log in with API key again");
        }

        return session.apiKey();
    }

    private AuthDtos.AuthResponse createSession(UserEntity user, String apiKey) {
        String token = UUID.randomUUID().toString();
        AuthDtos.UserDto dto = new AuthDtos.UserDto(user.getUsername(), user.getRole());
        sessions.put(token, new Session(dto, apiKey));
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

    private String validateApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank() || "token".equals(apiKey.trim())) {
            throw new IllegalArgumentException("API key must not be empty");
        }

        return apiKey.trim();
    }

    private void verifyUserApiKeyIfNeeded(String role, String apiKey) {
        if (ROLE_USER.equals(role)) {
            inspectionApiClient.checkApiAvailability(apiKey);
        }
    }

    private String normalizeToken(String token) {
        return token == null ? "" : token.trim();
    }

    private String randomSalt() {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private String hashCredential(String credential, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((salt + ":" + credential).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private record Session(AuthDtos.UserDto user, String apiKey) {
    }
}

package com.example.db.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "app_users")
public class UserEntity {

    @Id
    @Column(name = "username", nullable = false, columnDefinition = "text")
    private String username;

    @Column(name = "password_hash", nullable = false, columnDefinition = "text")
    private String passwordHash;

    @Column(name = "salt", nullable = false, columnDefinition = "text")
    private String salt;

    @Column(name = "role", nullable = false, columnDefinition = "text")
    private String role;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected UserEntity() {
    }

    public UserEntity(String username, String passwordHash, String salt, String role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.role = role;
        this.createdAt = LocalDateTime.now();
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getSalt() {
        return salt;
    }

    public String getRole() {
        return role;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

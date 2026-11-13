package com.platform.userservice.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false, length = 30)
    private String role;

    @Column(name = "password", nullable = false, length = 255, columnDefinition = "VARCHAR(255) DEFAULT '$2a$10$wKlh43gP0a4ZkY/gN82tjeH3jU0dZqN81.t4zJ1b7O7w5g0uLpWv6'")
    private String password;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public User() {
    }

    public User(String name, String email, String role) {
        this.name = name;
        this.email = email;
        this.role = (role == null || role.isBlank()) ? "CUSTOMER" : role;
    }

    public User(String name, String email, String role, String password) {
        this.name = name;
        this.email = email;
        this.role = (role == null || role.isBlank()) ? "CUSTOMER" : role;
        this.password = password;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        if (this.role == null || this.role.isBlank()) {
            this.role = "CUSTOMER";
        }
        if (this.password == null || this.password.isBlank()) {
            // Default BCrypt hash for "Password@123" if not explicitly provided
            this.password = "$2a$10$wKlh43gP0a4ZkY/gN82tjeH3jU0dZqN81.t4zJ1b7O7w5g0uLpWv6";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

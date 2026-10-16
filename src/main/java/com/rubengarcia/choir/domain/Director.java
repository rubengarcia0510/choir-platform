package com.rubengarcia.choir.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("directors")
public class Director {

    @Id
    private Long id;

    @Column("display_name")
    private String displayName;

    private String email;

    @Column("password_hash")
    private String passwordHash;

    @Column("email_verified")
    private boolean emailVerified;

    @Column("created_at")
    private LocalDateTime createdAt;

    protected Director() {
    }

    public static Director create(String displayName, String email) {
        Director director = new Director();
        director.setDisplayName(displayName);
        director.setEmail(email);
        return director;
    }

    public Long getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

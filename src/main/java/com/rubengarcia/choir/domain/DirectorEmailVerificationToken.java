package com.rubengarcia.choir.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("director_email_verification_tokens")
public class DirectorEmailVerificationToken {

    @Id
    private Long id;

    @Column("director_id")
    private Long directorId;

    @Column("token_hash")
    private String tokenHash;

    @Column("expires_at")
    private LocalDateTime expiresAt;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("consumed_at")
    private LocalDateTime consumedAt;

    protected DirectorEmailVerificationToken() {
    }

    public DirectorEmailVerificationToken(
            Long directorId,
            String tokenHash,
            LocalDateTime expiresAt) {
        this.directorId = directorId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public Long getDirectorId() {
        return directorId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getConsumedAt() {
        return consumedAt;
    }

    public void setConsumedAt(LocalDateTime consumedAt) {
        this.consumedAt = consumedAt;
    }
}

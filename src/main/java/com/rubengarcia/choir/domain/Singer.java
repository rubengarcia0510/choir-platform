package com.rubengarcia.choir.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("singers")
public class Singer {

    @Id
    private Long id;

    @Column("choir_id")
    private Long choirId;

    @Column("display_name")
    private String displayName;

    private String email;

    @Column("voice_part")
    private String voicePart;

    private boolean active = true;

    @Column("created_at")
    private LocalDateTime createdAt;

    protected Singer() {
    }

    public Long getId() {
        return id;
    }

    public Long getChoirId() {
        return choirId;
    }

    public void setChoirId(Long choirId) {
        this.choirId = choirId;
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

    public String getVoicePart() {
        return voicePart;
    }

    public void setVoicePart(String voicePart) {
        this.voicePart = voicePart;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

package com.rubengarcia.choir.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "singers")
public class Singer {

@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;

@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "choir_id", nullable = false)
private Choir choir;

@Column(name = "display_name", nullable = false, length = 160)
private String displayName;

@Column(length = 254)
private String email;

@Column(name = "voice_part", length = 40)
private String voicePart;

@Column(nullable = false)
private boolean active = true;

@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
private LocalDateTime createdAt;

protected Singer() {
}

public Long getId() {
    return id;
}

public Choir getChoir() {
    return choir;
}

public void setChoir(Choir choir) {
    this.choir = choir;
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

}

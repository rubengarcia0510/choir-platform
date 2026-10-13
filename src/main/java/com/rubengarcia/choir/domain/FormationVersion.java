package com.rubengarcia.choir.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
name = "formation_versions",
uniqueConstraints = @UniqueConstraint(
name = "uq_formation_version",
columnNames = {"formation_id", "version_number"}
)
)
public class FormationVersion {
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;

@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "formation_id", nullable = false)
private Formation formation;

@Column(name = "version_number", nullable = false)
private int versionNumber;

@Column(nullable = false, length = 20)
private String status = "DRAFT";

@Column(length = 2000)
private String notes;

@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
private LocalDateTime createdAt;

@Column(name = "published_at")
private LocalDateTime publishedAt;

protected FormationVersion() {}

public Long getId() { return id; }
public Formation getFormation() { return formation; }
public void setFormation(Formation formation) { this.formation = formation; }
public int getVersionNumber() { return versionNumber; }
public void setVersionNumber(int versionNumber) { this.versionNumber = versionNumber; }
public String getStatus() { return status; }
public void setStatus(String status) { this.status = status; }
public String getNotes() { return notes; }
public void setNotes(String notes) { this.notes = notes; }
public LocalDateTime getCreatedAt() { return createdAt; }
public LocalDateTime getPublishedAt() { return publishedAt; }
public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }

}

package com.rubengarcia.choir.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
name = "transition_versions",
uniqueConstraints = @UniqueConstraint(
name = "uq_transition_version",
columnNames = {"transition_id", "version_number"}
)
)
public class TransitionVersion {
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;

@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "transition_id", nullable = false)
private Transition transition;

@Column(name = "version_number", nullable = false)
private int versionNumber;

@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "from_formation_version_id", nullable = false)
private FormationVersion fromFormationVersion;

@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "to_formation_version_id", nullable = false)
private FormationVersion toFormationVersion;

@Column(nullable = false, length = 20)
private String status = "DRAFT";

@Column(length = 4000)
private String instructions;

@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
private LocalDateTime createdAt;

@Column(name = "published_at")
private LocalDateTime publishedAt;

protected TransitionVersion() {}

public Long getId() { return id; }
public Transition getTransition() { return transition; }
public void setTransition(Transition value) { this.transition = value; }
public int getVersionNumber() { return versionNumber; }
public void setVersionNumber(int value) { this.versionNumber = value; }
public FormationVersion getFromFormationVersion() { return fromFormationVersion; }
public void setFromFormationVersion(FormationVersion value) { this.fromFormationVersion = value; }
public FormationVersion getToFormationVersion() { return toFormationVersion; }
public void setToFormationVersion(FormationVersion value) { this.toFormationVersion = value; }
public String getStatus() { return status; }
public void setStatus(String value) { this.status = value; }
public String getInstructions() { return instructions; }
public void setInstructions(String value) { this.instructions = value; }
public LocalDateTime getCreatedAt() { return createdAt; }
public LocalDateTime getPublishedAt() { return publishedAt; }
public void setPublishedAt(LocalDateTime value) { this.publishedAt = value; }

}

package com.rubengarcia.choir.domain;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("transition_versions")
public class TransitionVersion {

    @Id
    private Long id;

    @Column("transition_id")
    private Long transitionId;

    @Column("choir_id")
    private Long choirId;

    @Column("version_number")
    private int versionNumber;

    @Column("from_formation_version_id")
    private Long fromFormationVersionId;

    @Column("to_formation_version_id")
    private Long toFormationVersionId;

    private String status = "DRAFT";

    private String instructions;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("published_at")
    private LocalDateTime publishedAt;

    public TransitionVersion() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getChoirId() {
        return choirId;
    }

    public void setChoirId(Long choirId) {
        this.choirId = choirId;
    }

    public Long getTransitionId() {
        return transitionId;
    }

    public void setTransitionId(Long transitionId) {
        this.transitionId = transitionId;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(int versionNumber) {
        this.versionNumber = versionNumber;
    }

    public Long getFromFormationVersionId() {
        return fromFormationVersionId;
    }

    public void setFromFormationVersionId(Long fromFormationVersionId) {
        this.fromFormationVersionId = fromFormationVersionId;
    }

    public Long getToFormationVersionId() {
        return toFormationVersionId;
    }

    public void setToFormationVersionId(Long toFormationVersionId) {
        this.toFormationVersionId = toFormationVersionId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }
}

package com.rubengarcia.choir.formation.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;


public class FormationPosition {

    private Long id;

    private Long formationVersionId;

    private Long singerId;

    private String singerNameSnapshot;

    private String voicePartSnapshot;

    private Long voiceSectionIdSnapshot;

    private String voiceSectionNameSnapshot;

    private Integer divisionNumberSnapshot;

    private String positionLabel;

    private BigDecimal xCoordinate;

    private BigDecimal yCoordinate;

    private LocalDateTime createdAt;

    public FormationPosition() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getFormationVersionId() {
        return formationVersionId;
    }

    public void setFormationVersionId(Long formationVersionId) {
        this.formationVersionId = formationVersionId;
    }

    public Long getSingerId() {
        return singerId;
    }

    public void setSingerId(Long singerId) {
        this.singerId = singerId;
    }

    public String getSingerNameSnapshot() {
        return singerNameSnapshot;
    }

    public void setSingerNameSnapshot(String singerNameSnapshot) {
        this.singerNameSnapshot = singerNameSnapshot;
    }

    public String getVoicePartSnapshot() {
        return voicePartSnapshot;
    }

    public void setVoicePartSnapshot(String voicePartSnapshot) {
        this.voicePartSnapshot = voicePartSnapshot;
    }

    public Long getVoiceSectionIdSnapshot() { return voiceSectionIdSnapshot; }
    public void setVoiceSectionIdSnapshot(Long value) { this.voiceSectionIdSnapshot = value; }
    public String getVoiceSectionNameSnapshot() { return voiceSectionNameSnapshot; }
    public void setVoiceSectionNameSnapshot(String value) { this.voiceSectionNameSnapshot = value; }
    public Integer getDivisionNumberSnapshot() { return divisionNumberSnapshot; }
    public void setDivisionNumberSnapshot(Integer value) { this.divisionNumberSnapshot = value; }

    public String getPositionLabel() {
        return positionLabel;
    }

    public void setPositionLabel(String positionLabel) {
        this.positionLabel = positionLabel;
    }

    public BigDecimal getXCoordinate() {
        return xCoordinate;
    }

    public void setXCoordinate(BigDecimal xCoordinate) {
        this.xCoordinate = xCoordinate;
    }

    public BigDecimal getYCoordinate() {
        return yCoordinate;
    }

    public void setYCoordinate(BigDecimal yCoordinate) {
        this.yCoordinate = yCoordinate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

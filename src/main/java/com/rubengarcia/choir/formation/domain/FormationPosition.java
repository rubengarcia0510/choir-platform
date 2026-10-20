package com.rubengarcia.choir.formation.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("formation_positions")
public class FormationPosition {

    @Id
    private Long id;

    @Column("formation_version_id")
    private Long formationVersionId;

    @Column("singer_id")
    private Long singerId;

    @Column("singer_name_snapshot")
    private String singerNameSnapshot;

    @Column("voice_part_snapshot")
    private String voicePartSnapshot;

    @Column("voice_section_id_snapshot")
    private Long voiceSectionIdSnapshot;

    @Column("voice_section_name_snapshot")
    private String voiceSectionNameSnapshot;

    @Column("division_number_snapshot")
    private Integer divisionNumberSnapshot;

    @Column("position_label")
    private String positionLabel;

    @Column("x_coordinate")
    private BigDecimal xCoordinate;

    @Column("y_coordinate")
    private BigDecimal yCoordinate;

    @Column("created_at")
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

package com.rubengarcia.choir.formation.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("formation_positions")
public class FormationPositionEntity {

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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFormationVersionId() { return formationVersionId; }
    public void setFormationVersionId(Long value) { this.formationVersionId = value; }
    public Long getSingerId() { return singerId; }
    public void setSingerId(Long value) { this.singerId = value; }
    public String getSingerNameSnapshot() { return singerNameSnapshot; }
    public void setSingerNameSnapshot(String value) { this.singerNameSnapshot = value; }
    public String getVoicePartSnapshot() { return voicePartSnapshot; }
    public void setVoicePartSnapshot(String value) { this.voicePartSnapshot = value; }
    public Long getVoiceSectionIdSnapshot() { return voiceSectionIdSnapshot; }
    public void setVoiceSectionIdSnapshot(Long value) { this.voiceSectionIdSnapshot = value; }
    public String getVoiceSectionNameSnapshot() { return voiceSectionNameSnapshot; }
    public void setVoiceSectionNameSnapshot(String value) { this.voiceSectionNameSnapshot = value; }
    public Integer getDivisionNumberSnapshot() { return divisionNumberSnapshot; }
    public void setDivisionNumberSnapshot(Integer value) { this.divisionNumberSnapshot = value; }
    public String getPositionLabel() { return positionLabel; }
    public void setPositionLabel(String value) { this.positionLabel = value; }
    public BigDecimal getXCoordinate() { return xCoordinate; }
    public void setXCoordinate(BigDecimal value) { this.xCoordinate = value; }
    public BigDecimal getYCoordinate() { return yCoordinate; }
    public void setYCoordinate(BigDecimal value) { this.yCoordinate = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
}

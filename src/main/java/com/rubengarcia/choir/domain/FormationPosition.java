package com.rubengarcia.choir.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "formation_positions")
public class FormationPosition {
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;

@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "formation_version_id", nullable = false)
private FormationVersion formationVersion;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "singer_id")
private Singer singer;

@Column(name = "singer_name_snapshot", nullable = false, length = 160)
private String singerNameSnapshot;

@Column(name = "voice_part_snapshot", length = 40)
private String voicePartSnapshot;

@Column(name = "position_label", length = 80)
private String positionLabel;

@Column(name = "x_coordinate", nullable = false, precision = 10, scale = 3)
private BigDecimal xCoordinate;

@Column(name = "y_coordinate", nullable = false, precision = 10, scale = 3)
private BigDecimal yCoordinate;

@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
private LocalDateTime createdAt;

protected FormationPosition() {}

public Long getId() { return id; }
public FormationVersion getFormationVersion() { return formationVersion; }
public void setFormationVersion(FormationVersion value) { this.formationVersion = value; }
public Singer getSinger() { return singer; }
public void setSinger(Singer singer) { this.singer = singer; }
public String getSingerNameSnapshot() { return singerNameSnapshot; }
public void setSingerNameSnapshot(String value) { this.singerNameSnapshot = value; }
public String getVoicePartSnapshot() { return voicePartSnapshot; }
public void setVoicePartSnapshot(String value) { this.voicePartSnapshot = value; }
public String getPositionLabel() { return positionLabel; }
public void setPositionLabel(String value) { this.positionLabel = value; }
public BigDecimal getXCoordinate() { return xCoordinate; }
public void setXCoordinate(BigDecimal value) { this.xCoordinate = value; }
public BigDecimal getYCoordinate() { return yCoordinate; }
public void setYCoordinate(BigDecimal value) { this.yCoordinate = value; }
public LocalDateTime getCreatedAt() { return createdAt; }

}

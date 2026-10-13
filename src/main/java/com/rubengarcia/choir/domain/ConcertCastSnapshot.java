package com.rubengarcia.choir.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "concert_cast_snapshots")
public class ConcertCastSnapshot {
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;

@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "concert_id", nullable = false)
private Concert concert;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "source_singer_id")
private Singer sourceSinger;

@Column(name = "singer_name_snapshot", nullable = false, length = 160)
private String singerNameSnapshot;

@Column(name = "voice_part_snapshot", length = 40)
private String voicePartSnapshot;

@Column(name = "captured_at", nullable = false, insertable = false, updatable = false)
private LocalDateTime capturedAt;

protected ConcertCastSnapshot() {}

public Long getId() { return id; }
public Concert getConcert() { return concert; }
public void setConcert(Concert concert) { this.concert = concert; }
public Singer getSourceSinger() { return sourceSinger; }
public void setSourceSinger(Singer sourceSinger) { this.sourceSinger = sourceSinger; }
public String getSingerNameSnapshot() { return singerNameSnapshot; }
public void setSingerNameSnapshot(String value) { this.singerNameSnapshot = value; }
public String getVoicePartSnapshot() { return voicePartSnapshot; }
public void setVoicePartSnapshot(String value) { this.voicePartSnapshot = value; }
public LocalDateTime getCapturedAt() { return capturedAt; }

}

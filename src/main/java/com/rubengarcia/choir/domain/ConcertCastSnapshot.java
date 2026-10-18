package com.rubengarcia.choir.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("concert_cast_snapshots")
public class ConcertCastSnapshot {
    @Id
    private Long id;

    @Column("concert_id")
    private Long concertId;

    @Column("source_singer_id")
    private Long sourceSingerId;

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

    @Column("captured_at")
    private LocalDateTime capturedAt;

    protected ConcertCastSnapshot() {}

    public Long getId() { return id; }
    public Long getConcertId() { return concertId; }
    public void setConcertId(Long concertId) { this.concertId = concertId; }
    public Long getSourceSingerId() { return sourceSingerId; }
    public void setSourceSingerId(Long sourceSingerId) { this.sourceSingerId = sourceSingerId; }
    public String getSingerNameSnapshot() { return singerNameSnapshot; }
    public void setSingerNameSnapshot(String singerNameSnapshot) { this.singerNameSnapshot = singerNameSnapshot; }
    public String getVoicePartSnapshot() { return voicePartSnapshot; }
    public void setVoicePartSnapshot(String voicePartSnapshot) { this.voicePartSnapshot = voicePartSnapshot; }
    public Long getVoiceSectionIdSnapshot() { return voiceSectionIdSnapshot; }
    public void setVoiceSectionIdSnapshot(Long value) { this.voiceSectionIdSnapshot = value; }
    public String getVoiceSectionNameSnapshot() { return voiceSectionNameSnapshot; }
    public void setVoiceSectionNameSnapshot(String value) { this.voiceSectionNameSnapshot = value; }
    public Integer getDivisionNumberSnapshot() { return divisionNumberSnapshot; }
    public void setDivisionNumberSnapshot(Integer value) { this.divisionNumberSnapshot = value; }
    public LocalDateTime getCapturedAt() { return capturedAt; }
}

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

    @Column("choir_id")
    private Long choirId;

    @Column("source_singer_id")
    private Long sourceSingerId;

    @Column("source_singer_choir_id")
    private Long sourceSingerChoirId;

    @Column("singer_name_snapshot")
    private String singerNameSnapshot;

    @Column("voice_part_snapshot")
    private String voicePartSnapshot;

    @Column("captured_at")
    private LocalDateTime capturedAt;

    protected ConcertCastSnapshot() {}

    public Long getId() { return id; }
    public Long getConcertId() { return concertId; }
    public Long getChoirId() { return choirId; }
    public void setChoirId(Long choirId) { this.choirId = choirId; }
    public void setConcertId(Long concertId) { this.concertId = concertId; }
    public Long getSourceSingerId() { return sourceSingerId; }
    public Long getSourceSingerChoirId() { return sourceSingerChoirId; }
    public void setSourceSingerChoirId(Long sourceSingerChoirId) {
        this.sourceSingerChoirId = sourceSingerChoirId;
    }
    public void setSourceSingerId(Long sourceSingerId) { this.sourceSingerId = sourceSingerId; }
    public String getSingerNameSnapshot() { return singerNameSnapshot; }
    public void setSingerNameSnapshot(String singerNameSnapshot) { this.singerNameSnapshot = singerNameSnapshot; }
    public String getVoicePartSnapshot() { return voicePartSnapshot; }
    public void setVoicePartSnapshot(String voicePartSnapshot) { this.voicePartSnapshot = voicePartSnapshot; }
    public LocalDateTime getCapturedAt() { return capturedAt; }
}

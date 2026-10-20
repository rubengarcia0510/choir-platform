package com.rubengarcia.choir.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("formations")
public class Formation {
    @Id
    private Long id;

    @Column("choir_id")
    private Long choirId;

    @Column("concert_id")
    private Long concertId;

    @Column("repertoire_item_id")
    private Long repertoireItemId;

    private String name;
    private String description;

    @Column("created_at")
    private LocalDateTime createdAt;

    protected Formation() {}

    public Long getId() { return id; }
    public Long getChoirId() { return choirId; }
    public void setChoirId(Long choirId) { this.choirId = choirId; }
    public Long getConcertId() { return concertId; }
    public void setConcertId(Long concertId) { this.concertId = concertId; }
    public Long getRepertoireItemId() { return repertoireItemId; }
    public void setRepertoireItemId(Long repertoireItemId) {
        this.repertoireItemId = repertoireItemId;
    }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

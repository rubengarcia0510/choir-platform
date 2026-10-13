package com.rubengarcia.choir.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("repertoire_items")
public class RepertoireItem {
    @Id
    private Long id;

    @Column("choir_id")
    private Long choirId;

    private String title;
    private String composer;
    private String arranger;
    private String notes;

    @Column("created_at")
    private LocalDateTime createdAt;

    protected RepertoireItem() {}

    public Long getId() { return id; }
    public Long getChoirId() { return choirId; }
    public void setChoirId(Long choirId) { this.choirId = choirId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getComposer() { return composer; }
    public void setComposer(String composer) { this.composer = composer; }
    public String getArranger() { return arranger; }
    public void setArranger(String arranger) { this.arranger = arranger; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

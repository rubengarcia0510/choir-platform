package com.rubengarcia.choir.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "repertoire_items")
public class RepertoireItem {
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;

@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "choir_id", nullable = false)
private Choir choir;

@Column(nullable = false, length = 200)
private String title;

@Column(length = 160)
private String composer;

@Column(length = 160)
private String arranger;

@Column(length = 2000)
private String notes;

@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
private LocalDateTime createdAt;

protected RepertoireItem() {}

public Long getId() { return id; }
public Choir getChoir() { return choir; }
public void setChoir(Choir choir) { this.choir = choir; }
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

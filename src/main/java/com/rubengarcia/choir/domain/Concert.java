package com.rubengarcia.choir.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "concerts")
public class Concert {
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;

@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "choir_id", nullable = false)
private Choir choir;

@Column(nullable = false, length = 200)
private String title;

@Column(name = "concert_date")
private LocalDate concertDate;

@Column(length = 250)
private String venue;

@Column(nullable = false, length = 20)
private String status = "PLANNED";

@Column(name = "published_at")
private LocalDateTime publishedAt;

@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
private LocalDateTime createdAt;

protected Concert() {}

public Long getId() { return id; }
public Choir getChoir() { return choir; }
public void setChoir(Choir choir) { this.choir = choir; }
public String getTitle() { return title; }
public void setTitle(String title) { this.title = title; }
public LocalDate getConcertDate() { return concertDate; }
public void setConcertDate(LocalDate concertDate) { this.concertDate = concertDate; }
public String getVenue() { return venue; }
public void setVenue(String venue) { this.venue = venue; }
public String getStatus() { return status; }
public void setStatus(String status) { this.status = status; }
public LocalDateTime getPublishedAt() { return publishedAt; }
public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
public LocalDateTime getCreatedAt() { return createdAt; }

}

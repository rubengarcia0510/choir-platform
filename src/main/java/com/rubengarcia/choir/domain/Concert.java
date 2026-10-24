package com.rubengarcia.choir.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Table("concerts")
public class Concert {

    @Id
    private Long id;

    @Column("choir_id")
    private Long choirId;

    private String title;

    @Column("concert_date")
    private LocalDate concertDate;

    private String venue;

    private String status = "PLANNED";

    @Column("published_at")
    private LocalDateTime publishedAt;

    @Column("created_at")
    private LocalDateTime createdAt;

    public Concert() {
    }

    public Long getId() {
        return id;
    }

    public Long getChoirId() {
        return choirId;
    }

    public void setChoirId(Long choirId) {
        this.choirId = choirId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDate getConcertDate() {
        return concertDate;
    }

    public void setConcertDate(LocalDate concertDate) {
        this.concertDate = concertDate;
    }

    public String getVenue() {
        return venue;
    }

    public void setVenue(String venue) {
        this.venue = venue;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

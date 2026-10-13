package com.rubengarcia.choir.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "absences")
public class Absence {
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;

@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "singer_id", nullable = false)
private Singer singer;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "concert_id")
private Concert concert;

@Column(name = "start_date", nullable = false)
private LocalDate startDate;

@Column(name = "end_date", nullable = false)
private LocalDate endDate;

@Column(length = 500)
private String reason;

@Column(nullable = false, length = 20)
private String status = "CONFIRMED";

@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
private LocalDateTime createdAt;

protected Absence() {}

public Long getId() { return id; }
public Singer getSinger() { return singer; }
public void setSinger(Singer singer) { this.singer = singer; }
public Concert getConcert() { return concert; }
public void setConcert(Concert concert) { this.concert = concert; }
public LocalDate getStartDate() { return startDate; }
public void setStartDate(LocalDate value) { this.startDate = value; }
public LocalDate getEndDate() { return endDate; }
public void setEndDate(LocalDate value) { this.endDate = value; }
public String getReason() { return reason; }
public void setReason(String value) { this.reason = value; }
public String getStatus() { return status; }
public void setStatus(String value) { this.status = value; }
public LocalDateTime getCreatedAt() { return createdAt; }

}

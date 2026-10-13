package com.rubengarcia.choir.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "transitions")
public class Transition {
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;

@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "choir_id", nullable = false)
private Choir choir;

@Column(nullable = false, length = 160)
private String name;

@Column(length = 1000)
private String description;

@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
private LocalDateTime createdAt;

protected Transition() {}

public Long getId() { return id; }
public Choir getChoir() { return choir; }
public void setChoir(Choir choir) { this.choir = choir; }
public String getName() { return name; }
public void setName(String name) { this.name = name; }
public String getDescription() { return description; }
public void setDescription(String description) { this.description = description; }
public LocalDateTime getCreatedAt() { return createdAt; }

}

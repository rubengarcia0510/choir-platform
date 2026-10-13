package com.rubengarcia.choir.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
name = "choirs",
uniqueConstraints = @UniqueConstraint(
name = "uq_choir_director_name",
columnNames = {"director_id", "name"}
)
)
public class Choir {

@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;

@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "director_id", nullable = false)
private Director director;

@Column(nullable = false, length = 160)
private String name;

@Column(length = 1000)
private String description;

@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
private LocalDateTime createdAt;

protected Choir() {
}

public Long getId() {
    return id;
}

public Director getDirector() {
    return director;
}

public void setDirector(Director director) {
    this.director = director;
}

public String getName() {
    return name;
}

public void setName(String name) {
    this.name = name;
}

public String getDescription() {
    return description;
}

public void setDescription(String description) {
    this.description = description;
}

public LocalDateTime getCreatedAt() {
    return createdAt;
}

}

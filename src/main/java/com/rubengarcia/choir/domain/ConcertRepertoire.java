package com.rubengarcia.choir.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "concert_repertoire")
public class ConcertRepertoire {
@EmbeddedId
private ConcertRepertoireId id;

@MapsId("concertId")
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "concert_id", nullable = false)
private Concert concert;

@MapsId("repertoireItemId")
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "repertoire_item_id", nullable = false)
private RepertoireItem repertoireItem;

@Column(name = "order_index", nullable = false)
private int orderIndex;

protected ConcertRepertoire() {}

public ConcertRepertoireId getId() { return id; }
public Concert getConcert() { return concert; }
public void setConcert(Concert concert) { this.concert = concert; }
public RepertoireItem getRepertoireItem() { return repertoireItem; }
public void setRepertoireItem(RepertoireItem item) { this.repertoireItem = item; }
public int getOrderIndex() { return orderIndex; }
public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }

}

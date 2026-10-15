package com.rubengarcia.choir.domain;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("concert_repertoire")
public class ConcertRepertoire {

    @Column("concert_id")
    private Long concertId;

    @Column("repertoire_item_id")
    private Long repertoireItemId;

    @Column("order_index")
    private int orderIndex;

    protected ConcertRepertoire() {
    }

    public ConcertRepertoire(Long concertId, Long repertoireItemId, int orderIndex) {
        this.concertId = concertId;
        this.repertoireItemId = repertoireItemId;
        this.orderIndex = orderIndex;
    }

    public Long getConcertId() {
        return concertId;
    }

    public Long getRepertoireItemId() {
        return repertoireItemId;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }
}

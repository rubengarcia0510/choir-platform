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

    @Column("duration_minutes")
    private Integer durationMinutes;

    @Column("stage_requirements")
    private String stageRequirements;

    protected ConcertRepertoire() {
    }

    public ConcertRepertoire(Long concertId, Long repertoireItemId, int orderIndex) {
        this(concertId, repertoireItemId, orderIndex, null, null);
    }

    public ConcertRepertoire(
            Long concertId,
            Long repertoireItemId,
            int orderIndex,
            Integer durationMinutes,
            String stageRequirements) {
        this.concertId = concertId;
        this.repertoireItemId = repertoireItemId;
        this.orderIndex = orderIndex;
        this.durationMinutes = durationMinutes;
        this.stageRequirements = stageRequirements;
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

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public String getStageRequirements() {
        return stageRequirements;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }
}

package com.rubengarcia.choir.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("vocal_plans")
public class VocalPlan {

    @Id
    private Long id;

    @Column("repertoire_item_id")
    private Long repertoireItemId;

    @Column("choir_id")
    private Long choirId;

    @Column("created_at")
    private LocalDateTime createdAt;

    protected VocalPlan() {
    }

    public Long getId() {
        return id;
    }

    public Long getRepertoireItemId() {
        return repertoireItemId;
    }

    public void setRepertoireItemId(Long repertoireItemId) {
        this.repertoireItemId = repertoireItemId;
    }

    public Long getChoirId() {
        return choirId;
    }

    public void setChoirId(Long choirId) {
        this.choirId = choirId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

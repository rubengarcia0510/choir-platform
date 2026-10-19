package com.rubengarcia.choir.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("scene_rules")
public class SceneRule {

    @Id
    private Long id;

    @Column("repertoire_item_id")
    private Long repertoireItemId;

    @Column("rule_order")
    private int ruleOrder;

    @Column("rule_type")
    private String ruleType;

    private String description;
    private boolean active = true;

    @Column("created_at")
    private LocalDateTime createdAt;

    public SceneRule() {
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

    public int getRuleOrder() {
        return ruleOrder;
    }

    public void setRuleOrder(int ruleOrder) {
        this.ruleOrder = ruleOrder;
    }

    public String getRuleType() {
        return ruleType;
    }

    public void setRuleType(String ruleType) {
        this.ruleType = ruleType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

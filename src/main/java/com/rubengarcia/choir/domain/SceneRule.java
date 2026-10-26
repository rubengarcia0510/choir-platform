package com.rubengarcia.choir.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import com.rubengarcia.choir.scene.domain.SceneRuleCondition;

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

    @Column("condition_json")
    private String conditionJson;

    @Transient
    private SceneRuleCondition condition;

    @Column("definition_status")
    private String definitionStatus = "DRAFT";

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

    public String getConditionJson() {
        return conditionJson;
    }

    public void setConditionJson(String conditionJson) {
        this.conditionJson = conditionJson;
    }

    public SceneRuleCondition getCondition() {
        return condition;
    }

    public void setCondition(SceneRuleCondition condition) {
        this.condition = condition;
    }

    public String getDefinitionStatus() {
        return definitionStatus;
    }

    public void setDefinitionStatus(String definitionStatus) {
        this.definitionStatus = definitionStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

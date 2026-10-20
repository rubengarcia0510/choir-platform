package com.rubengarcia.choir.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("scene_rule_revisions")
public class SceneRuleRevision {

    @Id
    private Long id;

    @Column("scene_rule_id")
    private Long sceneRuleId;

    @Column("revision_number")
    private int revisionNumber;

    @Column("repertoire_item_id")
    private Long repertoireItemId;

    @Column("rule_order")
    private int ruleOrder;

    @Column("rule_type")
    private String ruleType;

    private String description;
    private boolean active;

    @Column("revision_kind")
    private String revisionKind;

    public SceneRuleRevision() {
    }

    public Long getId() {
        return id;
    }

    public Long getSceneRuleId() {
        return sceneRuleId;
    }

    public void setSceneRuleId(Long sceneRuleId) {
        this.sceneRuleId = sceneRuleId;
    }

    public int getRevisionNumber() {
        return revisionNumber;
    }

    public void setRevisionNumber(int revisionNumber) {
        this.revisionNumber = revisionNumber;
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

    public String getRevisionKind() {
        return revisionKind;
    }

    public void setRevisionKind(String revisionKind) {
        this.revisionKind = revisionKind;
    }
}

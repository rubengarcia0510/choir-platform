package com.rubengarcia.choir.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("scene_rule_groups")
public class SceneRuleGroup {

    @Id
    private Long id;

    @Column("scene_rule_id")
    private Long sceneRuleId;

    @Column("group_order")
    private int groupOrder;

    @Column("group_label")
    private String groupLabel;

    @Column("member_count")
    private int memberCount;

    public SceneRuleGroup() {
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

    public int getGroupOrder() {
        return groupOrder;
    }

    public void setGroupOrder(int groupOrder) {
        this.groupOrder = groupOrder;
    }

    public String getGroupLabel() {
        return groupLabel;
    }

    public void setGroupLabel(String groupLabel) {
        this.groupLabel = groupLabel;
    }

    public int getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(int memberCount) {
        this.memberCount = memberCount;
    }
}

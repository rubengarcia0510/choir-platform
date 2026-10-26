package com.rubengarcia.choir.scene.domain;

import java.time.LocalDateTime;
import java.util.List;

public record SceneRuleRevisionSnapshot(
        long id,
        long sceneRuleId,
        int revisionNumber,
        long repertoireItemId,
        int ruleOrder,
        String ruleType,
        String description,
        boolean active,
        String definitionStatus,
        String revisionKind,
        LocalDateTime createdAt,
        List<GroupSnapshot> groups,
        SceneRuleCondition condition) {

    public SceneRuleRevisionSnapshot {
        groups = List.copyOf(groups);
    }

    public record GroupSnapshot(
            int groupOrder,
            String label,
            int memberCount,
            String compositionMode,
            List<Long> singerIds) {

        public GroupSnapshot {
            singerIds = List.copyOf(singerIds);
        }
    }
}

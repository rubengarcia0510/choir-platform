package com.rubengarcia.choir.scene.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record SceneRuleCondition(
        OrderingMode orderingMode,
        List<SpatialConstraint> spatialConstraints,
        List<ParticipantRef> exitParticipants) {

    public SceneRuleCondition {
        spatialConstraints = spatialConstraints == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(spatialConstraints));
        exitParticipants = exitParticipants == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(exitParticipants));
    }

    public enum OrderingMode {
        SEQUENTIAL,
        SIMULTANEOUS
    }

    public enum Relation {
        TOGETHER,
        NEAR,
        RELATIVE_ORDER,
        SEPARATED
    }

    public enum Stage {
        FROM,
        TO
    }

    public enum Direction {
        LEFT,
        RIGHT,
        FRONT,
        BACK
    }

    public enum ParticipantType {
        SINGER,
        GROUP
    }

    public record ParticipantRef(
            ParticipantType type,
            Long singerId,
            Integer groupOrder) {
    }

    public record SpatialConstraint(
            Relation relation,
            Stage stage,
            List<ParticipantRef> participants,
            List<ParticipantRef> comparedParticipants,
            BigDecimal threshold,
            Direction direction) {

        public SpatialConstraint {
            participants = participants == null
                    ? null
                    : Collections.unmodifiableList(new ArrayList<>(participants));
            comparedParticipants = comparedParticipants == null
                    ? null
                    : Collections.unmodifiableList(new ArrayList<>(comparedParticipants));
        }
    }
}

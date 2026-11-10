package com.rubengarcia.choir.transition.domain;

import java.util.List;

public record TransitionTrajectoryAnalysis(
        long transitionVersionId,
        List<TrajectoryFinding> crossings,
        List<GeometryIssue> geometryIssues) {

    public TransitionTrajectoryAnalysis {
        crossings = List.copyOf(crossings);
        geometryIssues = List.copyOf(geometryIssues);
    }

    public record TrajectoryFinding(
            long firstSingerId,
            long secondSingerId,
            TemporalConflictStatus temporalConflictStatus) {
    }

    public enum TemporalConflictStatus {
        CONFIRMED,
        NOT_CONCURRENT,
        NOT_EVALUABLE
    }

    public enum GeometryIssueCode {
        INVALID_SINGER_ID,
        DUPLICATE_SINGER_POSITION,
        INVALID_COORDINATE,
        MISSING_COUNTERPART
    }

    public record GeometryIssue(
            GeometryIssueCode code,
            String stage,
            Long singerId) {
    }
}

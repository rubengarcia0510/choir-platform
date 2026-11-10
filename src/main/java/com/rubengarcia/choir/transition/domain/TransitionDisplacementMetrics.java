package com.rubengarcia.choir.transition.domain;

import java.math.BigDecimal;
import java.util.List;

public record TransitionDisplacementMetrics(
        long transitionVersionId,
        long fromFormationVersionId,
        long toFormationVersionId,
        List<SingerDistance> singerDistances,
        BigDecimal totalDistance,
        BigDecimal maxIndividualDistance,
        int movingSingerCount,
        List<DataIssue> dataIssues) {

    public TransitionDisplacementMetrics {
        singerDistances = List.copyOf(singerDistances);
        dataIssues = List.copyOf(dataIssues);
    }

    public record SingerDistance(long singerId, BigDecimal distance) {
    }

    public enum Stage {
        FROM,
        TO
    }

    public enum DataIssueCode {
        INVALID_SINGER_ID,
        DUPLICATE_SINGER_POSITION,
        INVALID_COORDINATE,
        MISSING_COUNTERPART
    }

    public record DataIssue(
            DataIssueCode code,
            Stage stage,
            Long singerId) {
    }
}

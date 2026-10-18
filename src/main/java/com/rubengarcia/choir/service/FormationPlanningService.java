package com.rubengarcia.choir.service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

public interface FormationPlanningService {

    record PlanningPosition(
            long formationVersionId,
            long singerId,
            String singerName,
            String voicePart,
            Long voiceSectionId,
            String voiceSectionName,
            Integer divisionNumber,
            String positionLabel,
            BigDecimal xCoordinate,
            BigDecimal yCoordinate) {}

    record VocalAssignment(
            long concertId,
            long repertoireItemId,
            Long singerId,
            String singerName,
            String voicePart,
            Long voiceSectionId,
            String voiceSectionName,
            int divisionNumber) {}

    record TransitionPlanningPosition(
            long transitionVersionId,
            String stage,
            long formationVersionId,
            Long singerId,
            String singerName,
            String voicePart,
            Long voiceSectionId,
            String voiceSectionName,
            Integer divisionNumber,
            String positionLabel,
            BigDecimal xCoordinate,
            BigDecimal yCoordinate) {}

    Flux<PlanningPosition> positionsForFormationVersion(long formationVersionId);

    Flux<VocalAssignment> vocalAssignmentsForPlanning(
            long concertId, long repertoireItemId);

    Flux<TransitionPlanningPosition> positionsForTransitionVersion(
            long transitionVersionId);

    Mono<Void> updateDraftPosition(
            long positionId,
            String positionLabel,
            BigDecimal xCoordinate,
            BigDecimal yCoordinate);
}

package com.rubengarcia.choir.formation.application.usecase;

import com.rubengarcia.choir.formation.application.port.in.FormationProximityConstraintUseCase;
import com.rubengarcia.choir.formation.application.port.out.FormationOccurrenceAuthorizationPort;
import com.rubengarcia.choir.formation.application.port.out.FormationProximityConstraintPort;
import com.rubengarcia.choir.formation.domain.FormationProximityConstraint;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class FormationProximityConstraintService
        implements FormationProximityConstraintUseCase {

    private final FormationProximityConstraintPort constraintPort;
    private final FormationOccurrenceAuthorizationPort occurrenceAuthorization;

    public FormationProximityConstraintService(
            FormationProximityConstraintPort constraintPort,
            FormationOccurrenceAuthorizationPort occurrenceAuthorization) {
        this.constraintPort = constraintPort;
        this.occurrenceAuthorization = occurrenceAuthorization;
    }

    @Override
    public Flux<FormationProximityConstraint> list(
            String email,
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId) {
        return Flux.defer(() ->
                occurrenceAuthorization.requireOwnedOccurrence(
                                email, choirId, concertId, repertoireItemId)
                        .thenMany(Flux.defer(() ->
                                constraintPort.findForFormation(
                                        choirId,
                                        concertId,
                                        repertoireItemId,
                                        formationId))));
    }

    @Override
    public Mono<FormationProximityConstraint> create(
            String email,
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId,
            long singerOneId,
            long singerTwoId,
            BigDecimal maxDistance) {

        validatePair(singerOneId, singerTwoId);
        validateDistance(maxDistance);

        long firstSingerId = Math.min(singerOneId, singerTwoId);
        long secondSingerId = Math.max(singerOneId, singerTwoId);

        return Mono.defer(() ->
                occurrenceAuthorization.requireOwnedOccurrence(
                                email, choirId, concertId, repertoireItemId)
                        .then(Mono.defer(() ->
                                constraintPort.create(
                                        choirId,
                                        concertId,
                                        repertoireItemId,
                                        formationId,
                                        firstSingerId,
                                        secondSingerId,
                                        maxDistance))));
    }

    @Override
    public Mono<FormationProximityConstraint> update(
            String email,
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId,
            long constraintId,
            BigDecimal maxDistance,
            boolean active) {

        validateDistance(maxDistance);

        return Mono.defer(() ->
                occurrenceAuthorization.requireOwnedOccurrence(
                                email, choirId, concertId, repertoireItemId)
                        .then(Mono.defer(() ->
                                constraintPort.update(
                                        choirId,
                                        concertId,
                                        repertoireItemId,
                                        formationId,
                                        constraintId,
                                        maxDistance,
                                        active))));
    }

    @Override
    public Mono<Boolean> delete(
            String email,
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId,
            long constraintId) {
        return Mono.defer(() ->
                occurrenceAuthorization.requireOwnedOccurrence(
                                email, choirId, concertId, repertoireItemId)
                        .then(Mono.defer(() ->
                                constraintPort.delete(
                                        choirId,
                                        concertId,
                                        repertoireItemId,
                                        formationId,
                                        constraintId))));
    }

    private void validatePair(long singerOneId, long singerTwoId) {
        if (singerOneId <= 0 || singerTwoId <= 0) {
            throw new IllegalArgumentException(
                    "Los identificadores de los coreutas deben ser positivos");
        }
        if (singerOneId == singerTwoId) {
            throw new IllegalArgumentException(
                    "Una restricción requiere dos coreutas diferentes");
        }
    }

    private void validateDistance(BigDecimal maxDistance) {
        if (maxDistance == null || maxDistance.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "La distancia máxima debe ser mayor que cero");
        }
    }
}

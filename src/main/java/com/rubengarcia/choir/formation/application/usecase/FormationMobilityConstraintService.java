package com.rubengarcia.choir.formation.application.usecase;

import com.rubengarcia.choir.formation.application.exception.PhysicalConstraintConflictException;
import com.rubengarcia.choir.formation.application.port.in.FormationMobilityConstraintUseCase;
import com.rubengarcia.choir.formation.application.port.out.FormationMobilityConstraintPort;
import com.rubengarcia.choir.formation.domain.FormationMobilityConstraint;
import com.rubengarcia.choir.formation.domain.MobilityRegion;
import io.r2dbc.spi.R2dbcException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class FormationMobilityConstraintService
        implements FormationMobilityConstraintUseCase {

    /** SQLSTATE de violación de unicidad, igual en H2 y PostgreSQL. */
    private static final String UNIQUE_VIOLATION = "23505";

    private final FormationMobilityConstraintPort constraintPort;
    private final FormationOccurrenceContextAuthorizer authorizer;

    public FormationMobilityConstraintService(
            FormationMobilityConstraintPort constraintPort,
            FormationOccurrenceContextAuthorizer authorizer) {
        this.constraintPort = constraintPort;
        this.authorizer = authorizer;
    }

    @Override
    public Flux<FormationMobilityConstraint> list(
            String email,
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId) {
        return Flux.defer(() -> authorizer
                .authorize(email, choirId, concertId, repertoireItemId, formationId)
                .thenMany(Flux.defer(() -> constraintPort.findForFormation(
                        choirId, concertId, repertoireItemId, formationId)))
                .filter(FormationMobilityConstraint::active));
    }

    @Override
    public Mono<FormationMobilityConstraint> create(
            String email,
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId,
            long singerId,
            MobilityRegion region) {
        return Mono.defer(() -> authorizer
                        .authorize(email, choirId, concertId, repertoireItemId, formationId)
                        .then(Mono.defer(() -> constraintPort.create(
                                choirId, concertId, repertoireItemId,
                                formationId, singerId, region))))
                .onErrorMap(DataIntegrityViolationException.class,
                        error -> isUniqueViolation(error)
                                ? new PhysicalConstraintConflictException()
                                : error);
    }

    @Override
    public Mono<FormationMobilityConstraint> update(
            String email,
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId,
            long constraintId,
            MobilityRegion region,
            boolean active) {
        return Mono.defer(() -> authorizer
                .authorize(email, choirId, concertId, repertoireItemId, formationId)
                .then(Mono.defer(() -> constraintPort.update(
                        choirId, concertId, repertoireItemId,
                        formationId, constraintId, region, active))));
    }

    @Override
    public Mono<Boolean> delete(
            String email,
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId,
            long constraintId) {
        return Mono.defer(() -> authorizer
                .authorize(email, choirId, concertId, repertoireItemId, formationId)
                .then(Mono.defer(() -> constraintPort.delete(
                        choirId, concertId, repertoireItemId,
                        formationId, constraintId))));
    }

    private static boolean isUniqueViolation(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof R2dbcException r2dbc
                    && UNIQUE_VIOLATION.equals(r2dbc.getSqlState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}

package com.rubengarcia.choir.formation.application.usecase;

import com.rubengarcia.choir.formation.application.exception.FormationNotFoundException;
import com.rubengarcia.choir.formation.application.exception.PhysicalConstraintConflictException;
import com.rubengarcia.choir.formation.application.port.in.FormationMobilityConstraintUseCase;
import com.rubengarcia.choir.formation.application.port.out.FormationCatalogPort;
import com.rubengarcia.choir.formation.application.port.out.FormationMobilityConstraintPort;
import com.rubengarcia.choir.formation.application.port.out.FormationOccurrenceAuthorizationPort;
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
    private final FormationOccurrenceAuthorizationPort occurrenceAuthorization;
    private final FormationCatalogPort catalog;

    public FormationMobilityConstraintService(
            FormationMobilityConstraintPort constraintPort,
            FormationOccurrenceAuthorizationPort occurrenceAuthorization,
            FormationCatalogPort catalog) {
        this.constraintPort = constraintPort;
        this.occurrenceAuthorization = occurrenceAuthorization;
        this.catalog = catalog;
    }

    @Override
    public Flux<FormationMobilityConstraint> list(
            String email,
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId) {
        return Flux.defer(() -> authorizeContext(
                        email, choirId, concertId, repertoireItemId, formationId)
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
        return Mono.defer(() -> authorizeContext(
                        email, choirId, concertId, repertoireItemId, formationId)
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
        return Mono.defer(() -> authorizeContext(
                        email, choirId, concertId, repertoireItemId, formationId)
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
        return Mono.defer(() -> authorizeContext(
                        email, choirId, concertId, repertoireItemId, formationId)
                .then(Mono.defer(() -> constraintPort.delete(
                        choirId, concertId, repertoireItemId,
                        formationId, constraintId))));
    }

    /**
     * Acredita que el director es dueño de la ocurrencia y que la formación
     * pertenece a esa ocurrencia. La propiedad de la ocurrencia no implica por
     * sí sola la pertenencia de la formación.
     */
    private Mono<Void> authorizeContext(
            String email,
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId) {
        return occurrenceAuthorization
                .requireOwnedOccurrence(email, choirId, concertId, repertoireItemId)
                .then(Mono.defer(() -> requireFormationInOccurrence(
                        choirId, concertId, repertoireItemId, formationId)));
    }

    private Mono<Void> requireFormationInOccurrence(
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId) {
        return catalog.findForOccurrence(choirId, concertId, repertoireItemId)
                .filter(formation -> formation.getId().equals(formationId))
                .hasElements()
                .flatMap(found -> found
                        ? Mono.<Void>empty()
                        : Mono.<Void>error(new FormationNotFoundException()));
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

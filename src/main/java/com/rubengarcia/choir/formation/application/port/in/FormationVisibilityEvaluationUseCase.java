package com.rubengarcia.choir.formation.application.port.in;

import com.rubengarcia.choir.formation.domain.VisibilityEvaluation;
import reactor.core.publisher.Mono;

/** Evaluación de solo lectura de las restricciones de visibilidad contra el borrador. */
public interface FormationVisibilityEvaluationUseCase {

    Mono<VisibilityEvaluation> evaluate(
            String email,
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId);
}

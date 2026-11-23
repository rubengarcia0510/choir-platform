package com.rubengarcia.choir.formation.application.port.in;

import com.rubengarcia.choir.formation.domain.MobilityEvaluation;
import reactor.core.publisher.Mono;

/** Evaluación de solo lectura de las restricciones de movilidad contra el borrador. */
public interface FormationMobilityEvaluationUseCase {

    Mono<MobilityEvaluation> evaluate(
            String email,
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId);
}

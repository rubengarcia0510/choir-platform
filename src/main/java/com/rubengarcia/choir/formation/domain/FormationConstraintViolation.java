package com.rubengarcia.choir.formation.domain;

/**
 * Contrato común de las violaciones de restricciones de formación.
 * El detalle específico (coordenadas, ids, distancias, límites, motivos) sigue en cada record concreto.
 */
public interface FormationConstraintViolation {

    /** Código estable de la violación, por ejemplo LOCKED_POSITION_MOVED o MOBILITY_NOT_EVALUABLE. */
    String type();
}

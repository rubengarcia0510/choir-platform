package com.rubengarcia.choir.formation.domain;

/** Códigos estables de las violaciones de restricciones de movilidad. */
public enum MobilityConstraintViolationCode {
    /** La posición incumple uno o más límites de la región. */
    MOBILITY_CONSTRAINT_VIOLATION,
    /** No se pudo evaluar una restricción activa; nunca equivale a cumplimiento. */
    MOBILITY_NOT_EVALUABLE
}

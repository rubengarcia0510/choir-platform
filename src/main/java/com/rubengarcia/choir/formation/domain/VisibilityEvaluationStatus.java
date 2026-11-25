package com.rubengarcia.choir.formation.domain;

/**
 * EVALUABLE: todas las restricciones de visibilidad activas pudieron compararse.
 * NOT_EVALUABLE: al menos una comparación no pudo hacerse por datos faltantes.
 * Una lista vacía de hallazgos con NOT_EVALUABLE no significa visibilidad garantizada.
 */
public enum VisibilityEvaluationStatus {
    EVALUABLE,
    NOT_EVALUABLE
}

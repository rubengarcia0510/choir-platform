package com.rubengarcia.choir.formation.domain;

/**
 * EVALUABLE: todas las restricciones activas pudieron compararse con su posición.
 * NOT_EVALUABLE: al menos una restricción activa no pudo compararse por datos
 * faltantes; una lista vacía de hallazgos no significa cumplimiento.
 */
public enum MobilityEvaluationStatus {
    EVALUABLE,
    NOT_EVALUABLE
}

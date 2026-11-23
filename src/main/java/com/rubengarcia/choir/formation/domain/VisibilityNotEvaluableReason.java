package com.rubengarcia.choir.formation.domain;

/**
 * Motivos por los que una comparación de visibilidad no puede resolverse.
 * No existen motivos de ambigüedad: un coreuta con varias posiciones se evalúa
 * posición por posición.
 */
public enum VisibilityNotEvaluableReason {
    /** El coreuta protegido no tiene ninguna posición en el DRAFT. */
    MISSING_PROTECTED_DRAFT_POSITION,
    /** Una posición protegida no tiene X o Y. */
    MISSING_PROTECTED_COORDINATES,
    /** Una posición protegida no tiene heightCmSnapshot. */
    MISSING_PROTECTED_HEIGHT,
    /** Un coreuta asignado a la formación no tiene ninguna posición en el DRAFT. */
    MISSING_OBSTRUCTOR_DRAFT_POSITION,
    /** Una posición de un obstructor no tiene X o Y; no se puede descartar que obstruya. */
    MISSING_OBSTRUCTOR_COORDINATES,
    /** Una posición de un obstructor dentro de la tolerancia y delante no tiene altura. */
    MISSING_OBSTRUCTOR_HEIGHT,
    /** Hay una posición en el DRAFT de un coreuta que no está en la lista asignada. */
    POSITION_NOT_IN_ASSIGNED_ROSTER
}

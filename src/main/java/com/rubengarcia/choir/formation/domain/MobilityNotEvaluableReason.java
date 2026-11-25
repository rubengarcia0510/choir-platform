package com.rubengarcia.choir.formation.domain;

public enum MobilityNotEvaluableReason {
    /** El coreuta no tiene posición en el borrador. */
    MISSING_DRAFT_POSITION,
    /** El coreuta tiene más de una posición en el borrador; no se elige una. */
    AMBIGUOUS_DRAFT_POSITION,
    /** La posición no tiene coordenada X o Y. */
    MISSING_COORDINATES
}

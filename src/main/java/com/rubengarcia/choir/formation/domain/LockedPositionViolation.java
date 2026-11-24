package com.rubengarcia.choir.formation.domain;

import java.math.BigDecimal;

/**
 * Una posición bloqueada que sigue presente en la propuesta con coordenadas distintas
 * (o sin coordenadas) respecto del estado persistido.
 */
public record LockedPositionViolation(
        Long singerId,
        BigDecimal persistedX,
        BigDecimal persistedY,
        BigDecimal proposedX,
        BigDecimal proposedY)
        implements FormationConstraintViolation {

    public static final String TYPE = "LOCKED_POSITION_MOVED";

    public String type() {
        return TYPE;
    }
}

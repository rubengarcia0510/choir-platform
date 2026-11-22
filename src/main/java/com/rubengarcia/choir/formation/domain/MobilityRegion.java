package com.rubengarcia.choir.formation.domain;

import java.math.BigDecimal;

/**
 * Región rectangular de movilidad en el plano de coordenadas de la formación.
 *
 * <p>Los límites son inclusivos y se expresan en el mismo sistema de
 * coordenadas que {@code formation_positions.x_coordinate} e
 * {@code y_coordinate}. No se asume un rango fijo ni se agrega una coordenada Z.
 * Esta clase valida sus invariantes; no evalúa si una posición pertenece a la
 * región.</p>
 */
public record MobilityRegion(
        BigDecimal minX,
        BigDecimal maxX,
        BigDecimal minY,
        BigDecimal maxY) {

    public MobilityRegion {
        requireBound(minX, "minX");
        requireBound(maxX, "maxX");
        requireBound(minY, "minY");
        requireBound(maxY, "maxY");

        if (minX.compareTo(maxX) >= 0) {
            throw new IllegalArgumentException("minX debe ser menor que maxX");
        }
        if (minY.compareTo(maxY) >= 0) {
            throw new IllegalArgumentException("minY debe ser menor que maxY");
        }
    }

    private static void requireBound(BigDecimal value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " es obligatorio");
        }
    }
}

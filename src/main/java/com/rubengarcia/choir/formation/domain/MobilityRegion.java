package com.rubengarcia.choir.formation.domain;

import java.math.BigDecimal;

/**
 * Región rectangular de movilidad en el escenario.
 *
 * <p>Escala: porcentajes normalizados entre 0 y 100, inclusive. X crece de
 * izquierda a derecha desde la perspectiva del director. Y crece desde el fondo
 * (y = 0, borde superior de la UI) hasta el frente (y = 100, borde inferior).
 * El origen (0, 0) es la esquina superior izquierda. No hay coordenada Z.</p>
 *
 * <p>Los límites son inclusivos y deben cumplir minX &lt; maxX y minY &lt; maxY.
 * Esta clase valida sus invariantes; no evalúa si una posición pertenece a la región.</p>
 */
public record MobilityRegion(
        BigDecimal minX,
        BigDecimal maxX,
        BigDecimal minY,
        BigDecimal maxY) {

    public static final BigDecimal SCALE_MIN = BigDecimal.ZERO;
    public static final BigDecimal SCALE_MAX = new BigDecimal("100");

    public MobilityRegion {
        requireBound(minX, "minX");
        requireBound(maxX, "maxX");
        requireBound(minY, "minY");
        requireBound(maxY, "maxY");

        requireInScale(minX, "minX");
        requireInScale(maxX, "maxX");
        requireInScale(minY, "minY");
        requireInScale(maxY, "maxY");

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

    private static void requireInScale(BigDecimal value, String name) {
        if (value.compareTo(SCALE_MIN) < 0 || value.compareTo(SCALE_MAX) > 0) {
            throw new IllegalArgumentException(
                    name + " debe estar entre 0 y 100 (inclusive)");
        }
    }
}

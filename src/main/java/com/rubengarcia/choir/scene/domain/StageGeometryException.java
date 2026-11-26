package com.rubengarcia.choir.scene.domain;

/** Violación de una invariante geométrica del escenario. Unidad canónica: centímetros. */
public class StageGeometryException extends IllegalArgumentException {

    public StageGeometryException(String message) {
        super(message);
    }
}

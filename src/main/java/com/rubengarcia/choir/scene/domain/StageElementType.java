package com.rubengarcia.choir.scene.domain;

/**
 * Tipo de elemento físico. Solo {@link #STEP} y {@link #PLATFORM} admiten elevación positiva;
 * el resto ocupa la superficie al nivel de referencia.
 */
public enum StageElementType {
    BENCH,
    PIANO,
    FURNITURE,
    OBSTACLE,
    STEP,
    PLATFORM;

    public boolean allowsElevation() {
        return this == STEP || this == PLATFORM;
    }
}

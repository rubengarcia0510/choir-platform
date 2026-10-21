package com.rubengarcia.choir.formation.application.usecase;

/**
 * Indica que no existe la posición de formación solicitada.
 */
public class FormationPositionNotFoundException extends RuntimeException {

    public FormationPositionNotFoundException(long positionId) {
        super("No existe la posición de formación: " + positionId);
    }
}

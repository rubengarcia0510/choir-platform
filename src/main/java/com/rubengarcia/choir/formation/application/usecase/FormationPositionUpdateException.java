package com.rubengarcia.choir.formation.application.usecase;

/**
 * Indica que no se pudo actualizar una posición de formación.
 */
public class FormationPositionUpdateException extends RuntimeException {

    public FormationPositionUpdateException() {
        super("La posición no se pudo actualizar");
    }
}

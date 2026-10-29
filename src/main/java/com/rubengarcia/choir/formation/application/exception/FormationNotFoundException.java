package com.rubengarcia.choir.formation.application.exception;

public class FormationNotFoundException extends RuntimeException {

    public FormationNotFoundException(long formationId) {
        super("No existe la formación: " + formationId);
    }
}

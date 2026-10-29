package com.rubengarcia.choir.formation.application.exception;

public class FormationVersionNotFoundException extends RuntimeException {

    public FormationVersionNotFoundException(long versionId) {
        super("No existe la versión de formación: " + versionId);
    }

    public FormationVersionNotFoundException(String message) {
        super(message);
    }
}

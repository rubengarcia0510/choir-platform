package com.rubengarcia.choir.formation.application.exception;

public class FormationVersionNotFoundException extends RuntimeException {

    public FormationVersionNotFoundException() {
        super("Versión inexistente o fuera de la formación");
    }
}

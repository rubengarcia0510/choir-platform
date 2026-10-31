package com.rubengarcia.choir.formation.application.exception;

public class FormationNotFoundException extends RuntimeException {

    public FormationNotFoundException() {
        super("Formación inexistente o fuera del coro");
    }
}

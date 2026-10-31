package com.rubengarcia.choir.formation.application.exception;

public class FormationOccurrenceNotFoundException extends RuntimeException {

    public FormationOccurrenceNotFoundException() {
        super("La ocurrencia de repertorio no existe");
    }
}

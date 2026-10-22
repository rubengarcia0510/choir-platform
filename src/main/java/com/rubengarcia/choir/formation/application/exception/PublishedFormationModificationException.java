package com.rubengarcia.choir.formation.application.exception;

/**
 * Indica que se intentó modificar una posición de una formación publicada.
 */
public class PublishedFormationModificationException extends RuntimeException {

    public PublishedFormationModificationException() {
        super("No se puede modificar una formación publicada");
    }
}

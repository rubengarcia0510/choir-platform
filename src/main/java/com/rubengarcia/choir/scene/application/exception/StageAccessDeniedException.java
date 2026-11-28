package com.rubengarcia.choir.scene.application.exception;

/** El recurso existe pero el director autenticado no es su propietario. Se traduce a 403. */
public class StageAccessDeniedException extends RuntimeException {

    public StageAccessDeniedException() {
        super("El director no tiene acceso a este recurso");
    }
}

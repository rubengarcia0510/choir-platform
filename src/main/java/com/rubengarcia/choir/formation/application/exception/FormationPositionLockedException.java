package com.rubengarcia.choir.formation.application.exception;

public class FormationPositionLockedException extends RuntimeException {

    public FormationPositionLockedException() {
        super("No se puede mover una posición bloqueada");
    }
}

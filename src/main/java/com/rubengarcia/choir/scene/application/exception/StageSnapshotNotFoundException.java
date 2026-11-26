package com.rubengarcia.choir.scene.application.exception;

public class StageSnapshotNotFoundException extends RuntimeException {

    public StageSnapshotNotFoundException() {
        super("La inclusión del repertorio no tiene escenario asociado");
    }
}

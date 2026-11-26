package com.rubengarcia.choir.scene.application.exception;

public class StageSnapshotAlreadyAssociatedException extends RuntimeException {

    public StageSnapshotAlreadyAssociatedException() {
        super("La inclusión del repertorio ya tiene un escenario asociado");
    }
}

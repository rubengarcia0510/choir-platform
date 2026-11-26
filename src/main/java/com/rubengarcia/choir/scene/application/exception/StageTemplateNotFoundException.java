package com.rubengarcia.choir.scene.application.exception;

public class StageTemplateNotFoundException extends RuntimeException {

    public StageTemplateNotFoundException() {
        super("No existe la plantilla de escenario indicada");
    }
}

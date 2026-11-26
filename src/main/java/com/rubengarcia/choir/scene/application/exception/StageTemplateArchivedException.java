package com.rubengarcia.choir.scene.application.exception;

public class StageTemplateArchivedException extends RuntimeException {

    public StageTemplateArchivedException() {
        super("La plantilla de escenario está archivada");
    }
}

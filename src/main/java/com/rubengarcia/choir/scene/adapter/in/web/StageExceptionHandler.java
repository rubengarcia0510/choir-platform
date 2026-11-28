package com.rubengarcia.choir.scene.adapter.in.web;

import com.rubengarcia.choir.scene.application.exception.StageAccessDeniedException;
import com.rubengarcia.choir.scene.application.exception.StageSnapshotAlreadyAssociatedException;
import com.rubengarcia.choir.scene.application.exception.StageSnapshotNotFoundException;
import com.rubengarcia.choir.scene.application.exception.StageTemplateArchivedException;
import com.rubengarcia.choir.scene.application.exception.StageTemplateNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebInputException;

/**
 * Traduce las excepciones de escenarios a respuestas HTTP. Solo alcanza a los controllers de escena; no
 * modifica el manejo de otros dominios. Los detalles de infraestructura nunca se exponen.
 */
@RestControllerAdvice(assignableTypes = {StageTemplateController.class, ConcertStageController.class})
public class StageExceptionHandler {

    @ExceptionHandler({StageTemplateNotFoundException.class, StageSnapshotNotFoundException.class})
    public ProblemDetail notFound(Exception exception) {
        return problem(HttpStatus.NOT_FOUND, "El escenario o la inclusión solicitados no existen");
    }

    @ExceptionHandler(StageAccessDeniedException.class)
    public ProblemDetail forbidden(Exception exception) {
        return problem(HttpStatus.FORBIDDEN, "El director no tiene acceso a este recurso");
    }

    @ExceptionHandler({
        StageTemplateArchivedException.class,
        StageSnapshotAlreadyAssociatedException.class,
        DataIntegrityViolationException.class
    })
    public ProblemDetail conflict(Exception exception) {
        return problem(HttpStatus.CONFLICT, "La operación no puede completarse en el estado actual");
    }

    @ExceptionHandler({
        IllegalArgumentException.class,
        WebExchangeBindException.class,
        HandlerMethodValidationException.class,
        ServerWebInputException.class,
        ConstraintViolationException.class
    })
    public ProblemDetail badRequest(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, "Los datos del escenario no son válidos");
    }

    /** Respuestas de autorización (401 o 404) que llegan ya tipadas desde el adaptador de salida. */
    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail responseStatus(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        return problem(status, status == HttpStatus.NOT_FOUND
                ? "El escenario o la inclusión solicitados no existen"
                : "No autenticado");
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }
}

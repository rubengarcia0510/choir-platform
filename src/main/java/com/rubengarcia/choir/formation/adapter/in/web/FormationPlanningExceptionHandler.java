package com.rubengarcia.choir.formation.adapter.in.web;

import com.rubengarcia.choir.api.FormationVersionController;
import com.rubengarcia.choir.formation.application.exception.FormationLifecycleConflictException;
import com.rubengarcia.choir.formation.application.exception.FormationNotFoundException;
import com.rubengarcia.choir.formation.application.exception.FormationOccurrenceNotFoundException;
import com.rubengarcia.choir.formation.application.exception.FormationPositionNotFoundException;
import com.rubengarcia.choir.formation.application.exception.FormationPositionUpdateException;
import com.rubengarcia.choir.formation.application.exception.FormationSelectionConflictException;
import com.rubengarcia.choir.formation.application.exception.FormationVersionNotFoundException;
import com.rubengarcia.choir.formation.application.exception.PublishedFormationModificationException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;

@RestControllerAdvice(assignableTypes = {
        FormationDraftController.class,
        FormationVersionController.class,
        FormationVersionSelectionController.class
})
public class FormationPlanningExceptionHandler {

    private static final String NOT_FOUND_DETAIL =
            "El recurso solicitado no existe o no está disponible";
    private static final String INVALID_REQUEST_DETAIL =
            "La solicitud contiene datos inválidos";
    private static final String CONFLICT_DETAIL =
            "La operación no puede completarse en el estado actual";
    private static final String UNAUTHENTICATED_DETAIL =
            "Se requiere autenticación";
    private static final String FORBIDDEN_DETAIL =
            "No está autorizado para realizar esta operación";

    @ExceptionHandler({
            FormationPositionNotFoundException.class,
            FormationNotFoundException.class,
            FormationVersionNotFoundException.class,
            FormationOccurrenceNotFoundException.class
    })
    public ProblemDetail handleNotFound(RuntimeException exception) {
        return problem(HttpStatus.NOT_FOUND, NOT_FOUND_DETAIL);
    }

    @ExceptionHandler({
            WebExchangeBindException.class,
            HandlerMethodValidationException.class,
            ServerWebInputException.class,
            ConstraintViolationException.class
    })
    public ProblemDetail handleInvalidRequest(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, INVALID_REQUEST_DETAIL);
    }

    @ExceptionHandler({
            FormationLifecycleConflictException.class,
            FormationSelectionConflictException.class,
            PublishedFormationModificationException.class,
            FormationPositionUpdateException.class
    })
    public ProblemDetail handleConflict(RuntimeException exception) {
        return problem(HttpStatus.CONFLICT, CONFLICT_DETAIL);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleStatusException(
            ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String detail = switch (status) {
            case BAD_REQUEST -> INVALID_REQUEST_DETAIL;
            case UNAUTHORIZED -> UNAUTHENTICATED_DETAIL;
            case FORBIDDEN -> FORBIDDEN_DETAIL;
            case NOT_FOUND -> NOT_FOUND_DETAIL;
            case CONFLICT -> CONFLICT_DETAIL;
            default -> "No se pudo completar la solicitud";
        };
        return problem(status, detail);
    }

    private ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        return problem;
    }
}

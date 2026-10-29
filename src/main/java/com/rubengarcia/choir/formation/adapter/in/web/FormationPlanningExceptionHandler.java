package com.rubengarcia.choir.formation.adapter.in.web;

import com.rubengarcia.choir.formation.application.exception.FormationNotFoundException;
import com.rubengarcia.choir.formation.application.exception.FormationPositionNotFoundException;
import com.rubengarcia.choir.formation.application.exception.FormationPositionUpdateException;
import com.rubengarcia.choir.formation.application.exception.FormationVersionNotFoundException;
import com.rubengarcia.choir.formation.application.exception.FormationVersionPublicationException;
import com.rubengarcia.choir.formation.application.exception.PublishedFormationModificationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class FormationPlanningExceptionHandler {

    @ExceptionHandler(FormationPositionNotFoundException.class)
    public ProblemDetail handlePositionNotFound(
            FormationPositionNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(FormationNotFoundException.class)
    public ProblemDetail handleFormationNotFound(
            FormationNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(FormationVersionNotFoundException.class)
    public ProblemDetail handleFormationVersionNotFound(
            FormationVersionNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(PublishedFormationModificationException.class)
    public ProblemDetail handlePublishedFormationModification(
            PublishedFormationModificationException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(FormationPositionUpdateException.class)
    public ProblemDetail handlePositionUpdate(
            FormationPositionUpdateException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(FormationVersionPublicationException.class)
    public ProblemDetail handleFormationVersionPublication(
            FormationVersionPublicationException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, exception.getMessage());
    }
}

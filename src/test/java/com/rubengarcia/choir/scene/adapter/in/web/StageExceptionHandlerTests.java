package com.rubengarcia.choir.scene.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.rubengarcia.choir.scene.application.exception.StageAccessDeniedException;
import com.rubengarcia.choir.scene.application.exception.StageSnapshotAlreadyAssociatedException;
import com.rubengarcia.choir.scene.application.exception.StageSnapshotNotFoundException;
import com.rubengarcia.choir.scene.application.exception.StageTemplateArchivedException;
import com.rubengarcia.choir.scene.application.exception.StageTemplateNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class StageExceptionHandlerTests {

    private final StageExceptionHandler handler = new StageExceptionHandler();

    @Test
    void missingResourcesAnswerNotFound() {
        assertThat(handler.notFound(new StageTemplateNotFoundException()).getStatus()).isEqualTo(404);
        assertThat(handler.notFound(new StageSnapshotNotFoundException()).getStatus()).isEqualTo(404);
    }

    @Test
    void existingResourceOwnedByAnotherDirectorAnswersForbidden() {
        assertThat(handler.forbidden(new StageAccessDeniedException()).getStatus()).isEqualTo(403);
    }

    @Test
    void stateConflictsAnswerConflict() {
        assertThat(handler.conflict(new StageTemplateArchivedException()).getStatus()).isEqualTo(409);
        assertThat(handler.conflict(new StageSnapshotAlreadyAssociatedException()).getStatus()).isEqualTo(409);
    }

    @Test
    void invalidGeometryAnswersBadRequest() {
        assertThat(handler.badRequest(new IllegalArgumentException("x")).getStatus()).isEqualTo(400);
    }

    @Test
    void authorizationResponseStatusIsPreserved() {
        assertThat(handler.responseStatus(new ResponseStatusException(HttpStatus.UNAUTHORIZED)).getStatus())
                .isEqualTo(401);
        assertThat(handler.responseStatus(new ResponseStatusException(HttpStatus.NOT_FOUND)).getStatus())
                .isEqualTo(404);
    }

    @Test
    void responsesNeverLeakInfrastructureDetails() {
        String detail = handler.conflict(new IllegalStateException("SQL: ... constraint fk_xyz")).getDetail();

        assertThat(detail).doesNotContain("SQL").doesNotContain("constraint");
    }
}

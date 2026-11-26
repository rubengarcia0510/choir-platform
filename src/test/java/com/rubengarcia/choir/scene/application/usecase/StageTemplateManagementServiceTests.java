package com.rubengarcia.choir.scene.application.usecase;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rubengarcia.choir.scene.application.exception.StageTemplateArchivedException;
import com.rubengarcia.choir.scene.application.exception.StageTemplateNotFoundException;
import com.rubengarcia.choir.scene.application.port.out.StageAuthorizationPort;
import com.rubengarcia.choir.scene.application.port.out.StageTemplatePersistencePort;
import com.rubengarcia.choir.scene.domain.StageBoundary;
import com.rubengarcia.choir.scene.domain.StageBoundaryPoint;
import com.rubengarcia.choir.scene.domain.StageTemplate;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class StageTemplateManagementServiceTests {

    private static final String EMAIL = "director@test.com";
    private static final long CHOIR_ID = 1L;
    private static final long STAGE_ID = 10L;

    @Mock
    private StageTemplatePersistencePort templates;

    @Mock
    private StageAuthorizationPort authorization;

    @InjectMocks
    private StageTemplateManagementService service;

    @Test
    void listAuthorizesTheChoirBeforeReadingTemplates() {
        when(authorization.requireOwnedChoir(EMAIL, CHOIR_ID)).thenReturn(Mono.empty());
        when(templates.findByChoir(CHOIR_ID)).thenReturn(Flux.just(active()));

        StepVerifier.create(service.list(EMAIL, CHOIR_ID)).expectNextCount(1).verifyComplete();

        InOrder order = inOrder(authorization, templates);
        order.verify(authorization).requireOwnedChoir(EMAIL, CHOIR_ID);
        order.verify(templates).findByChoir(CHOIR_ID);
    }

    @Test
    void directorOfAnotherChoirNeverReachesPersistence() {
        when(authorization.requireOwnedChoir(EMAIL, CHOIR_ID))
                .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));

        StepVerifier.create(service.get(EMAIL, CHOIR_ID, STAGE_ID))
                .expectError(ResponseStatusException.class)
                .verify();

        verifyNoInteractions(templates);
    }

    @Test
    void getReportsNotFoundWhenTheTemplateIsMissing() {
        when(authorization.requireOwnedChoir(EMAIL, CHOIR_ID)).thenReturn(Mono.empty());
        when(templates.findById(CHOIR_ID, STAGE_ID)).thenReturn(Mono.empty());

        StepVerifier.create(service.get(EMAIL, CHOIR_ID, STAGE_ID))
                .expectError(StageTemplateNotFoundException.class)
                .verify();
    }

    @Test
    void updateRejectsAReplacementThatDoesNotMatchTheRequestedTemplate() {
        when(authorization.requireOwnedChoir(EMAIL, CHOIR_ID)).thenReturn(Mono.empty());

        StepVerifier.create(service.update(EMAIL, CHOIR_ID, STAGE_ID, withId(99L)))
                .expectError(IllegalArgumentException.class)
                .verify();

        verify(templates, never()).save(any());
    }

    @Test
    void updateRejectsArchivedTemplates() {
        when(authorization.requireOwnedChoir(EMAIL, CHOIR_ID)).thenReturn(Mono.empty());
        when(templates.findById(CHOIR_ID, STAGE_ID)).thenReturn(Mono.just(archived()));

        StepVerifier.create(service.update(EMAIL, CHOIR_ID, STAGE_ID, withId(STAGE_ID)))
                .expectError(StageTemplateArchivedException.class)
                .verify();

        verify(templates, never()).save(any());
    }

    @Test
    void archiveKeepsTheRecordAndMarksItArchived() {
        when(authorization.requireOwnedChoir(EMAIL, CHOIR_ID)).thenReturn(Mono.empty());
        when(templates.findById(CHOIR_ID, STAGE_ID)).thenReturn(Mono.just(active()));
        when(templates.save(any(StageTemplate.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.archive(EMAIL, CHOIR_ID, STAGE_ID))
                .assertNext(archived -> {
                    assertTrue(archived.archived());
                    assertEquals("Escenario", archived.name());
                })
                .verifyComplete();

        ArgumentCaptor<StageTemplate> saved = ArgumentCaptor.forClass(StageTemplate.class);
        verify(templates).save(saved.capture());
        assertTrue(saved.getValue().archived());
    }

    @Test
    void archivingAnArchivedTemplateIsIdempotent() {
        when(authorization.requireOwnedChoir(EMAIL, CHOIR_ID)).thenReturn(Mono.empty());
        when(templates.findById(CHOIR_ID, STAGE_ID)).thenReturn(Mono.just(archived()));

        StepVerifier.create(service.archive(EMAIL, CHOIR_ID, STAGE_ID))
                .assertNext(archived -> assertTrue(archived.archived()))
                .verifyComplete();

        verify(templates, never()).save(any());
    }

    @Test
    void invalidInputIsRejectedBeforeAnyAuthorizationOrPersistence() {
        StepVerifier.create(service.get(EMAIL, 0L, STAGE_ID))
                .expectError(IllegalArgumentException.class)
                .verify();
        StepVerifier.create(service.list("  ", CHOIR_ID))
                .expectError(IllegalArgumentException.class)
                .verify();

        verifyNoInteractions(authorization, templates);
    }

    private static StageTemplate active() {
        return template(false);
    }

    private static StageTemplate archived() {
        return template(true);
    }

    private static StageTemplate withId(long id) {
        return new StageTemplate(id, CHOIR_ID, "Escenario", bd(800), bd(600),
                rectangle(), List.of(), List.of(), false);
    }

    private static StageTemplate template(boolean archived) {
        return new StageTemplate(STAGE_ID, CHOIR_ID, "Escenario", bd(800), bd(600),
                rectangle(), List.of(), List.of(), archived);
    }

    private static StageBoundary rectangle() {
        return new StageBoundary(List.of(
                new StageBoundaryPoint(bd(0), bd(0)),
                new StageBoundaryPoint(bd(800), bd(0)),
                new StageBoundaryPoint(bd(800), bd(600)),
                new StageBoundaryPoint(bd(0), bd(600))));
    }

    private static BigDecimal bd(int value) {
        return new BigDecimal(value);
    }
}

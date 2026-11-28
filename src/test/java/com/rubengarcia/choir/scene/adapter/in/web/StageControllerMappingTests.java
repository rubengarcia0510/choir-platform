package com.rubengarcia.choir.scene.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rubengarcia.choir.scene.adapter.in.web.StageDtos.AssociateStageRequest;
import com.rubengarcia.choir.scene.adapter.in.web.StageDtos.PointDto;
import com.rubengarcia.choir.scene.application.port.in.StageGeometryQueryUseCase;
import com.rubengarcia.choir.scene.application.port.in.StageSnapshotUseCase;
import com.rubengarcia.choir.scene.application.port.in.StageTemplateUseCase;
import com.rubengarcia.choir.scene.application.model.StageGeometry;
import com.rubengarcia.choir.scene.domain.StageBoundary;
import com.rubengarcia.choir.scene.domain.StageBoundaryPoint;
import com.rubengarcia.choir.scene.domain.StageSnapshot;
import com.rubengarcia.choir.scene.domain.StageTemplate;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class StageControllerMappingTests {

    private static final String EMAIL = "director@test.com";

    @Mock
    private StageTemplateUseCase templates;

    @Mock
    private StageSnapshotUseCase snapshots;

    @Mock
    private StageGeometryQueryUseCase geometry;

    @Mock
    private Authentication authentication;

    @Test
    void templateListUsesTheAuthenticatedDirectorAndTheChoirPath() {
        when(authentication.getName()).thenReturn(EMAIL);
        when(templates.list(EMAIL, 1L)).thenReturn(Flux.just(template(10L)));
        StageTemplateController controller = new StageTemplateController(templates);

        StepVerifier.create(controller.list(authentication, 1L))
                .assertNext(response -> assertThat(response.id()).isEqualTo(10L))
                .verifyComplete();
    }

    @Test
    void templateUpdateBindsTheStagePathIdentityIntoTheReplacement() {
        when(authentication.getName()).thenReturn(EMAIL);
        when(templates.update(eq(EMAIL), eq(1L), eq(10L), any()))
                .thenReturn(Mono.just(template(10L)));
        StageTemplateController controller = new StageTemplateController(templates);

        StepVerifier.create(controller.update(authentication, 1L, 10L,
                        request()))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<StageTemplate> replacement = ArgumentCaptor.forClass(StageTemplate.class);
        verify(templates).update(eq(EMAIL), eq(1L), eq(10L), replacement.capture());
        assertThat(replacement.getValue().id()).isEqualTo(10L);
        assertThat(replacement.getValue().choirId()).isEqualTo(1L);
    }

    @Test
    void templateCreateAnswersCreated() {
        when(authentication.getName()).thenReturn(EMAIL);
        when(templates.create(eq(EMAIL), any())).thenReturn(Mono.just(template(10L)));
        StageTemplateController controller = new StageTemplateController(templates);

        StepVerifier.create(controller.create(authentication, 1L, request()))
                .assertNext(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED))
                .verifyComplete();
    }

    @Test
    void templateArchiveAnswersNoContent() {
        when(authentication.getName()).thenReturn(EMAIL);
        when(templates.archive(EMAIL, 1L, 10L)).thenReturn(Mono.just(template(10L)));
        StageTemplateController controller = new StageTemplateController(templates);

        StepVerifier.create(controller.archive(authentication, 1L, 10L)).verifyComplete();
        verify(templates).archive(EMAIL, 1L, 10L);
    }

    @Test
    void associationCreatesAndAnswersCreatedWithTheGeometryView() {
        when(authentication.getName()).thenReturn(EMAIL);
        when(snapshots.associate(EMAIL, 1L, 5L, 9L, 10L))
                .thenReturn(Mono.just(snapshot(77L)));
        ConcertStageController controller = new ConcertStageController(snapshots, geometry);

        StepVerifier.create(controller.associate(authentication, 1L, 5L, 9L,
                        new AssociateStageRequest(10L)))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                    ResponseEntity<StageDtos.StageGeometryResponse> entity = response;
                    assertThat(entity.getBody().snapshotId()).isEqualTo(77L);
                    assertThat(entity.getBody().version()).isEqualTo(1);
                })
                .verifyComplete();
    }

    @Test
    void geometryQueryReturnsTheStageGeometryContract() {
        when(authentication.getName()).thenReturn(EMAIL);
        when(geometry.geometry(EMAIL, 1L, 5L, 9L))
                .thenReturn(Mono.just(StageGeometry.from(snapshot(77L))));
        ConcertStageController controller = new ConcertStageController(snapshots, geometry);

        StepVerifier.create(controller.geometry(authentication, 1L, 5L, 9L))
                .assertNext(response -> assertThat(response.snapshotId()).isEqualTo(77L))
                .verifyComplete();
    }

    private static StageDtos.StageTemplateRequest request() {
        return new StageDtos.StageTemplateRequest(
                "Escenario",
                bd("800"),
                bd("600"),
                List.of(dto("0", "0"), dto("800", "0"), dto("800", "600")),
                List.of(),
                List.of());
    }

    private static StageTemplate template(long id) {
        return new StageTemplate(id, 1L, "Escenario", bd("800"), bd("600"),
                new StageBoundary(List.of(p("0", "0"), p("800", "0"), p("800", "600"))),
                List.of(), List.of(), false);
    }

    private static StageSnapshot snapshot(long id) {
        return StageWebMapperTests.snapshot(id);
    }

    private static PointDto dto(String x, String y) {
        return new PointDto(bd(x), bd(y));
    }

    private static StageBoundaryPoint p(String x, String y) {
        return new StageBoundaryPoint(bd(x), bd(y));
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}

package com.rubengarcia.choir.scene.adapter.in.web;

import com.rubengarcia.choir.scene.adapter.in.web.StageDtos.AssociateStageRequest;
import com.rubengarcia.choir.scene.adapter.in.web.StageDtos.StageGeometryResponse;
import com.rubengarcia.choir.scene.application.model.StageGeometry;
import com.rubengarcia.choir.scene.application.port.in.StageGeometryQueryUseCase;
import com.rubengarcia.choir.scene.application.port.in.StageSnapshotUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Asociación de un escenario a una inclusión concierto + obra y consulta de su geometría histórica.
 * La asociación crea un snapshot inmutable; una segunda asociación de la misma inclusión responde 409.
 */
@RestController
@Validated
@RequestMapping("/api/choirs/{choirId}/concerts/{concertId}/repertoire/{repertoireItemId}/stage")
public class ConcertStageController {

    private final StageSnapshotUseCase snapshots;
    private final StageGeometryQueryUseCase geometry;

    public ConcertStageController(StageSnapshotUseCase snapshots, StageGeometryQueryUseCase geometry) {
        this.snapshots = snapshots;
        this.geometry = geometry;
    }

    @PostMapping
    public Mono<ResponseEntity<StageGeometryResponse>> associate(
            Authentication authentication,
            @PathVariable @Positive long choirId,
            @PathVariable @Positive long concertId,
            @PathVariable @Positive long repertoireItemId,
            @Valid @RequestBody AssociateStageRequest request) {
        return snapshots.associate(authentication.getName(), choirId, concertId, repertoireItemId, request.stageId())
                .map(snapshot -> StageGeometry.from(snapshot))
                .map(view -> ResponseEntity.status(HttpStatus.CREATED).body(StageWebMapper.toResponse(view)));
    }

    @GetMapping
    public Mono<StageGeometryResponse> geometry(
            Authentication authentication,
            @PathVariable @Positive long choirId,
            @PathVariable @Positive long concertId,
            @PathVariable @Positive long repertoireItemId) {
        return geometry.geometry(authentication.getName(), choirId, concertId, repertoireItemId)
                .map(view -> StageWebMapper.toResponse(view));
    }
}

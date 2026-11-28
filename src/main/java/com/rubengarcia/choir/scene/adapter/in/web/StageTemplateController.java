package com.rubengarcia.choir.scene.adapter.in.web;

import com.rubengarcia.choir.scene.adapter.in.web.StageDtos.StageTemplateRequest;
import com.rubengarcia.choir.scene.adapter.in.web.StageDtos.StageTemplateResponse;
import com.rubengarcia.choir.scene.application.port.in.StageTemplateUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Gestión de plantillas de escenario de un coro. El director se obtiene de la autenticación. */
@RestController
@Validated
@RequestMapping("/api/choirs/{choirId}/stages")
public class StageTemplateController {

    private final StageTemplateUseCase templates;

    public StageTemplateController(StageTemplateUseCase templates) {
        this.templates = templates;
    }

    @GetMapping
    public Flux<StageTemplateResponse> list(
            Authentication authentication,
            @PathVariable @Positive long choirId) {
        return templates.list(authentication.getName(), choirId)
                .map(template -> StageWebMapper.toResponse(template));
    }

    @GetMapping("/{stageId}")
    public Mono<StageTemplateResponse> get(
            Authentication authentication,
            @PathVariable @Positive long choirId,
            @PathVariable @Positive long stageId) {
        return templates.get(authentication.getName(), choirId, stageId)
                .map(template -> StageWebMapper.toResponse(template));
    }

    @PostMapping
    public Mono<ResponseEntity<StageTemplateResponse>> create(
            Authentication authentication,
            @PathVariable @Positive long choirId,
            @Valid @RequestBody StageTemplateRequest request) {
        return templates.create(authentication.getName(), StageWebMapper.toTemplate(choirId, null, request))
                .map(template -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(StageWebMapper.toResponse(template)));
    }

    @PutMapping("/{stageId}")
    public Mono<StageTemplateResponse> update(
            Authentication authentication,
            @PathVariable @Positive long choirId,
            @PathVariable @Positive long stageId,
            @Valid @RequestBody StageTemplateRequest request) {
        return templates.update(authentication.getName(), choirId, stageId,
                        StageWebMapper.toTemplate(choirId, stageId, request))
                .map(template -> StageWebMapper.toResponse(template));
    }

    /** Archivado lógico: la plantilla y su historial se conservan. */
    @DeleteMapping("/{stageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> archive(
            Authentication authentication,
            @PathVariable @Positive long choirId,
            @PathVariable @Positive long stageId) {
        return templates.archive(authentication.getName(), choirId, stageId).then();
    }
}

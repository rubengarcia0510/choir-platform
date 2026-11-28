package com.rubengarcia.choir.scene.adapter.in.web;

import com.rubengarcia.choir.scene.domain.StageElementType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/** Contratos HTTP de escenarios. Las medidas están en centímetros. */
public final class StageDtos {

    private StageDtos() {
    }

    /** Vértice o punto de un acceso, en centímetros, con origen en la esquina delantera izquierda. */
    public record PointDto(@NotNull BigDecimal x, @NotNull BigDecimal y) {
    }

    public record AccessDto(
            @NotBlank @Size(max = 160) String name,
            @NotNull @Valid PointDto start,
            @NotNull @Valid PointDto end) {
    }

    /** Elemento físico como superficie rectangular. {@code elevationCm} es obligatoria: cero salvo escalones y plataformas. */
    public record ElementDto(
            @NotBlank @Size(max = 160) String name,
            @NotNull StageElementType type,
            @NotNull BigDecimal x,
            @NotNull BigDecimal y,
            @NotNull @Positive BigDecimal widthCm,
            @NotNull @Positive BigDecimal depthCm,
            @NotNull BigDecimal elevationCm) {
    }

    public record StageTemplateRequest(
            @NotBlank @Size(max = 160) String name,
            @NotNull @Positive BigDecimal widthCm,
            @NotNull @Positive BigDecimal depthCm,
            @NotNull @Size(min = 3) @Valid List<PointDto> boundary,
            @NotNull @Valid List<AccessDto> accesses,
            @NotNull @Valid List<ElementDto> elements) {
    }

    public record StageTemplateResponse(
            long id,
            long choirId,
            String name,
            BigDecimal widthCm,
            BigDecimal depthCm,
            boolean archived,
            List<PointDto> boundary,
            List<AccessDto> accesses,
            List<ElementDto> elements) {
    }

    public record AssociateStageRequest(@NotNull @Positive Long stageId) {
    }

    /** Vista de lectura de la geometría histórica de una inclusión. Es el contrato que consume CHOIR-25. */
    public record StageGeometryResponse(
            long snapshotId,
            int version,
            BigDecimal widthCm,
            BigDecimal depthCm,
            List<PointDto> contour,
            List<AccessDto> accesses,
            List<ElementDto> elements) {
    }
}

package com.rubengarcia.choir.scene.adapter.in.web;

import com.rubengarcia.choir.scene.adapter.in.web.StageDtos.AccessDto;
import com.rubengarcia.choir.scene.adapter.in.web.StageDtos.ElementDto;
import com.rubengarcia.choir.scene.adapter.in.web.StageDtos.PointDto;
import com.rubengarcia.choir.scene.adapter.in.web.StageDtos.StageGeometryResponse;
import com.rubengarcia.choir.scene.adapter.in.web.StageDtos.StageTemplateRequest;
import com.rubengarcia.choir.scene.adapter.in.web.StageDtos.StageTemplateResponse;
import com.rubengarcia.choir.scene.application.model.StageGeometry;
import com.rubengarcia.choir.scene.domain.StageAccess;
import com.rubengarcia.choir.scene.domain.StageBoundary;
import com.rubengarcia.choir.scene.domain.StageBoundaryPoint;
import com.rubengarcia.choir.scene.domain.StageElement;
import com.rubengarcia.choir.scene.domain.StageTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Traducción entre DTOs HTTP y modelos de dominio. La validación geométrica queda en el dominio. */
final class StageWebMapper {

    private StageWebMapper() {
    }

    static StageTemplate toTemplate(long choirId, Long stageId, StageTemplateRequest request) {
        return new StageTemplate(
                stageId,
                choirId,
                request.name(),
                request.widthCm(),
                request.depthCm(),
                new StageBoundary(list(request.boundary(), p -> point(p))),
                list(request.accesses(), a -> access(a)),
                list(request.elements(), e -> element(e)),
                false);
    }

    static StageTemplateResponse toResponse(StageTemplate template) {
        return new StageTemplateResponse(
                template.id(),
                template.choirId(),
                template.name(),
                template.widthCm(),
                template.depthCm(),
                template.archived(),
                template.boundary().vertices().stream().map(p -> point(p)).toList(),
                template.accesses().stream().map(a -> access(a)).toList(),
                template.elements().stream().map(e -> element(e)).toList());
    }

    static StageGeometryResponse toResponse(StageGeometry geometry) {
        return new StageGeometryResponse(
                geometry.snapshotId(),
                geometry.version(),
                geometry.widthCm(),
                geometry.depthCm(),
                geometry.boundary().vertices().stream().map(p -> point(p)).toList(),
                geometry.accesses().stream().map(a -> access(a)).toList(),
                geometry.elements().stream().map(e -> element(e)).toList());
    }

    private static StageBoundaryPoint point(PointDto dto) {
        return new StageBoundaryPoint(dto.x(), dto.y());
    }

    private static StageAccess access(AccessDto dto) {
        return new StageAccess(dto.name(), point(dto.start()), point(dto.end()));
    }

    private static StageElement element(ElementDto dto) {
        return new StageElement(dto.name(), dto.type(), dto.x(), dto.y(),
                dto.widthCm(), dto.depthCm(), dto.elevationCm());
    }

    private static PointDto point(StageBoundaryPoint point) {
        return new PointDto(point.x(), point.y());
    }

    private static AccessDto access(StageAccess access) {
        return new AccessDto(access.name(), point(access.start()), point(access.end()));
    }

    private static ElementDto element(StageElement element) {
        return new ElementDto(element.name(), element.type(), element.x(), element.y(),
                element.widthCm(), element.depthCm(), element.elevationCm());
    }

    private static <I, O> List<O> list(List<I> items, Function<I, O> mapper) {
        if (items == null) {
            throw new IllegalArgumentException("La lista de detalles es obligatoria");
        }
        List<O> mapped = new ArrayList<>(items.size());
        for (I item : items) {
            if (item == null) {
                throw new IllegalArgumentException("Los detalles no admiten elementos nulos");
            }
            mapped.add(mapper.apply(item));
        }
        return mapped;
    }
}

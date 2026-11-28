package com.rubengarcia.choir.scene.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rubengarcia.choir.scene.adapter.in.web.StageDtos.AccessDto;
import com.rubengarcia.choir.scene.adapter.in.web.StageDtos.ElementDto;
import com.rubengarcia.choir.scene.adapter.in.web.StageDtos.PointDto;
import com.rubengarcia.choir.scene.adapter.in.web.StageDtos.StageTemplateRequest;
import com.rubengarcia.choir.scene.application.model.StageGeometry;
import com.rubengarcia.choir.scene.domain.StageBoundary;
import com.rubengarcia.choir.scene.domain.StageElementType;
import com.rubengarcia.choir.scene.domain.StageSnapshot;
import com.rubengarcia.choir.scene.domain.StageTemplate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class StageWebMapperTests {

    @Test
    void requestMapsToTemplateWithPathIdentityAndOrderedDetails() {
        StageTemplate template = StageWebMapper.toTemplate(7L, 10L, rectangleRequest());

        assertThat(template.id()).isEqualTo(10L);
        assertThat(template.choirId()).isEqualTo(7L);
        assertThat(template.archived()).isFalse();
        assertThat(template.boundary().vertices()).hasSize(4);
        assertThat(template.accesses().get(0).name()).isEqualTo("Puerta");
        assertThat(template.elements().get(0).type()).isEqualTo(StageElementType.STEP);
        assertThat(template.elements().get(0).elevationCm()).isEqualByComparingTo("15.125");
    }

    @Test
    void nullDetailInRequestIsRejectedAsInvalidInput() {
        StageTemplateRequest request = new StageTemplateRequest("Escenario", bd("800"), bd("600"),
                Arrays.asList(point("0", "0"), null, point("800", "600")), List.of(), List.of());

        assertThatThrownBy(() -> StageWebMapper.toTemplate(7L, null, request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void geometryResponseCarriesSnapshotIdentityVersionAndElevations() {
        StageGeometry geometry = StageGeometry.from(snapshot(77L));

        StageDtos.StageGeometryResponse response = StageWebMapper.toResponse(geometry);

        assertThat(response.snapshotId()).isEqualTo(77L);
        assertThat(response.version()).isEqualTo(1);
        assertThat(response.contour()).hasSize(4);
        assertThat(response.elements().get(0).elevationCm()).isEqualByComparingTo("15.125");
    }

    private static StageTemplateRequest rectangleRequest() {
        return new StageTemplateRequest("Escenario", bd("800"), bd("600"),
                List.of(point("0", "0"), point("800", "0"), point("800", "600"), point("0", "600")),
                List.of(new AccessDto("Puerta", point("300", "0"), point("400", "0"))),
                List.of(new ElementDto("Escalón", StageElementType.STEP,
                        bd("100"), bd("100"), bd("200"), bd("30"), bd("15.125"))));
    }

    static StageSnapshot snapshot(Long id) {
        return new StageSnapshot(id, 7L, 2L, 3L, 10L, "Escenario", bd("800"), bd("600"),
                new StageBoundary(List.of(
                        new com.rubengarcia.choir.scene.domain.StageBoundaryPoint(bd("0"), bd("0")),
                        new com.rubengarcia.choir.scene.domain.StageBoundaryPoint(bd("800"), bd("0")),
                        new com.rubengarcia.choir.scene.domain.StageBoundaryPoint(bd("800"), bd("600")),
                        new com.rubengarcia.choir.scene.domain.StageBoundaryPoint(bd("0"), bd("600")))),
                List.of(),
                List.of(new com.rubengarcia.choir.scene.domain.StageElement("Escalón", StageElementType.STEP,
                        bd("100"), bd("100"), bd("200"), bd("30"), bd("15.125"))),
                LocalDateTime.of(2026, 11, 28, 10, 0));
    }

    private static PointDto point(String x, String y) {
        return new PointDto(bd(x), bd(y));
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}

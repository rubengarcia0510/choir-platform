package com.rubengarcia.choir.scene.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;

/** Integridad del esquema V42: FKs, CHECKs y secuencias de geometría. */
@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class StageSchemaIntegrityTests {

    private final JdbcTemplate jdbc;
    private final StagePersistenceFixture fixture;

    StageSchemaIntegrityTests(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.fixture = new StagePersistenceFixture(jdbc);
    }

    @AfterEach
    void cleanUp() {
        fixture.cleanup();
    }

    @Test
    void detailRowWithoutExistingTemplateIsRejected() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO stage_boundary_points "
                        + "(stage_template_id, choir_id, seq_no, x_cm, y_cm) VALUES (?, ?, 0, 0, 0)",
                999_999_001L, 999_999_002L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unknownElementTypeIsRejected() {
        long templateId = createTemplate("tipo");

        assertThatThrownBy(() -> insertElement(templateId, 0, "SOFA", "0"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void benchWithElevationIsRejected() {
        long templateId = createTemplate("banco");

        assertThatThrownBy(() -> insertElement(templateId, 0, "BENCH", "10"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void stepWithoutElevationIsRejected() {
        long templateId = createTemplate("escalon");

        assertThatThrownBy(() -> insertElement(templateId, 0, "STEP", "0"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicatePositionWithinATemplateIsRejected() {
        long templateId = createTemplate("posicion");
        insertElement(templateId, 0, "OBSTACLE", "0");

        assertThatThrownBy(() -> insertElement(templateId, 0, "OBSTACLE", "0"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void templateAccessWithIdenticalEndpointsIsRejected() {
        long templateId = createTemplate("acceso-degenerado");
        long choirId = choirIdForTemplate(templateId);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO stage_accesses "
                        + "(stage_template_id, choir_id, seq_no, name, "
                        + "start_x_cm, start_y_cm, end_x_cm, end_y_cm) "
                        + "VALUES (?, ?, 0, 'Acceso', 10, 10, 10, 10)",
                templateId, choirId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void templateAccessWithNegativeCoordinateIsRejected() {
        long templateId = createTemplate("acceso-negativo");
        long choirId = choirIdForTemplate(templateId);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO stage_accesses "
                        + "(stage_template_id, choir_id, seq_no, name, "
                        + "start_x_cm, start_y_cm, end_x_cm, end_y_cm) "
                        + "VALUES (?, ?, 0, 'Acceso', -1, 10, 20, 10)",
                templateId, choirId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void templateElementWithZeroWidthIsRejected() {
        long templateId = createTemplate("elemento-ancho-cero");
        long choirId = choirIdForTemplate(templateId);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO stage_elements "
                        + "(stage_template_id, choir_id, seq_no, name, element_type, "
                        + "x_cm, y_cm, width_cm, depth_cm, elevation_cm) "
                        + "VALUES (?, ?, 0, 'Elemento', 'OBSTACLE', 10, 10, 0, 20, 0)",
                templateId, choirId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void templateElementWithNegativeCoordinateIsRejected() {
        long templateId = createTemplate("elemento-negativo");
        long choirId = choirIdForTemplate(templateId);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO stage_elements "
                        + "(stage_template_id, choir_id, seq_no, name, element_type, "
                        + "x_cm, y_cm, width_cm, depth_cm, elevation_cm) "
                        + "VALUES (?, ?, 0, 'Elemento', 'OBSTACLE', -1, 10, 20, 20, 0)",
                templateId, choirId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void snapshotBoundaryPointWithNegativeCoordinateIsRejected() {
        StagePersistenceFixture.Context ctx = fixture.create("snapshot-contorno-negativo");
        long templateId = createTemplateFor(ctx.choirId());
        long snapshotId = createSnapshot(ctx, templateId);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO concert_stage_snapshot_boundary_points "
                        + "(snapshot_id, choir_id, seq_no, x_cm, y_cm) VALUES (?, ?, 0, -1, 0)",
                snapshotId, ctx.choirId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void snapshotAccessWithIdenticalEndpointsIsRejected() {
        StagePersistenceFixture.Context ctx = fixture.create("snapshot-acceso-degenerado");
        long templateId = createTemplateFor(ctx.choirId());
        long snapshotId = createSnapshot(ctx, templateId);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO concert_stage_snapshot_accesses "
                        + "(snapshot_id, choir_id, seq_no, name, "
                        + "start_x_cm, start_y_cm, end_x_cm, end_y_cm) "
                        + "VALUES (?, ?, 0, 'Acceso', 10, 10, 10, 10)",
                snapshotId, ctx.choirId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void snapshotAccessWithNegativeCoordinateIsRejected() {
        StagePersistenceFixture.Context ctx = fixture.create("snapshot-acceso-negativo");
        long templateId = createTemplateFor(ctx.choirId());
        long snapshotId = createSnapshot(ctx, templateId);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO concert_stage_snapshot_accesses "
                        + "(snapshot_id, choir_id, seq_no, name, "
                        + "start_x_cm, start_y_cm, end_x_cm, end_y_cm) "
                        + "VALUES (?, ?, 0, 'Acceso', 10, -1, 20, 10)",
                snapshotId, ctx.choirId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void snapshotElementWithZeroDepthIsRejected() {
        StagePersistenceFixture.Context ctx = fixture.create("snapshot-elemento-cero");
        long templateId = createTemplateFor(ctx.choirId());
        long snapshotId = createSnapshot(ctx, templateId);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO concert_stage_snapshot_elements "
                        + "(snapshot_id, choir_id, seq_no, name, element_type, "
                        + "x_cm, y_cm, width_cm, depth_cm, elevation_cm) "
                        + "VALUES (?, ?, 0, 'Elemento', 'OBSTACLE', 10, 10, 20, 0, 0)",
                snapshotId, ctx.choirId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void snapshotElementWithNegativeCoordinateIsRejected() {
        StagePersistenceFixture.Context ctx = fixture.create("snapshot-elemento-negativo");
        long templateId = createTemplateFor(ctx.choirId());
        long snapshotId = createSnapshot(ctx, templateId);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO concert_stage_snapshot_elements "
                        + "(snapshot_id, choir_id, seq_no, name, element_type, "
                        + "x_cm, y_cm, width_cm, depth_cm, elevation_cm) "
                        + "VALUES (?, ?, 0, 'Elemento', 'OBSTACLE', 10, -1, 20, 20, 0)",
                snapshotId, ctx.choirId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void discontinuousGeometrySequenceIsRejectedDuringReconstruction() {
        assertThatThrownBy(() -> StageGeometryRows.ordered(List.of(
                new StageGeometryRows.Seq<>(0, "primero"),
                new StageGeometryRows.Seq<>(2, "tercero"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Orden de detalle");
    }

    @Test
    void snapshotCannotReferenceATemplateOfAnotherChoir() {
        StagePersistenceFixture.Context mine = fixture.create("snap-mio");
        StagePersistenceFixture.Context other = fixture.create("snap-otro");
        Long foreignTemplate = createTemplateFor(other.choirId());

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO concert_stage_snapshots "
                        + "(choir_id, concert_id, repertoire_item_id, source_template_id, "
                        + "name, width_cm, depth_cm, created_at) "
                        + "VALUES (?, ?, ?, ?, 'X', 800, 600, CURRENT_TIMESTAMP)",
                mine.choirId(), mine.concertId(), mine.repertoireItemId(), foreignTemplate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void snapshotCannotReferenceAnInclusionThatDoesNotExist() {
        StagePersistenceFixture.Context ctx = fixture.create("snap-inclusion");
        Long template = createTemplateFor(ctx.choirId());
        String workTitle = "Obra sin inclusión " + UUID.randomUUID();

        jdbc.update(
                "INSERT INTO repertoire_items (choir_id, title) VALUES (?, ?)",
                ctx.choirId(), workTitle);

        Long otherWork = jdbc.queryForObject(
                "SELECT id FROM repertoire_items WHERE choir_id = ? AND title = ?",
                Long.class, ctx.choirId(), workTitle);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO concert_stage_snapshots "
                        + "(choir_id, concert_id, repertoire_item_id, source_template_id, "
                        + "name, width_cm, depth_cm, created_at) "
                        + "VALUES (?, ?, ?, ?, 'X', 800, 600, CURRENT_TIMESTAMP)",
                ctx.choirId(), ctx.concertId(), otherWork, template))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long createTemplate(String suffix) {
        return createTemplateFor(fixture.create(suffix).choirId());
    }

    private Long createTemplateFor(long choirId) {
        jdbc.update(
                "INSERT INTO stage_templates "
                        + "(choir_id, name, width_cm, depth_cm, archived, created_at, updated_at) "
                        + "VALUES (?, 'Escenario', 800, 600, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                choirId);

        return jdbc.queryForObject(
                "SELECT MAX(id) FROM stage_templates WHERE choir_id = ?",
                Long.class, choirId);
    }

    private long choirIdForTemplate(long templateId) {
        return jdbc.queryForObject(
                "SELECT choir_id FROM stage_templates WHERE id = ?",
                Long.class, templateId);
    }

    private long createSnapshot(StagePersistenceFixture.Context ctx, long templateId) {
        jdbc.update(
                "INSERT INTO concert_stage_snapshots "
                        + "(choir_id, concert_id, repertoire_item_id, source_template_id, "
                        + "name, width_cm, depth_cm, created_at) "
                        + "VALUES (?, ?, ?, ?, 'Snapshot', 800, 600, CURRENT_TIMESTAMP)",
                ctx.choirId(), ctx.concertId(), ctx.repertoireItemId(), templateId);

        return jdbc.queryForObject(
                "SELECT id FROM concert_stage_snapshots WHERE concert_id = ? AND repertoire_item_id = ?",
                Long.class, ctx.concertId(), ctx.repertoireItemId());
    }

    private void insertElement(long templateId, int seq, String type, String elevation) {
        long choirId = choirIdForTemplate(templateId);

        jdbc.update(
                "INSERT INTO stage_elements "
                        + "(stage_template_id, choir_id, seq_no, name, element_type, "
                        + "x_cm, y_cm, width_cm, depth_cm, elevation_cm) "
                        + "VALUES (?, ?, ?, 'E', ?, 0, 0, 100, 100, ?)",
                templateId, choirId, seq, type, new java.math.BigDecimal(elevation));
    }
}

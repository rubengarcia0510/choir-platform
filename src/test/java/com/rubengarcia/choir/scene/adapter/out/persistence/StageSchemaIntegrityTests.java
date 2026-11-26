package com.rubengarcia.choir.scene.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;

/** Integridad del esquema V42: FKs compuestas, CHECK de elevación y tipos, y claves primarias por posición. */
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
                "INSERT INTO stage_boundary_points (stage_template_id, choir_id, seq_no, x_cm, y_cm) VALUES (?, ?, 0, 0, 0)",
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
    void snapshotCannotReferenceATemplateOfAnotherChoir() {
        StagePersistenceFixture.Context mine = fixture.create("snap-mio");
        StagePersistenceFixture.Context other = fixture.create("snap-otro");
        Long foreignTemplate = createTemplateFor(other.choirId());

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO concert_stage_snapshots (choir_id, concert_id, repertoire_item_id, source_template_id,"
                        + " name, width_cm, depth_cm, created_at) VALUES (?, ?, ?, ?, 'X', 800, 600, CURRENT_TIMESTAMP)",
                mine.choirId(), mine.concertId(), mine.repertoireItemId(), foreignTemplate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void snapshotCannotReferenceAnInclusionThatDoesNotExist() {
        StagePersistenceFixture.Context ctx = fixture.create("snap-inclusion");
        Long template = createTemplateFor(ctx.choirId());
        String workTitle = "Obra sin inclusión " + UUID.randomUUID();
        jdbc.update("INSERT INTO repertoire_items (choir_id, title) VALUES (?, ?)", ctx.choirId(), workTitle);
        Long otherWork = jdbc.queryForObject(
                "SELECT id FROM repertoire_items WHERE choir_id = ? AND title = ?", Long.class, ctx.choirId(), workTitle);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO concert_stage_snapshots (choir_id, concert_id, repertoire_item_id, source_template_id,"
                        + " name, width_cm, depth_cm, created_at) VALUES (?, ?, ?, ?, 'X', 800, 600, CURRENT_TIMESTAMP)",
                ctx.choirId(), ctx.concertId(), otherWork, template))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long createTemplate(String suffix) {
        return createTemplateFor(fixture.create(suffix).choirId());
    }

    private Long createTemplateFor(long choirId) {
        jdbc.update("INSERT INTO stage_templates (choir_id, name, width_cm, depth_cm, archived, created_at, updated_at)"
                + " VALUES (?, 'Escenario', 800, 600, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", choirId);
        return jdbc.queryForObject(
                "SELECT MAX(id) FROM stage_templates WHERE choir_id = ?", Long.class, choirId);
    }

    private void insertElement(long templateId, int seq, String type, String elevation) {
        long choirId = jdbc.queryForObject(
                "SELECT choir_id FROM stage_templates WHERE id = ?", Long.class, templateId);
        jdbc.update("INSERT INTO stage_elements (stage_template_id, choir_id, seq_no, name, element_type,"
                        + " x_cm, y_cm, width_cm, depth_cm, elevation_cm) VALUES (?, ?, ?, 'E', ?, 0, 0, 100, 100, "
                        + elevation + ")",
                templateId, choirId, seq, type);
    }
}

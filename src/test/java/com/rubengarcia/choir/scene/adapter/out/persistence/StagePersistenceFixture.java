package com.rubengarcia.choir.scene.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.rubengarcia.choir.scene.domain.StageAccess;
import com.rubengarcia.choir.scene.domain.StageBoundary;
import com.rubengarcia.choir.scene.domain.StageBoundaryPoint;
import com.rubengarcia.choir.scene.domain.StageElement;
import com.rubengarcia.choir.scene.domain.StageElementType;
import com.rubengarcia.choir.scene.domain.StageTemplate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Fixture de integración: directores, coros, conciertos, obras e inclusiones, con limpieza al final. */
final class StagePersistenceFixture {

    record Context(long directorId, String email, long choirId, long concertId, long repertoireItemId) {
    }

    private final JdbcTemplate jdbc;
    private final List<Long> directorIds = new ArrayList<>();
    private final List<Long> choirIds = new ArrayList<>();

    StagePersistenceFixture(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Context create(String suffix) {
        String unique = suffix + "-" + UUID.randomUUID();
        String email = "stage-" + unique + "@example.test";
        jdbc.update("INSERT INTO directors (display_name, email) VALUES (?, ?)", "Director " + unique, email);
        Long directorId = jdbc.queryForObject("SELECT id FROM directors WHERE email = ?", Long.class, email);
        directorIds.add(directorId);

        String choirName = "Coro " + unique;
        jdbc.update("INSERT INTO choirs (director_id, name) VALUES (?, ?)", directorId, choirName);
        Long choirId = jdbc.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ? AND name = ?", Long.class, directorId, choirName);
        choirIds.add(choirId);

        String concertTitle = "Concierto " + unique;
        jdbc.update("INSERT INTO concerts (choir_id, title) VALUES (?, ?)", choirId, concertTitle);
        Long concertId = jdbc.queryForObject(
                "SELECT id FROM concerts WHERE choir_id = ? AND title = ?", Long.class, choirId, concertTitle);

        String workTitle = "Obra " + unique;
        jdbc.update("INSERT INTO repertoire_items (choir_id, title) VALUES (?, ?)", choirId, workTitle);
        Long repertoireItemId = jdbc.queryForObject(
                "SELECT id FROM repertoire_items WHERE choir_id = ? AND title = ?", Long.class, choirId, workTitle);

        jdbc.update("INSERT INTO concert_repertoire (concert_id, repertoire_item_id, order_index) VALUES (?, ?, 0)",
                concertId, repertoireItemId);
        return new Context(directorId, email, choirId, concertId, repertoireItemId);
    }

    void cleanup() {
        for (Long choirId : choirIds) {
            jdbc.update("DELETE FROM concert_stage_snapshot_elements WHERE choir_id = ?", choirId);
            jdbc.update("DELETE FROM concert_stage_snapshot_accesses WHERE choir_id = ?", choirId);
            jdbc.update("DELETE FROM concert_stage_snapshot_boundary_points WHERE choir_id = ?", choirId);
            jdbc.update("DELETE FROM concert_stage_snapshots WHERE choir_id = ?", choirId);
            jdbc.update("DELETE FROM stage_elements WHERE choir_id = ?", choirId);
            jdbc.update("DELETE FROM stage_accesses WHERE choir_id = ?", choirId);
            jdbc.update("DELETE FROM stage_boundary_points WHERE choir_id = ?", choirId);
            jdbc.update("DELETE FROM stage_templates WHERE choir_id = ?", choirId);
            jdbc.update("DELETE FROM concert_repertoire WHERE concert_id IN (SELECT id FROM concerts WHERE choir_id = ?)",
                    choirId);
            jdbc.update("DELETE FROM concerts WHERE choir_id = ?", choirId);
            jdbc.update("DELETE FROM repertoire_items WHERE choir_id = ?", choirId);
            jdbc.update("DELETE FROM choirs WHERE id = ?", choirId);
        }
        for (Long directorId : directorIds) {
            jdbc.update("DELETE FROM directors WHERE id = ?", directorId);
        }
        choirIds.clear();
        directorIds.clear();
    }

    /** Plantilla completa: rectángulo de 800x600 cm, un acceso y los seis tipos de elemento. */
    static StageTemplate fullTemplate(long choirId, String name) {
        return new StageTemplate(null, choirId, name, bd("800.000"), bd("600.000"),
                new StageBoundary(List.of(p("0", "0"), p("800", "0"), p("800", "600"), p("0", "600"))),
                List.of(new StageAccess("Puerta", p("300", "0"), p("400", "0"))),
                List.of(
                        element("Banco", StageElementType.BENCH, "100", "100", "200", "40", "0"),
                        element("Piano", StageElementType.PIANO, "400", "300", "120", "100", "0"),
                        element("Mueble", StageElementType.FURNITURE, "600", "100", "100", "60", "0"),
                        element("Pilar", StageElementType.OBSTACLE, "350", "450", "30", "30", "0"),
                        element("Escalón", StageElementType.STEP, "100", "500", "200", "30", "15.125"),
                        element("Plataforma", StageElementType.PLATFORM, "400", "520", "200", "60", "40.500")),
                false);
    }

    static StageBoundaryPoint p(String x, String y) {
        return new StageBoundaryPoint(bd(x), bd(y));
    }

    static StageElement element(String name, StageElementType type, String x, String y,
            String w, String d, String h) {
        return new StageElement(name, type, bd(x), bd(y), bd(w), bd(d), bd(h));
    }

    static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    /** Compara la geometría completa, en orden, con igualdad numérica de decimales. */
    static void assertSameGeometry(StageTemplate expected, StageTemplate actual) {
        assertThat(actual.name()).isEqualTo(expected.name());
        assertThat(actual.widthCm()).isEqualByComparingTo(expected.widthCm());
        assertThat(actual.depthCm()).isEqualByComparingTo(expected.depthCm());
        assertSameDetails(expected.boundary(), expected.accesses(), expected.elements(),
                actual.boundary(), actual.accesses(), actual.elements());
    }

    static void assertSameDetails(StageBoundary eb, List<StageAccess> ea, List<StageElement> ee,
            StageBoundary ab, List<StageAccess> aa, List<StageElement> ae) {
        assertThat(ab.vertices()).hasSameSizeAs(eb.vertices());
        for (int i = 0; i < eb.vertices().size(); i++) {
            assertThat(ab.vertices().get(i).sameAs(eb.vertices().get(i))).isTrue();
        }
        assertThat(aa).hasSameSizeAs(ea);
        for (int i = 0; i < ea.size(); i++) {
            StageAccess x = ea.get(i);
            StageAccess y = aa.get(i);
            assertThat(y.name()).isEqualTo(x.name());
            assertThat(y.start().sameAs(x.start())).isTrue();
            assertThat(y.end().sameAs(x.end())).isTrue();
        }
        assertThat(ae).hasSameSizeAs(ee);
        for (int i = 0; i < ee.size(); i++) {
            StageElement x = ee.get(i);
            StageElement y = ae.get(i);
            assertThat(y.name()).isEqualTo(x.name());
            assertThat(y.type()).isEqualTo(x.type());
            assertThat(y.x()).isEqualByComparingTo(x.x());
            assertThat(y.y()).isEqualByComparingTo(x.y());
            assertThat(y.widthCm()).isEqualByComparingTo(x.widthCm());
            assertThat(y.depthCm()).isEqualByComparingTo(x.depthCm());
            assertThat(y.elevationCm()).isEqualByComparingTo(x.elevationCm());
        }
    }
}

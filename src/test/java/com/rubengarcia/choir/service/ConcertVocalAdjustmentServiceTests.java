package com.rubengarcia.choir.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ConcertVocalAdjustmentServiceTests {

    private final JdbcTemplate jdbc;
    private final ConcertVocalAdjustmentService service;

    ConcertVocalAdjustmentServiceTests(
            JdbcTemplate jdbc,
            ConcertVocalAdjustmentService service) {
        this.jdbc = jdbc;
        this.service = service;
    }

    @Test
    void concertOverrideTakesPrecedenceWithoutChangingBasePlan() {
        Fixture f = createFixture();
        int originalDivisions = count(
                "SELECT COUNT(*) FROM vocal_plan_divisions WHERE vocal_plan_id = ?",
                f.planId());

        var first = service.assignSinger(
                f.email(), f.choirId(), f.concertId(), f.workId(),
                f.singerId(), f.firstDivisionId()).block();

        var override = service.assignSinger(
                f.email(), f.choirId(), f.concertId(), f.workId(),
                f.singerId(), f.secondDivisionId()).block();

        assertThat(first).isNotNull();
        assertThat(override).isNotNull();
        assertThat(override.vocalPlanDivisionId()).isEqualTo(f.secondDivisionId());
        assertThat(count("""
                SELECT COUNT(*) FROM concert_vocal_assignments
                WHERE concert_id = ? AND repertoire_item_id = ? AND singer_id = ?
                """, f.concertId(), f.workId(), f.singerId())).isEqualTo(1);
        assertThat(count(
                "SELECT COUNT(*) FROM vocal_plan_divisions WHERE vocal_plan_id = ?",
                f.planId())).isEqualTo(originalDivisions);
    }

    @Test
    void absenceWarningsAreStableAndDoNotRewriteAssignments() {
        Fixture f = createFixture();
        service.assignSinger(
                f.email(), f.choirId(), f.concertId(), f.workId(),
                f.singerId(), f.firstDivisionId()).block();

        jdbc.update("""
                INSERT INTO absences
                    (singer_id, concert_id, start_date, end_date, status)
                VALUES (?, ?, ?, ?, 'CONFIRMED')
                """, f.singerId(), f.concertId(),
                LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"));

        List<ConcertVocalAdjustmentService.Warning> first =
                service.warnings(f.email(), f.choirId(), f.concertId()).block();
        List<ConcertVocalAdjustmentService.Warning> second =
                service.warnings(f.email(), f.choirId(), f.concertId()).block();

        assertThat(first).isNotNull();
        assertThat(first).contains(new ConcertVocalAdjustmentService.Warning(
                "ASSIGNED_SINGER_ABSENT", f.workId(), f.singerId(), null,
                "El cantante asignado " + f.singerName()
                        + " tiene una ausencia registrada para el concierto."));
        assertThat(second).containsExactlyElementsOf(first);
        assertThat(count("""
                SELECT COUNT(*) FROM concert_vocal_assignments
                WHERE concert_id = ? AND repertoire_item_id = ? AND singer_id = ?
                """, f.concertId(), f.workId(), f.singerId())).isEqualTo(1);
    }

    @Test
    void directorCannotApplyOverrideToAnotherChoirConcert() {
        Fixture own = createFixture();
        Fixture other = createFixture();

        assertThatThrownBy(() -> service.assignSinger(
                own.email(), own.choirId(), other.concertId(),
                other.workId(), other.singerId(), other.firstDivisionId()).block())
                .isInstanceOf(ResponseStatusException.class);
    }

    private Fixture createFixture() {
        String suffix = UUID.randomUUID().toString();
        String email = suffix + "@example.test";

        jdbc.update("INSERT INTO directors (display_name, email) VALUES (?, ?)",
                "Director " + suffix, email);
        long directorId = scalar("SELECT id FROM directors WHERE email = ?", email);

        String choirName = "Coro " + suffix;
        jdbc.update("INSERT INTO choirs (director_id, name) VALUES (?, ?)",
                directorId, choirName);
        long choirId = scalar(
                "SELECT id FROM choirs WHERE director_id = ? AND name = ?",
                directorId, choirName);

        String singerName = "Coreuta " + suffix;
        jdbc.update("""
                INSERT INTO singers (choir_id, display_name, active)
                VALUES (?, ?, TRUE)
                """, choirId, singerName);
        long singerId = scalar(
                "SELECT id FROM singers WHERE choir_id = ? AND display_name = ?",
                choirId, singerName);

        String workTitle = "Obra " + suffix;
        jdbc.update("INSERT INTO repertoire_items (choir_id, title) VALUES (?, ?)",
                choirId, workTitle);
        long workId = scalar(
                "SELECT id FROM repertoire_items WHERE choir_id = ? AND title = ?",
                choirId, workTitle);

        String concertTitle = "Concierto " + suffix;
        jdbc.update("""
                INSERT INTO concerts (choir_id, title, concert_date)
                VALUES (?, ?, ?)
                """, choirId, concertTitle, LocalDate.parse("2026-10-17"));
        long concertId = scalar(
                "SELECT id FROM concerts WHERE choir_id = ? AND title = ?",
                choirId, concertTitle);

        jdbc.update("""
                INSERT INTO concert_repertoire (concert_id, repertoire_item_id, order_index)
                VALUES (?, ?, 0)
                """, concertId, workId);

        jdbc.update("""
                INSERT INTO vocal_plans (repertoire_item_id, choir_id)
                VALUES (?, ?)
                """, workId, choirId);
        long planId = scalar("""
                SELECT id FROM vocal_plans
                WHERE repertoire_item_id = ? AND choir_id = ?
                """, workId, choirId);

        long sectionA = createSection(choirId, "Cuerda A " + suffix);
        long sectionB = createSection(choirId, "Cuerda B " + suffix);
        createDivision(planId, choirId, sectionA, 1);
        createDivision(planId, choirId, sectionB, 1);

        long divisionA = scalar("""
                SELECT id FROM vocal_plan_divisions
                WHERE vocal_plan_id = ? AND vocal_section_id = ?
                """, planId, sectionA);
        long divisionB = scalar("""
                SELECT id FROM vocal_plan_divisions
                WHERE vocal_plan_id = ? AND vocal_section_id = ?
                """, planId, sectionB);

        return new Fixture(email, choirId, concertId, workId, singerId,
                singerName, planId, divisionA, divisionB);
    }

    private long createSection(long choirId, String name) {
        jdbc.update("INSERT INTO vocal_sections (choir_id, name) VALUES (?, ?)",
                choirId, name);
        return scalar(
                "SELECT id FROM vocal_sections WHERE choir_id = ? AND name = ?",
                choirId, name);
    }

    private void createDivision(
            long planId, long choirId, long sectionId, int number) {
        jdbc.update("""
                INSERT INTO vocal_plan_divisions
                    (vocal_plan_id, choir_id, vocal_section_id, division_number)
                VALUES (?, ?, ?, ?)
                """, planId, choirId, sectionId, number);
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private long scalar(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        if (value == null) {
            throw new IllegalStateException("No row returned for SQL: " + sql);
        }
        return value;
    }

    private record Fixture(
            String email,
            long choirId,
            long concertId,
            long workId,
            long singerId,
            String singerName,
            long planId,
            long firstDivisionId,
            long secondDivisionId) {}
}

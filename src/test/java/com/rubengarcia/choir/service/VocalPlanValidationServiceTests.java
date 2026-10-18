package com.rubengarcia.choir.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class VocalPlanValidationServiceTests {

    private final JdbcTemplate jdbc;
    private final VocalPlanValidationService service;

    VocalPlanValidationServiceTests(
            JdbcTemplate jdbc,
            VocalPlanValidationService service) {
        this.jdbc = jdbc;
        this.service = service;
    }

    @Test
    void explicitValidationAcceptsOneDivisionWhenPopulationIsEnough() {
        Fixture f = fixture(1, 1);

        var report = service.validate(f.email(), f.choirId(), f.workId()).block();

        assertThat(report).isNotNull();
        assertThat(report.reviewRequired()).isFalse();
        assertThat(report.sections()).singleElement().satisfies(section -> {
            assertThat(section.activeSingerCount()).isEqualTo(1);
            assertThat(section.divisionCount()).isEqualTo(1);
            assertThat(section.contiguousNumbering()).isTrue();
            assertThat(section.enoughSingers()).isTrue();
        });
    }

    @Test
    void concertAbsenceDoesNotChangeValidatedVocalPopulation() {
        Fixture f = fixture(1, 1);

        var initial = service.validate(f.email(), f.choirId(), f.workId()).block();

        assertThat(initial).isNotNull();
        assertThat(initial.reviewRequired()).isFalse();
        assertThat(initial.sections()).singleElement()
                .satisfies(section ->
                        assertThat(section.activeSingerCount()).isEqualTo(1));

        String validatedSignature = initial.populationSignature();
        String concertTitle = "Concierto ausencia " + UUID.randomUUID();

        jdbc.update("""
                INSERT INTO concerts (choir_id, title, concert_date)
                VALUES (?, ?, CURRENT_DATE)
                """, f.choirId(), concertTitle);

        Long concertId = jdbc.queryForObject("""
                SELECT id FROM concerts
                WHERE choir_id = ? AND title = ?
                """, Long.class, f.choirId(), concertTitle);

        assertThat(concertId).isNotNull();

        jdbc.update("""
                INSERT INTO absences
                    (singer_id, concert_id, start_date, end_date, reason, status)
                VALUES (?, ?, CURRENT_DATE, CURRENT_DATE, ?, 'CONFIRMED')
                """, f.singerId(), concertId, "Ausencia para probar CHOIR-63");

        var afterAbsence = service.inspect(
                f.email(), f.choirId(), f.workId()).block();

        assertThat(afterAbsence).isNotNull();
        assertThat(afterAbsence.reviewRequired()).isFalse();
        assertThat(afterAbsence.populationSignature())
                .isEqualTo(validatedSignature);
        assertThat(afterAbsence.sections()).singleElement().satisfies(section -> {
            assertThat(section.activeSingerCount()).isEqualTo(1);
            assertThat(section.enoughSingers()).isTrue();
        });
    }

    @Test
    void populationBelowDivisionCountRequiresReviewWithoutDeletingDivisions() {
        Fixture f = fixture(2, 1);

        Long divisionId = jdbc.queryForObject("""
                SELECT id FROM vocal_plan_divisions
                WHERE vocal_plan_id = ? AND division_number = 1
                """, Long.class, f.planId());

        jdbc.update("""
                INSERT INTO work_vocal_assignments
                    (repertoire_item_id, singer_id, vocal_plan_id,
                     vocal_plan_division_id, choir_id)
                VALUES (?, ?, ?, ?, ?)
                """, f.workId(), f.singerId(), f.planId(),
                divisionId, f.choirId());

        var report = service.inspect(f.email(), f.choirId(), f.workId()).block();

        assertThat(report).isNotNull();
        assertThat(report.reviewRequired()).isTrue();
        assertThat(report.reviewReason()).contains("N=1").contains("k=2");
        assertThat(count(
                "SELECT COUNT(*) FROM vocal_plan_divisions WHERE vocal_plan_id = ?",
                f.planId())).isEqualTo(2);
        assertThat(count("""
                SELECT COUNT(*) FROM work_vocal_assignments
                WHERE repertoire_item_id = ? AND singer_id = ?
                """, f.workId(), f.singerId())).isEqualTo(1);
    }

    @Test
    void numberingGapRequiresReviewAndDoesNotRenumberDivisions() {
        Fixture f = fixtureWithNumbers(1, 1, 3);

        var report = service.inspect(f.email(), f.choirId(), f.workId()).block();

        assertThat(report).isNotNull();
        assertThat(report.reviewRequired()).isTrue();
        assertThat(report.sections()).singleElement()
                .satisfies(section ->
                        assertThat(section.contiguousNumbering()).isFalse());

        assertThat(jdbc.queryForList("""
                SELECT division_number FROM vocal_plan_divisions
                WHERE vocal_plan_id = ? ORDER BY division_number
                """, Integer.class, f.planId())).containsExactly(1, 3);
    }

    @Test
    void populationChangeAfterValidationRequiresReviewWithoutRenumbering() {
        Fixture f = fixture(1, 1);

        var initial = service.validate(f.email(), f.choirId(), f.workId()).block();

        assertThat(initial).isNotNull();
        assertThat(initial.reviewRequired()).isFalse();

        jdbc.update(
                "UPDATE singers SET active = FALSE WHERE id = ?",
                f.singerId());

        var changed = service.inspect(f.email(), f.choirId(), f.workId()).block();

        assertThat(changed).isNotNull();
        assertThat(changed.reviewRequired()).isTrue();
        assertThat(changed.reviewReason())
                .contains("Cambió la población activa");
        assertThat(jdbc.queryForObject("""
                SELECT division_number FROM vocal_plan_divisions
                WHERE vocal_plan_id = ?
                """, Integer.class, f.planId())).isEqualTo(1);
    }

    @Test
    void directorCannotValidateAnotherDirectorsWork() {
        Fixture own = fixture(1, 1);
        Fixture other = fixture(1, 1);

        assertThatThrownBy(() -> service.inspect(
                own.email(), other.choirId(), other.workId()).block())
                .isInstanceOf(ResponseStatusException.class);
    }

    private Fixture fixture(int divisionCount, int singerCount) {
        int[] numbers = new int[divisionCount];

        for (int i = 0; i < divisionCount; i++) {
            numbers[i] = i + 1;
        }

        return fixtureWithNumbers(singerCount, numbers);
    }

    private Fixture fixtureWithNumbers(int singerCount, int... divisionNumbers) {
        String suffix = UUID.randomUUID().toString();
        String email = suffix + "@example.test";

        jdbc.update(
                "INSERT INTO directors (display_name, email) VALUES (?, ?)",
                "Director " + suffix, email);

        long directorId = scalar(
                "SELECT id FROM directors WHERE email = ?", email);

        jdbc.update(
                "INSERT INTO choirs (director_id, name) VALUES (?, ?)",
                directorId, "Coro " + suffix);

        long choirId = scalar(
                "SELECT id FROM choirs WHERE director_id = ?", directorId);

        jdbc.update(
                "INSERT INTO vocal_sections (choir_id, name) VALUES (?, ?)",
                choirId, "Cuerda " + suffix);

        long sectionId = scalar("""
                SELECT id FROM vocal_sections WHERE choir_id = ? AND name = ?
                """, choirId, "Cuerda " + suffix);

        for (int i = 0; i < singerCount; i++) {
            jdbc.update("""
                    INSERT INTO singers
                        (choir_id, display_name, voice_part, active, voice_section_id)
                    VALUES (?, ?, ?, TRUE, ?)
                    """, choirId, "Cantante " + i + " " + suffix,
                    "TENOR", sectionId);
        }

        jdbc.update(
                "INSERT INTO repertoire_items (choir_id, title) VALUES (?, ?)",
                choirId, "Obra " + suffix);

        long workId = scalar("""
                SELECT id FROM repertoire_items WHERE choir_id = ? AND title = ?
                """, choirId, "Obra " + suffix);

        jdbc.update("""
                INSERT INTO vocal_plans (repertoire_item_id, choir_id)
                VALUES (?, ?)
                """, workId, choirId);

        long planId = scalar("""
                SELECT id FROM vocal_plans
                WHERE repertoire_item_id = ? AND choir_id = ?
                """, workId, choirId);

        for (int number : divisionNumbers) {
            jdbc.update("""
                    INSERT INTO vocal_plan_divisions
                        (vocal_plan_id, choir_id, vocal_section_id, division_number)
                    VALUES (?, ?, ?, ?)
                    """, planId, choirId, sectionId, number);
        }

        long singerId = scalar("""
                SELECT id FROM singers
                WHERE choir_id = ? ORDER BY id LIMIT 1
                """, choirId);

        return new Fixture(email, choirId, workId, planId, singerId);
    }

    private int count(String sql, Object... args) {
        Integer result = jdbc.queryForObject(sql, Integer.class, args);
        return result == null ? 0 : result;
    }

    private long scalar(String sql, Object... args) {
        Long result = jdbc.queryForObject(sql, Long.class, args);

        if (result == null) {
            throw new IllegalStateException("No result for SQL: " + sql);
        }

        return result;
    }

    private record Fixture(
            String email,
            long choirId,
            long workId,
            long planId,
            long singerId) {}
}

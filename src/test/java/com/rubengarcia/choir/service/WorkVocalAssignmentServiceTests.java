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
class WorkVocalAssignmentServiceTests {

    private final JdbcTemplate jdbc;
    private final WorkVocalAssignmentService service;

    WorkVocalAssignmentServiceTests(
            JdbcTemplate jdbc,
            WorkVocalAssignmentService service) {
        this.jdbc = jdbc;
        this.service = service;
    }

    @Test
    void createsAndChangesAssignmentWithoutDuplicatingIt() {
        Fixture f = createFixture();

        var first = service.assign(
                f.email(), f.choirId(), f.workId(),
                f.singerId(), f.firstDivisionId()).block();

        var changed = service.assign(
                f.email(), f.choirId(), f.workId(),
                f.singerId(), f.secondDivisionId()).block();

        assertThat(first).isNotNull();
        assertThat(changed).isNotNull();
        assertThat(changed.vocalPlanDivisionId()).isEqualTo(f.secondDivisionId());
        assertThat(count("""
                SELECT COUNT(*) FROM work_vocal_assignments
                WHERE repertoire_item_id = ? AND singer_id = ?
                """, f.workId(), f.singerId())).isEqualTo(1);
    }

    @Test
    void assignmentForOneWorkDoesNotChangeAnotherWork() {
        Fixture f = createFixture();

        service.assign(f.email(), f.choirId(), f.workId(),
                f.singerId(), f.firstDivisionId()).block();

        service.assign(f.email(), f.choirId(), f.secondWorkId(),
                f.singerId(), f.secondWorkDivisionId()).block();

        assertThat(divisionFor(f.workId(), f.singerId()))
                .isEqualTo(f.firstDivisionId());
        assertThat(divisionFor(f.secondWorkId(), f.singerId()))
                .isEqualTo(f.secondWorkDivisionId());
        assertThat(count("""
                SELECT COUNT(*) FROM work_vocal_assignments
                WHERE singer_id = ?
                """, f.singerId())).isEqualTo(2);
    }

    @Test
    void rejectsSingerFromAnotherChoir() {
        Fixture own = createFixture();
        Fixture other = createFixture();

        assertThatThrownBy(() -> service.assign(
                own.email(), own.choirId(), own.workId(),
                other.singerId(), own.firstDivisionId()).block())
                .isInstanceOf(ResponseStatusException.class);

        assertThat(count("""
                SELECT COUNT(*) FROM work_vocal_assignments
                WHERE repertoire_item_id = ? AND singer_id = ?
                """, own.workId(), other.singerId())).isZero();
    }

    @Test
    void rejectsDivisionFromAnotherWorkEvenInTheSameChoir() {
        Fixture f = createFixture();

        assertThatThrownBy(() -> service.assign(
                f.email(), f.choirId(), f.workId(),
                f.singerId(), f.secondWorkDivisionId()).block())
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void directorCannotAssignSingerInAnotherDirectorsChoir() {
        Fixture own = createFixture();
        Fixture other = createFixture();

        assertThatThrownBy(() -> service.assign(
                own.email(), other.choirId(), other.workId(),
                other.singerId(), other.firstDivisionId()).block())
                .isInstanceOf(ResponseStatusException.class);
    }

    private Fixture createFixture() {
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
                "SELECT id FROM choirs WHERE director_id = ?",
                directorId);

        jdbc.update("""
                INSERT INTO singers (choir_id, display_name, active)
                VALUES (?, ?, TRUE)
                """, choirId, "Cantante " + suffix);

        long singerId = scalar(
                "SELECT id FROM singers WHERE choir_id = ? AND display_name = ?",
                choirId, "Cantante " + suffix);

        long workId = createWork(choirId, suffix, "Principal");
        long secondWorkId = createWork(choirId, suffix, "Secundaria");

        long firstDivisionId = createDivision(
                choirId, workId, suffix, "Primera", 1);
        long secondDivisionId = createDivision(
                choirId, workId, suffix, "Segunda", 2);
        long secondWorkDivisionId = createDivision(
                choirId, secondWorkId, suffix, "Otra obra", 1);

        return new Fixture(email, choirId, singerId, workId,
                secondWorkId, firstDivisionId, secondDivisionId,
                secondWorkDivisionId);
    }

    private long createWork(long choirId, String suffix, String label) {
        String title = label + " " + suffix;
        jdbc.update(
                "INSERT INTO repertoire_items (choir_id, title) VALUES (?, ?)",
                choirId, title);

        long workId = scalar(
                "SELECT id FROM repertoire_items WHERE choir_id = ? AND title = ?",
                choirId, title);

        jdbc.update("""
                INSERT INTO vocal_plans (repertoire_item_id, choir_id)
                VALUES (?, ?)
                """, workId, choirId);

        return workId;
    }

    private long createDivision(
            long choirId, long workId, String suffix,
            String label, int number) {
        long planId = scalar("""
                SELECT id FROM vocal_plans
                WHERE repertoire_item_id = ? AND choir_id = ?
                """, workId, choirId);

        String sectionName = label + " " + suffix;
        jdbc.update(
                "INSERT INTO vocal_sections (choir_id, name) VALUES (?, ?)",
                choirId, sectionName);

        long sectionId = scalar(
                "SELECT id FROM vocal_sections WHERE choir_id = ? AND name = ?",
                choirId, sectionName);

        jdbc.update("""
                INSERT INTO vocal_plan_divisions
                    (vocal_plan_id, choir_id, vocal_section_id, division_number)
                VALUES (?, ?, ?, ?)
                """, planId, choirId, sectionId, number);

        return scalar("""
                SELECT id FROM vocal_plan_divisions
                WHERE vocal_plan_id = ? AND vocal_section_id = ?
                """, planId, sectionId);
    }

    private long divisionFor(long workId, long singerId) {
        return scalar("""
                SELECT vocal_plan_division_id
                FROM work_vocal_assignments
                WHERE repertoire_item_id = ? AND singer_id = ?
                """, workId, singerId);
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
            long singerId,
            long workId,
            long secondWorkId,
            long firstDivisionId,
            long secondDivisionId,
            long secondWorkDivisionId) {}
}

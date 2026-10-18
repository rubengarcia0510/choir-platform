package com.rubengarcia.choir;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class VocalPlanIntegrityConstraintTests {

    private final JdbcTemplate jdbcTemplate;

    VocalPlanIntegrityConstraintTests(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void migrationCreatesVocalPlanTables() {
        assertThat(tableExists("vocal_plans")).isTrue();
        assertThat(tableExists("vocal_plan_divisions")).isTrue();
    }

    @Test
    void planCanBePersistedForAWorkInTheSameChoir() {
        ChoirFixture choir = createChoir();
        long workId = createWork(choir.choirId());

        createVocalPlan(workId, choir.choirId());

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM vocal_plans
                WHERE repertoire_item_id = ? AND choir_id = ?
                """, Integer.class, workId, choir.choirId())).isEqualTo(1);
    }

    @Test
    void workCanHaveOnlyOneVocalPlan() {
        ChoirFixture choir = createChoir();
        long workId = createWork(choir.choirId());
        createVocalPlan(workId, choir.choirId());

        assertThatThrownBy(() -> createVocalPlan(workId, choir.choirId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void divisionRequiresPositiveNumberAndUniquePlanSectionNumber() {
        ChoirFixture choir = createChoir();
        long workId = createWork(choir.choirId());
        long planId = createVocalPlan(workId, choir.choirId());
        long sectionId = createSection(choir.choirId());

        assertThatThrownBy(() -> createDivision(
                planId, choir.choirId(), sectionId, 0))
                .isInstanceOf(DataIntegrityViolationException.class);

        createDivision(planId, choir.choirId(), sectionId, 1);

        assertThatThrownBy(() -> createDivision(
                planId, choir.choirId(), sectionId, 1))
                .isInstanceOf(DataIntegrityViolationException.class);

        createDivision(planId, choir.choirId(), sectionId, 2);

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM vocal_plan_divisions
                WHERE vocal_plan_id = ? AND vocal_section_id = ?
                """, Integer.class, planId, sectionId)).isEqualTo(2);
    }

    @Test
    void planCannotReferenceAWorkOwnedByAnotherChoir() {
        ChoirFixture first = createChoir();
        ChoirFixture second = createChoir();
        long workId = createWork(first.choirId());

        assertThatThrownBy(() -> createVocalPlan(workId, second.choirId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void divisionCannotReferenceASectionOwnedByAnotherChoir() {
        ChoirFixture first = createChoir();
        ChoirFixture second = createChoir();
        long workId = createWork(first.choirId());
        long planId = createVocalPlan(workId, first.choirId());
        long sectionId = createSection(second.choirId());

        assertThatThrownBy(() -> createDivision(
                planId, first.choirId(), sectionId, 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void divisionCannotClaimAChoirDifferentFromItsPlan() {
        ChoirFixture first = createChoir();
        ChoirFixture second = createChoir();
        long workId = createWork(first.choirId());
        long planId = createVocalPlan(workId, first.choirId());
        long sectionId = createSection(second.choirId());

        assertThatThrownBy(() -> createDivision(
                planId, second.choirId(), sectionId, 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE LOWER(table_name) = ?
                """, Integer.class, tableName);
        return count != null && count > 0;
    }

    private ChoirFixture createChoir() {
        String suffix = UUID.randomUUID().toString();
        String email = suffix + "@example.test";
        String name = "Coro " + suffix;

        jdbcTemplate.update("""
                INSERT INTO directors (display_name, email)
                VALUES ('Director de prueba', ?)
                """, email);

        long directorId = jdbcTemplate.queryForObject(
                "SELECT id FROM directors WHERE email = ?", Long.class, email);

        jdbcTemplate.update("""
                INSERT INTO choirs (director_id, name) VALUES (?, ?)
                """, directorId, name);

        long choirId = jdbcTemplate.queryForObject("""
                SELECT id FROM choirs WHERE director_id = ? AND name = ?
                """, Long.class, directorId, name);

        return new ChoirFixture(directorId, choirId);
    }

    private long createWork(long choirId) {
        String title = "Obra " + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO repertoire_items (choir_id, title) VALUES (?, ?)
                """, choirId, title);

        return jdbcTemplate.queryForObject("""
                SELECT id FROM repertoire_items WHERE choir_id = ? AND title = ?
                """, Long.class, choirId, title);
    }

    private long createSection(long choirId) {
        String name = "Cuerda " + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO vocal_sections (choir_id, name) VALUES (?, ?)
                """, choirId, name);

        return jdbcTemplate.queryForObject("""
                SELECT id FROM vocal_sections WHERE choir_id = ? AND name = ?
                """, Long.class, choirId, name);
    }

    private long createVocalPlan(long workId, long choirId) {
        jdbcTemplate.update("""
                INSERT INTO vocal_plans (repertoire_item_id, choir_id)
                VALUES (?, ?)
                """, workId, choirId);

        return jdbcTemplate.queryForObject("""
                SELECT id FROM vocal_plans
                WHERE repertoire_item_id = ? AND choir_id = ?
                """, Long.class, workId, choirId);
    }

    private void createDivision(
            long planId, long choirId, long sectionId, int number) {
        jdbcTemplate.update("""
                INSERT INTO vocal_plan_divisions
                    (vocal_plan_id, choir_id, vocal_section_id, division_number)
                VALUES (?, ?, ?, ?)
                """, planId, choirId, sectionId, number);
    }

    private record ChoirFixture(long directorId, long choirId) {}
}

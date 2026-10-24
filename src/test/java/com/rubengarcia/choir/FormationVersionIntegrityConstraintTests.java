package com.rubengarcia.choir;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class FormationVersionIntegrityConstraintTests {

    private static final long MISSING_ID = -999999L;

    private final JdbcTemplate jdbcTemplate;

    FormationVersionIntegrityConstraintTests(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void rejectsDuplicateVersionNumbersForTheSameFormation() {
        long choirId = createChoir();
        long formationId = createFormation(choirId);
        long existingVersionId = createFormationVersion(formationId, 1);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO formation_versions (formation_id, version_number, status)
                VALUES (?, 1, 'DRAFT')
                """, formationId))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM formation_versions
                WHERE formation_id = ? AND version_number = 1
                """, Integer.class, formationId))
                .isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject("""
                SELECT id FROM formation_versions
                WHERE formation_id = ? AND version_number = 1
                """, Long.class, formationId))
                .isEqualTo(existingVersionId);
    }

    @Test
    void rejectsFormationVersionsReferencingMissingFormations() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO formation_versions (formation_id, version_number, status)
                VALUES (?, 1, 'DRAFT')
                """, MISSING_ID))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM formation_versions
                WHERE formation_id = ?
                """, Integer.class, MISSING_ID))
                .isZero();
    }

    private long createChoir() {
        String suffix = UUID.randomUUID().toString();
        String email = suffix + "@example.test";
        String name = "Coro " + suffix;

        jdbcTemplate.update("""
                INSERT INTO directors (display_name, email)
                VALUES ('Director de prueba', ?)
                """, email);

        long directorId = jdbcTemplate.queryForObject(
                "SELECT id FROM directors WHERE email = ?",
                Long.class, email);

        jdbcTemplate.update("""
                INSERT INTO choirs (director_id, name)
                VALUES (?, ?)
                """, directorId, name);

        return jdbcTemplate.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ? AND name = ?",
                Long.class, directorId, name);
    }

    private long createFormation(long choirId) {
        String name = "Formación " + UUID.randomUUID();

        jdbcTemplate.update("""
                INSERT INTO formations (choir_id, name)
                VALUES (?, ?)
                """, choirId, name);

        return jdbcTemplate.queryForObject(
                "SELECT id FROM formations WHERE choir_id = ? AND name = ?",
                Long.class, choirId, name);
    }

    private long createFormationVersion(long formationId, int number) {
        jdbcTemplate.update("""
                INSERT INTO formation_versions (formation_id, version_number, status)
                VALUES (?, ?, 'DRAFT')
                """, formationId, number);

        return jdbcTemplate.queryForObject("""
                SELECT id FROM formation_versions
                WHERE formation_id = ? AND version_number = ?
                """, Long.class, formationId, number);
    }
}

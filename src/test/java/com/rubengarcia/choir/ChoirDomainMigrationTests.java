package com.rubengarcia.choir;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ChoirDomainMigrationTests {

    private final JdbcTemplate jdbcTemplate;
    private final List<Long> createdDirectorIds = new ArrayList<>();

    ChoirDomainMigrationTests(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @AfterEach
    void cleanup() {
        for (Long directorId : createdDirectorIds) {
            deleteDirectorData(directorId);
        }
        createdDirectorIds.clear();
    }

    @Test
    void migrationCreatesAllDomainTables() {
        List<String> tables = jdbcTemplate.execute(
                (org.springframework.jdbc.core.ConnectionCallback<List<String>>) connection -> {
                    List<String> names = new ArrayList<>();

                    try (java.sql.ResultSet resultSet =
                                 connection.getMetaData().getTables(
                                         connection.getCatalog(),
                                         null,
                                         "%",
                                         new String[]{"TABLE"})) {
                        while (resultSet.next()) {
                            String schema = resultSet.getString("TABLE_SCHEM");
                            if (schema != null && schema.equalsIgnoreCase("public")) {
                                names.add(resultSet.getString("TABLE_NAME")
                                        .toLowerCase(java.util.Locale.ROOT));
                            }
                        }
                    }
                    return names;
                });

        assertThat(tables).contains(
                "directors",
                "choirs",
                "singers",
                "concerts",
                "concert_cast_snapshots",
                "repertoire_items",
                "concert_repertoire",
                "formations",
                "formation_versions",
                "formation_positions",
                "transitions",
                "transition_versions",
                "absences"
        );
    }

    @Test
    void choirMustBelongToAnExistingDirector() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO choirs (director_id, name)
                VALUES (-999999, ?)
                """, "Orquesta " + UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void choirNameMustBeUniquePerDirector() {
        String email = UUID.randomUUID() + "@example.test";
        String choirName = "Coro " + UUID.randomUUID();

        jdbcTemplate.update(
                "INSERT INTO directors (display_name, email) VALUES (?, ?)",
                "Director de prueba", email);

        Long directorId = jdbcTemplate.queryForObject(
                "SELECT id FROM directors WHERE email = ?",
                Long.class, email);
        createdDirectorIds.add(directorId);

        jdbcTemplate.update(
                "INSERT INTO choirs (director_id, name) VALUES (?, ?)",
                directorId, choirName);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO choirs (director_id, name) VALUES (?, ?)",
                directorId, choirName))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void formationVersionNumberMustBePositive() {
        String email = UUID.randomUUID() + "@example.test";

        jdbcTemplate.update(
                "INSERT INTO directors (display_name, email) VALUES (?, ?)",
                "Director de prueba", email);

        Long directorId = jdbcTemplate.queryForObject(
                "SELECT id FROM directors WHERE email = ?",
                Long.class, email);
        createdDirectorIds.add(directorId);

        jdbcTemplate.update(
                "INSERT INTO choirs (director_id, name) VALUES (?, ?)",
                directorId, "Coro " + UUID.randomUUID());

        Long choirId = jdbcTemplate.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ?",
                Long.class, directorId);

        jdbcTemplate.update(
                "INSERT INTO formations (choir_id, name) VALUES (?, ?)",
                choirId, "Formación " + UUID.randomUUID());

        Long formationId = jdbcTemplate.queryForObject(
                "SELECT id FROM formations WHERE choir_id = ?",
                Long.class, choirId);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO formation_versions
                    (formation_id, version_number, status)
                VALUES (?, 0, 'DRAFT')
                """, formationId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }


    @Test
    void legacyFormationCanRemainWithoutConcertRepertoireAssociation() {
        Long choirId = createChoir();
        String name = "Formación legacy " + UUID.randomUUID();

        Long formationId = createFormation(choirId, name, null, null);

        java.util.Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT concert_id, repertoire_item_id FROM formations WHERE id = ?",
                formationId);

        assertThat(row.get("concert_id")).isNull();
        assertThat(row.get("repertoire_item_id")).isNull();
    }

    @Test
    void formationCanReferenceConcertRepertoireOccurrence() {
        Long choirId = createChoir();
        Long concertId = createConcert(choirId);
        Long repertoireItemId = createRepertoireItem(choirId);
        jdbcTemplate.update("""
                INSERT INTO concert_repertoire
                    (concert_id, repertoire_item_id, order_index)
                VALUES (?, ?, 0)
                """, concertId, repertoireItemId);

        Long formationId = createFormation(
                choirId, "Formación " + UUID.randomUUID(), concertId, repertoireItemId);

        java.util.Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT concert_id, repertoire_item_id
                FROM formations
                WHERE id = ?
                """, formationId);

        assertThat(((Number) row.get("concert_id")).longValue()).isEqualTo(concertId);
        assertThat(((Number) row.get("repertoire_item_id")).longValue())
                .isEqualTo(repertoireItemId);
    }

    @Test
    void formationAssociationMustHaveBothIdentifiersOrNeither() {
        Long choirId = createChoir();
        Long concertId = createConcert(choirId);

        assertThatThrownBy(() -> createFormation(
                choirId, "Formación incompleta " + UUID.randomUUID(), concertId, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void formationCannotReferenceWorkNotIncludedInConcert() {
        Long choirId = createChoir();
        Long concertId = createConcert(choirId);
        Long repertoireItemId = createRepertoireItem(choirId);

        assertThatThrownBy(() -> createFormation(
                choirId, "Formación sin inclusión " + UUID.randomUUID(),
                concertId, repertoireItemId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void formationCannotAssociateConcertAndWorkFromDifferentChoirs() {
        Long concertChoirId = createChoir();
        Long repertoireChoirId = createChoir();
        Long concertId = createConcert(concertChoirId);
        Long repertoireItemId = createRepertoireItem(repertoireChoirId);

        // La inclusión existe, pero el concierto y la obra pertenecen a coros distintos.
        jdbcTemplate.update("""
                INSERT INTO concert_repertoire
                    (concert_id, repertoire_item_id, order_index)
                VALUES (?, ?, 0)
                """, concertId, repertoireItemId);

        assertThatThrownBy(() -> createFormation(
                concertChoirId, "Formación incompatible " + UUID.randomUUID(),
                concertId, repertoireItemId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void multipleFormationsCanReferenceTheSameConcertRepertoireOccurrence() {
        Long choirId = createChoir();
        Long concertId = createConcert(choirId);
        Long repertoireItemId = createRepertoireItem(choirId);
        jdbcTemplate.update("""
                INSERT INTO concert_repertoire
                    (concert_id, repertoire_item_id, order_index)
                VALUES (?, ?, 0)
                """, concertId, repertoireItemId);

        createFormation(choirId, "Alternativa A " + UUID.randomUUID(),
                concertId, repertoireItemId);
        createFormation(choirId, "Alternativa B " + UUID.randomUUID(),
                concertId, repertoireItemId);

        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM formations
                WHERE choir_id = ?
                  AND concert_id = ?
                  AND repertoire_item_id = ?
                """, Integer.class, choirId, concertId, repertoireItemId);

        assertThat(count).isEqualTo(2);
    }

    private Long createChoir() {
        String email = UUID.randomUUID() + "@example.test";
        jdbcTemplate.update(
                "INSERT INTO directors (display_name, email) VALUES (?, ?)",
                "Director de prueba", email);

        Long directorId = jdbcTemplate.queryForObject(
                "SELECT id FROM directors WHERE email = ?",
                Long.class, email);
        createdDirectorIds.add(directorId);

        jdbcTemplate.update(
                "INSERT INTO choirs (director_id, name) VALUES (?, ?)",
                directorId, "Coro " + UUID.randomUUID());

        return jdbcTemplate.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ?",
                Long.class, directorId);
    }

    private Long createConcert(Long choirId) {
        jdbcTemplate.update(
                "INSERT INTO concerts (choir_id, title) VALUES (?, ?)",
                choirId, "Concierto " + UUID.randomUUID());

        return jdbcTemplate.queryForObject("""
                SELECT id FROM concerts
                WHERE choir_id = ?
                ORDER BY id DESC
                LIMIT 1
                """, Long.class, choirId);
    }

    private Long createRepertoireItem(Long choirId) {
        jdbcTemplate.update(
                "INSERT INTO repertoire_items (choir_id, title) VALUES (?, ?)",
                choirId, "Obra " + UUID.randomUUID());

        return jdbcTemplate.queryForObject("""
                SELECT id FROM repertoire_items
                WHERE choir_id = ?
                ORDER BY id DESC
                LIMIT 1
                """, Long.class, choirId);
    }

    private Long createFormation(
            Long choirId,
            String name,
            Long concertId,
            Long repertoireItemId) {
        jdbcTemplate.update("""
                INSERT INTO formations
                    (choir_id, name, concert_id, repertoire_item_id)
                VALUES (?, ?, ?, ?)
                """, choirId, name, concertId, repertoireItemId);

        return jdbcTemplate.queryForObject("""
                SELECT id FROM formations
                WHERE choir_id = ? AND name = ?
                """, Long.class, choirId, name);
    }

    private void deleteDirectorData(Long directorId) {
        List<Long> choirIds = jdbcTemplate.queryForList(
                "SELECT id FROM choirs WHERE director_id = ?",
                Long.class, directorId);

        for (Long choirId : choirIds) {
            deleteChoirData(choirId);
        }

        jdbcTemplate.update(
                "DELETE FROM directors WHERE id = ?",
                directorId);
    }

    private void deleteChoirData(Long choirId) {
        jdbcTemplate.update("""
                DELETE FROM formation_positions
                WHERE formation_version_id IN (
                    SELECT fv.id
                    FROM formation_versions fv
                    JOIN formations f ON f.id = fv.formation_id
                    WHERE f.choir_id = ?
                )
                """, choirId);

        jdbcTemplate.update("""
                DELETE FROM transition_versions
                WHERE transition_id IN (
                    SELECT id FROM transitions WHERE choir_id = ?
                )
                """, choirId);

        jdbcTemplate.update("""
                DELETE FROM formation_versions
                WHERE formation_id IN (
                    SELECT id FROM formations WHERE choir_id = ?
                )
                """, choirId);

        jdbcTemplate.update(
                "DELETE FROM formations WHERE choir_id = ?",
                choirId);

        jdbcTemplate.update("""
                DELETE FROM concert_repertoire
                WHERE concert_id IN (
                    SELECT id FROM concerts WHERE choir_id = ?
                )
                """, choirId);

        jdbcTemplate.update("""
                DELETE FROM concert_cast_snapshots
                WHERE concert_id IN (
                    SELECT id FROM concerts WHERE choir_id = ?
                )
                """, choirId);

        jdbcTemplate.update("""
                DELETE FROM absences
                WHERE singer_id IN (
                    SELECT id FROM singers WHERE choir_id = ?
                )
                OR concert_id IN (
                    SELECT id FROM concerts WHERE choir_id = ?
                )
                """, choirId, choirId);

        jdbcTemplate.update(
                "DELETE FROM concerts WHERE choir_id = ?",
                choirId);

        jdbcTemplate.update(
                "DELETE FROM repertoire_items WHERE choir_id = ?",
                choirId);

        jdbcTemplate.update(
                "DELETE FROM singers WHERE choir_id = ?",
                choirId);

        jdbcTemplate.update(
                "DELETE FROM transitions WHERE choir_id = ?",
                choirId);

        jdbcTemplate.update(
                "DELETE FROM choirs WHERE id = ?",
                choirId);
    }
}

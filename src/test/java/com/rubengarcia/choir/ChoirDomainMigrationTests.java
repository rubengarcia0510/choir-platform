package com.rubengarcia.choir;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ChoirDomainMigrationTests {

private final JdbcTemplate jdbcTemplate;

ChoirDomainMigrationTests(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
}

@Test
void migrationCreatesAllDomainTables() {
    List<String> tables = jdbcTemplate.execute(
            (org.springframework.jdbc.core.ConnectionCallback<List<String>>) connection -> {
                List<String> names = new java.util.ArrayList<>();

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
                (formation_id, choir_id, version_number, status)
            VALUES (?, ?, 0, 'DRAFT')
            """, formationId, choirId))
            .isInstanceOf(DataIntegrityViolationException.class);
}



    @Test
    void concertRepertoireAcceptsReferencesFromSameChoir() {
        long choirId = createTestChoir();
        long concertId = insertConcert(choirId);
        long itemId = insertRepertoireItem(choirId);

        int inserted = jdbcTemplate.update("""
                INSERT INTO concert_repertoire
                    (concert_id, repertoire_item_id, order_index, choir_id)
                VALUES (?, ?, 0, ?)
                """, concertId, itemId, choirId);

        assertThat(inserted).isEqualTo(1);
    }

    @Test

    void concertRepertoireRejectsCrossChoirReference() {
        long choirA = createTestChoir();
        long choirB = createTestChoir();
        long concertId = insertConcert(choirA);
        long itemId = insertRepertoireItem(choirB);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO concert_repertoire
                    (concert_id, repertoire_item_id, order_index, choir_id)
                VALUES (?, ?, 0, ?)
                """, concertId, itemId, choirA))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void formationPositionRejectsSingerFromAnotherChoir() {
        long choirA = createTestChoir();
        long choirB = createTestChoir();
        long versionId = insertFormationVersion(choirA);
        long singerId = insertSinger(choirB);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO formation_positions
                    (formation_version_id, singer_id, singer_name_snapshot,
                     x_coordinate, y_coordinate, choir_id, singer_choir_id)
                VALUES (?, ?, 'Cantante', 1.0, 2.0, ?, ?)
                """, versionId, singerId, choirA, choirB))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void formationPositionRejectsSingerWithNullAuxiliaryChoirId() {
        long choirId = createTestChoir();
        long versionId = insertFormationVersion(choirId);
        long singerId = insertSinger(choirId);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO formation_positions
                    (formation_version_id, singer_id, singer_name_snapshot,
                     x_coordinate, y_coordinate, choir_id, singer_choir_id)
                VALUES (?, ?, 'Cantante', 1.0, 2.0, ?, NULL)
                """, versionId, singerId, choirId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void transitionVersionAcceptsFormationVersionsFromSameChoir() {
        long choirId = createTestChoir();
        long fromVersion = insertFormationVersion(choirId);
        long toVersion = insertFormationVersion(choirId);
        String transitionName = "Transición " + UUID.randomUUID();

        jdbcTemplate.update(
                "INSERT INTO transitions (choir_id, name) VALUES (?, ?)",
                choirId, transitionName);

        long transitionId = jdbcTemplate.queryForObject(
                "SELECT id FROM transitions WHERE choir_id = ? AND name = ?",
                Long.class, choirId, transitionName);

        int inserted = jdbcTemplate.update("""
                INSERT INTO transition_versions
                    (transition_id, version_number, from_formation_version_id,
                     to_formation_version_id, choir_id)
                VALUES (?, 1, ?, ?, ?)
                """, transitionId, fromVersion, toVersion, choirId);

        assertThat(inserted).isEqualTo(1);
    }

    @Test

    void transitionVersionRejectsFormationVersionFromAnotherChoir() {
        long choirA = createTestChoir();
        long choirB = createTestChoir();
        long fromVersion = insertFormationVersion(choirA);
        long toVersion = insertFormationVersion(choirB);
        jdbcTemplate.update(
                "INSERT INTO transitions (choir_id, name) VALUES (?, ?)",
                choirA, "Transición " + UUID.randomUUID());
        long transitionId = jdbcTemplate.queryForObject(
                "SELECT id FROM transitions WHERE choir_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, choirA);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO transition_versions
                    (transition_id, version_number, from_formation_version_id,
                     to_formation_version_id, choir_id)
                VALUES (?, 1, ?, ?, ?)
                """, transitionId, fromVersion, toVersion, choirA))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void concertCastSnapshotRejectsSingerFromAnotherChoir() {
        long choirA = createTestChoir();
        long choirB = createTestChoir();
        long concertId = insertConcert(choirA);
        long singerId = insertSinger(choirB);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO concert_cast_snapshots
                    (concert_id, source_singer_id, singer_name_snapshot,
                     choir_id, source_singer_choir_id)
                VALUES (?, ?, 'Cantante', ?, ?)
                """, concertId, singerId, choirA, choirB))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void concertCastSnapshotRejectsSingerWithNullAuxiliaryChoirId() {
        long choirId = createTestChoir();
        long concertId = insertConcert(choirId);
        long singerId = insertSinger(choirId);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO concert_cast_snapshots
                    (concert_id, source_singer_id, singer_name_snapshot,
                     choir_id, source_singer_choir_id)
                VALUES (?, ?, 'Cantante', ?, NULL)
                """, concertId, singerId, choirId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void absenceRejectsConcertFromAnotherChoir() {
        long choirA = createTestChoir();
        long choirB = createTestChoir();
        long singerId = insertSinger(choirA);
        long concertId = insertConcert(choirB);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO absences
                    (singer_id, concert_id, start_date, end_date, choir_id)
                VALUES (?, ?, DATE '2026-01-01', DATE '2026-01-02', ?)
                """, singerId, concertId, choirA))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void optionalSingerReferencesCanBeNull() {
        long choirId = createTestChoir();
        long concertId = insertConcert(choirId);
        long versionId = insertFormationVersion(choirId);

        jdbcTemplate.update("""
                INSERT INTO formation_positions
                    (formation_version_id, singer_id, singer_name_snapshot,
                     x_coordinate, y_coordinate, choir_id, singer_choir_id)
                VALUES (?, NULL, 'Cantante histórico', 1.0, 2.0, ?, NULL)
                """, versionId, choirId);

        jdbcTemplate.update("""
                INSERT INTO concert_cast_snapshots
                    (concert_id, source_singer_id, singer_name_snapshot,
                     choir_id, source_singer_choir_id)
                VALUES (?, NULL, 'Cantante histórico', ?, NULL)
                """, concertId, choirId);

        jdbcTemplate.update("""
                INSERT INTO absences
                    (singer_id, concert_id, start_date, end_date, choir_id)
                VALUES (?, NULL, DATE '2026-01-01', DATE '2026-01-02', ?)
                """, insertSinger(choirId), choirId);
    }

    private long createTestChoir() {
        String suffix = UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO directors (display_name, email) VALUES (?, ?)",
                "Director de prueba", suffix + "@example.test");
        long directorId = jdbcTemplate.queryForObject(
                "SELECT id FROM directors WHERE email = ?",
                Long.class, suffix + "@example.test");
        jdbcTemplate.update(
                "INSERT INTO choirs (director_id, name) VALUES (?, ?)",
                directorId, "Coro " + suffix);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ? AND name = ?",
                Long.class, directorId, "Coro " + suffix);
    }

    private long insertSinger(long choirId) {
        jdbcTemplate.update("""
                INSERT INTO singers (choir_id, display_name, voice_part)
                VALUES (?, ?, 'SOPRANO')
                """, choirId, "Cantante " + UUID.randomUUID());
        return jdbcTemplate.queryForObject(
                "SELECT id FROM singers WHERE choir_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, choirId);
    }

    private long insertConcert(long choirId) {
        jdbcTemplate.update(
                "INSERT INTO concerts (choir_id, title) VALUES (?, ?)",
                choirId, "Concierto " + UUID.randomUUID());
        return jdbcTemplate.queryForObject(
                "SELECT id FROM concerts WHERE choir_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, choirId);
    }

    private long insertRepertoireItem(long choirId) {
        jdbcTemplate.update(
                "INSERT INTO repertoire_items (choir_id, title) VALUES (?, ?)",
                choirId, "Obra " + UUID.randomUUID());
        return jdbcTemplate.queryForObject(
                "SELECT id FROM repertoire_items WHERE choir_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, choirId);
    }

    private long insertFormationVersion(long choirId) {
        jdbcTemplate.update(
                "INSERT INTO formations (choir_id, name) VALUES (?, ?)",
                choirId, "Formación " + UUID.randomUUID());
        long formationId = jdbcTemplate.queryForObject(
                "SELECT id FROM formations WHERE choir_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, choirId);
        jdbcTemplate.update("""
                INSERT INTO formation_versions
                    (formation_id, choir_id, version_number, status)
                VALUES (?, ?, 1, 'DRAFT')
                """, formationId, choirId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM formation_versions WHERE formation_id = ?",
                Long.class, formationId);
    }

}

package com.rubengarcia.choir;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class DomainIntegrityConstraintTests {

    private static final long MISSING_ID = -999999L;

    private final JdbcTemplate jdbcTemplate;

    DomainIntegrityConstraintTests(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void concertStatusCheckRejectsUnknownStatus() {
        ChoirFixture choir = createChoir();
        long concertId = createConcert(choir.choirId());

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE concerts SET status = 'INVALID' WHERE id = ?",
                concertId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void formationVersionChecksRejectNonPositiveNumbersAndUnknownStatuses() {
        ChoirFixture choir = createChoir();
        long formationId = createFormation(choir.choirId());

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO formation_versions (formation_id, version_number, status)
                VALUES (?, 0, 'DRAFT')
                """, formationId))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO formation_versions (formation_id, version_number, status)
                VALUES (?, 1, 'INVALID')
                """, formationId))
                .isInstanceOf(DataIntegrityViolationException.class);

        long versionId = createFormationVersion(formationId, 1);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM formation_versions WHERE id = ?",
                String.class, versionId))
                .isEqualTo("DRAFT");
    }

    @Test
    void transitionChecksRejectInvalidNumberStatusAndSameFormationVersion() {
        ChoirFixture choir = createChoir();
        long firstFormation = createFormation(choir.choirId());
        long secondFormation = createFormation(choir.choirId());
        long firstVersion = createFormationVersion(firstFormation, 1);
        long secondVersion = createFormationVersion(secondFormation, 1);
        long transitionId = createTransition(choir.choirId());

        assertThatThrownBy(() -> insertTransitionVersion(
                transitionId, 0, firstVersion, secondVersion, "DRAFT"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertTransitionVersion(
                transitionId, 1, firstVersion, secondVersion, "INVALID"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertTransitionVersion(
                transitionId, 1, firstVersion, firstVersion, "DRAFT"))
                .isInstanceOf(DataIntegrityViolationException.class);

        insertTransitionVersion(
                transitionId, 1, firstVersion, secondVersion, "DRAFT");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transition_versions WHERE transition_id = ?",
                Integer.class, transitionId))
                .isEqualTo(1);
    }

    @Test
    void concertRepertoireRejectsDuplicateCompositeKeyAndNegativeOrder() {
        ChoirFixture choir = createChoir();
        long concertId = createConcert(choir.choirId());
        long firstItemId = createRepertoireItem(choir.choirId());
        long secondItemId = createRepertoireItem(choir.choirId());

        jdbcTemplate.update("""
                INSERT INTO concert_repertoire
                    (concert_id, repertoire_item_id, order_index)
                VALUES (?, ?, 0)
                """, concertId, firstItemId);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO concert_repertoire
                    (concert_id, repertoire_item_id, order_index)
                VALUES (?, ?, 1)
                """, concertId, firstItemId))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO concert_repertoire
                    (concert_id, repertoire_item_id, order_index)
                VALUES (?, ?, -1)
                """, concertId, secondItemId))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO concert_repertoire
                    (concert_id, repertoire_item_id, order_index)
                VALUES (?, ?, 0)
                """, MISSING_ID, secondItemId))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO concert_repertoire
                    (concert_id, repertoire_item_id, order_index)
                VALUES (?, ?, 0)
                """, concertId, MISSING_ID))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM concert_repertoire
                WHERE concert_id = ? AND repertoire_item_id = ?
                """, Integer.class, concertId, firstItemId))
                .isEqualTo(1);
    }

    @Test
    void absencesRejectReversedDatesAndUnknownStatus() {
        ChoirFixture choir = createChoir();
        long singerId = createSinger(choir.choirId());
        LocalDate start = LocalDate.of(2026, 10, 20);
        LocalDate end = LocalDate.of(2026, 10, 19);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO absences (singer_id, start_date, end_date, status)
                VALUES (?, ?, ?, 'CONFIRMED')
                """, singerId, start, end))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO absences (singer_id, start_date, end_date, status)
                VALUES (?, ?, ?, 'INVALID')
                """, singerId, start, start))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO absences (singer_id, start_date, end_date, status)
                VALUES (?, ?, ?, 'CONFIRMED')
                """, MISSING_ID, start, start))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void castSnapshotsAndFormationPositionsSetSingerReferenceToNullOnDelete() {
        ChoirFixture choir = createChoir();
        long singerId = createSinger(choir.choirId());
        long concertId = createConcert(choir.choirId());
        long formationId = createFormation(choir.choirId());
        long formationVersionId = createFormationVersion(formationId, 1);

        jdbcTemplate.update("""
                INSERT INTO concert_cast_snapshots
                    (concert_id, source_singer_id, singer_name_snapshot, voice_part_snapshot)
                VALUES (?, ?, 'Nombre histórico', 'SOPRANO')
                """, concertId, singerId);

        jdbcTemplate.update("""
                INSERT INTO formation_positions
                    (formation_version_id, singer_id, singer_name_snapshot,
                     voice_part_snapshot, x_coordinate, y_coordinate)
                VALUES (?, ?, 'Nombre histórico', 'SOPRANO', 1.000, 2.000)
                """, formationVersionId, singerId);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO concert_cast_snapshots
                    (concert_id, source_singer_id, singer_name_snapshot)
                VALUES (?, ?, 'Referencia inválida')
                """, MISSING_ID, singerId))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO formation_positions
                    (formation_version_id, singer_id, singer_name_snapshot,
                     x_coordinate, y_coordinate)
                VALUES (?, ?, 'Referencia inválida', 1.000, 2.000)
                """, MISSING_ID, singerId))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbcTemplate.update("DELETE FROM singers WHERE id = ?", singerId);

        assertThat(jdbcTemplate.queryForObject("""
                SELECT source_singer_id FROM concert_cast_snapshots
                WHERE concert_id = ?
                """, Long.class, concertId))
                .isNull();

        assertThat(jdbcTemplate.queryForObject("""
                SELECT singer_id FROM formation_positions
                WHERE formation_version_id = ?
                """, Long.class, formationVersionId))
                .isNull();

        assertThat(jdbcTemplate.queryForObject("""
                SELECT singer_name_snapshot FROM concert_cast_snapshots
                WHERE concert_id = ?
                """, String.class, concertId))
                .isEqualTo("Nombre histórico");

        assertThat(jdbcTemplate.queryForObject("""
                SELECT singer_name_snapshot FROM formation_positions
                WHERE formation_version_id = ?
                """, String.class, formationVersionId))
                .isEqualTo("Nombre histórico");
    }

    @Test
    void transitionVersionForeignKeysRejectMissingFormationVersion() {
        ChoirFixture choir = createChoir();
        long formationId = createFormation(choir.choirId());
        long versionId = createFormationVersion(formationId, 1);
        long transitionId = createTransition(choir.choirId());

        assertThatThrownBy(() -> insertTransitionVersion(
                transitionId, 1, MISSING_ID, versionId, "DRAFT"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertTransitionVersion(
                transitionId, 1, versionId, MISSING_ID, "DRAFT"))
                .isInstanceOf(DataIntegrityViolationException.class);
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
                "SELECT id FROM directors WHERE email = ?",
                Long.class, email);

        jdbcTemplate.update("""
                INSERT INTO choirs (director_id, name)
                VALUES (?, ?)
                """, directorId, name);

        long choirId = jdbcTemplate.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ? AND name = ?",
                Long.class, directorId, name);

        return new ChoirFixture(directorId, choirId);
    }

    private long createConcert(long choirId) {
        String title = "Concierto " + UUID.randomUUID();

        jdbcTemplate.update("""
                INSERT INTO concerts (choir_id, title, status)
                VALUES (?, ?, 'PLANNED')
                """, choirId, title);

        return jdbcTemplate.queryForObject(
                "SELECT id FROM concerts WHERE choir_id = ? AND title = ?",
                Long.class, choirId, title);
    }

    private long createSinger(long choirId) {
        String name = "Cantante " + UUID.randomUUID();

        jdbcTemplate.update("""
                INSERT INTO singers (choir_id, display_name, voice_part)
                VALUES (?, ?, 'SOPRANO')
                """, choirId, name);

        return jdbcTemplate.queryForObject(
                "SELECT id FROM singers WHERE choir_id = ? AND display_name = ?",
                Long.class, choirId, name);
    }

    private long createRepertoireItem(long choirId) {
        String title = "Obra " + UUID.randomUUID();

        jdbcTemplate.update("""
                INSERT INTO repertoire_items (choir_id, title)
                VALUES (?, ?)
                """, choirId, title);

        return jdbcTemplate.queryForObject(
                "SELECT id FROM repertoire_items WHERE choir_id = ? AND title = ?",
                Long.class, choirId, title);
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

    private long createTransition(long choirId) {
        String name = "Transición " + UUID.randomUUID();

        jdbcTemplate.update("""
                INSERT INTO transitions (choir_id, name)
                VALUES (?, ?)
                """, choirId, name);

        return jdbcTemplate.queryForObject(
                "SELECT id FROM transitions WHERE choir_id = ? AND name = ?",
                Long.class, choirId, name);
    }

    private void insertTransitionVersion(
            long transitionId,
            int number,
            long fromVersionId,
            long toVersionId,
            String status) {
        jdbcTemplate.update("""
                INSERT INTO transition_versions
                    (transition_id, version_number, from_formation_version_id,
                     to_formation_version_id, status)
                VALUES (?, ?, ?, ?, ?)
                """, transitionId, number, fromVersionId, toVersionId, status);
    }

    private record ChoirFixture(long directorId, long choirId) {
    }
}

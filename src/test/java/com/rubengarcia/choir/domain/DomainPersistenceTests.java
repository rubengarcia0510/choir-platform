package com.rubengarcia.choir.domain;

import com.rubengarcia.choir.repository.SingerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DomainPersistenceTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SingerRepository singerRepository;

    private final List<Long> createdDirectorIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (Long directorId : createdDirectorIds) {
            deleteDirectorData(directorId);
        }
        createdDirectorIds.clear();
    }

    @Test
    void allDomainTablesExistInRelationalSchema() {
        List<String> tables = List.of(
                "directors", "choirs", "singers", "concerts",
                "concert_cast_snapshots", "repertoire_items",
                "concert_repertoire", "formations", "formation_versions",
                "formation_positions", "transitions", "transition_versions",
                "absences", "vocal_sections", "vocal_plans", "vocal_plan_divisions");

        for (String table : tables) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                            + "WHERE UPPER(TABLE_SCHEMA) = UPPER(CURRENT_SCHEMA()) "
                            + "AND UPPER(TABLE_NAME) = UPPER(?)",
                    Integer.class,
                    table.toUpperCase());

            assertThat(count)
                    .as("Debe existir la tabla relacional %s", table)
                    .isEqualTo(1);
        }
    }

    @Test
    void singerSavedWithoutActiveSettingIsPersistedAsActive() {
        String suffix = UUID.randomUUID().toString();
        Long choirId = createChoirForSingerTest(suffix);

        Singer singer = new Singer();
        singer.setChoirId(choirId);
        singer.setDisplayName("Cantante activo " + suffix);
        singer.setEmail("active-" + suffix + "@example.test");
        singer.setVoicePart("SOPRANO");
        singer.setCreatedAt(LocalDateTime.now());

        Singer saved = singerRepository.save(singer)
                .block(Duration.ofSeconds(10));

        assertThat(saved).isNotNull();
        assertThat(saved.isActive()).isTrue();

        Boolean persistedActive = jdbcTemplate.queryForObject(
                "SELECT active FROM singers WHERE id = ?",
                Boolean.class,
                saved.getId());

        assertThat(persistedActive).isTrue();
    }

    @Test
    void singerCanBeSavedExplicitlyInactive() {
        String suffix = UUID.randomUUID().toString();
        Long choirId = createChoirForSingerTest(suffix);

        Singer singer = new Singer();
        singer.setChoirId(choirId);
        singer.setDisplayName("Cantante inactivo " + suffix);
        singer.setEmail("inactive-" + suffix + "@example.test");
        singer.setVoicePart("ALTO");
        singer.setCreatedAt(LocalDateTime.now());
        singer.setActive(false);

        Singer saved = singerRepository.save(singer)
                .block(Duration.ofSeconds(10));

        assertThat(saved).isNotNull();
        assertThat(saved.isActive()).isFalse();

        Boolean persistedActive = jdbcTemplate.queryForObject(
                "SELECT active FROM singers WHERE id = ?",
                Boolean.class,
                saved.getId());

        assertThat(persistedActive).isFalse();
    }

    private Long createChoirForSingerTest(String suffix) {
        String email = "singer-test-" + suffix + "@example.test";
        String choirName = "Coro cantante " + suffix;

        jdbcTemplate.update(
                "INSERT INTO directors (display_name, email) VALUES (?, ?)",
                "Director de prueba",
                email);

        Long directorId = jdbcTemplate.queryForObject(
                "SELECT id FROM directors WHERE email = ?",
                Long.class,
                email);

        createdDirectorIds.add(directorId);

        jdbcTemplate.update(
                "INSERT INTO choirs (director_id, name) VALUES (?, ?)",
                directorId,
                choirName);

        return jdbcTemplate.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ? AND name = ?",
                Long.class,
                directorId,
                choirName);
    }

    @Test
    void concertCastSnapshotKeepsHistoricalSingerDetails() {
        String suffix = UUID.randomUUID().toString();
        String email = "snapshot-" + suffix + "@example.test";
        String choirName = "Coro snapshot " + suffix;
        String concertTitle = "Concierto snapshot " + suffix;

        jdbcTemplate.update(
                "INSERT INTO directors (display_name, email) VALUES (?, ?)",
                "Director de prueba", email);
        Long directorId = jdbcTemplate.queryForObject(
                "SELECT id FROM directors WHERE email = ?", Long.class, email);
        createdDirectorIds.add(directorId);

        jdbcTemplate.update(
                "INSERT INTO choirs (director_id, name) VALUES (?, ?)",
                directorId, choirName);
        Long choirId = jdbcTemplate.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ? AND name = ?",
                Long.class, directorId, choirName);

        jdbcTemplate.update(
                "INSERT INTO vocal_sections (choir_id, name) VALUES (?, ?)",
                choirId, "Sopranos");
        Long sectionId = jdbcTemplate.queryForObject(
                "SELECT id FROM vocal_sections WHERE choir_id = ? AND name = ?",
                Long.class, choirId, "Sopranos");

        jdbcTemplate.update(
                "INSERT INTO singers (choir_id, display_name, voice_part, voice_section_id) VALUES (?, ?, ?, ?)",
                choirId, "Nombre original", "SOPRANO", sectionId);
        Long singerId = jdbcTemplate.queryForObject(
                "SELECT id FROM singers WHERE choir_id = ? AND display_name = ?",
                Long.class, choirId, "Nombre original");

        jdbcTemplate.update(
                "INSERT INTO concerts (choir_id, title) VALUES (?, ?)",
                choirId, concertTitle);
        Long concertId = jdbcTemplate.queryForObject(
                "SELECT id FROM concerts WHERE choir_id = ? AND title = ?",
                Long.class, choirId, concertTitle);

        jdbcTemplate.update(
                "INSERT INTO concert_cast_snapshots "
                        + "(concert_id, source_singer_id, singer_name_snapshot, voice_part_snapshot, "
                        + "voice_section_id_snapshot, voice_section_name_snapshot, division_number_snapshot) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                concertId, singerId, "Nombre original", "SOPRANO", sectionId, "Sopranos", 1);

        jdbcTemplate.update("UPDATE vocal_sections SET name = ? WHERE id = ?",
                "Voces agudas", sectionId);
        jdbcTemplate.update(
                "UPDATE singers SET display_name = ?, voice_part = ? WHERE id = ?",
                "Nombre actualizado", "ALTO", singerId);

        String storedName = jdbcTemplate.queryForObject(
                "SELECT singer_name_snapshot FROM concert_cast_snapshots WHERE concert_id = ?",
                String.class, concertId);
        String storedVoice = jdbcTemplate.queryForObject(
                "SELECT voice_part_snapshot FROM concert_cast_snapshots WHERE concert_id = ?",
                String.class, concertId);
        String currentSingerName = jdbcTemplate.queryForObject(
                "SELECT display_name FROM singers WHERE id = ?",
                String.class, singerId);
        String storedSectionName = jdbcTemplate.queryForObject(
                "SELECT voice_section_name_snapshot FROM concert_cast_snapshots WHERE concert_id = ?",
                String.class, concertId);
        Long storedSectionId = jdbcTemplate.queryForObject(
                "SELECT voice_section_id_snapshot FROM concert_cast_snapshots WHERE concert_id = ?",
                Long.class, concertId);
        Integer storedDivision = jdbcTemplate.queryForObject(
                "SELECT division_number_snapshot FROM concert_cast_snapshots WHERE concert_id = ?",
                Integer.class, concertId);
        String currentSectionName = jdbcTemplate.queryForObject(
                "SELECT name FROM vocal_sections WHERE id = ?", String.class, sectionId);

        assertThat(storedName).isEqualTo("Nombre original");
        assertThat(storedVoice).isEqualTo("SOPRANO");
        assertThat(currentSingerName).isEqualTo("Nombre actualizado");
        assertThat(storedSectionName).isEqualTo("Sopranos");
        assertThat(storedSectionId).isEqualTo(sectionId);
        assertThat(storedDivision).isEqualTo(1);
        assertThat(currentSectionName).isEqualTo("Voces agudas");
    }

    @Test
    void publishedFormationVersionCanCoexistWithANewDraftVersion() {
        String suffix = UUID.randomUUID().toString();
        String email = "formation-" + suffix + "@example.test";
        String choirName = "Coro versiones " + suffix;
        String formationName = "Formación principal " + suffix;

        jdbcTemplate.update(
                "INSERT INTO directors (display_name, email) VALUES (?, ?)",
                "Director de versiones", email);
        Long directorId = jdbcTemplate.queryForObject(
                "SELECT id FROM directors WHERE email = ?", Long.class, email);
        createdDirectorIds.add(directorId);

        jdbcTemplate.update(
                "INSERT INTO choirs (director_id, name) VALUES (?, ?)",
                directorId, choirName);
        Long choirId = jdbcTemplate.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ? AND name = ?",
                Long.class, directorId, choirName);

        jdbcTemplate.update(
                "INSERT INTO formations (choir_id, name) VALUES (?, ?)",
                choirId, formationName);
        Long formationId = jdbcTemplate.queryForObject(
                "SELECT id FROM formations WHERE choir_id = ? AND name = ?",
                Long.class, choirId, formationName);

        jdbcTemplate.update(
                "INSERT INTO formation_versions (formation_id, version_number, status) "
                        + "VALUES (?, ?, ?)",
                formationId, 1, "PUBLISHED");
        jdbcTemplate.update(
                "INSERT INTO formation_versions (formation_id, version_number, status) "
                        + "VALUES (?, ?, ?)",
                formationId, 2, "DRAFT");

        List<Integer> versions = jdbcTemplate.queryForList(
                "SELECT version_number FROM formation_versions "
                        + "WHERE formation_id = ? ORDER BY version_number",
                Integer.class, formationId);
        List<String> statuses = jdbcTemplate.queryForList(
                "SELECT status FROM formation_versions "
                        + "WHERE formation_id = ? ORDER BY version_number",
                String.class, formationId);

        assertThat(versions).containsExactly(1, 2);
        assertThat(statuses).containsExactly("PUBLISHED", "DRAFT");
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

        jdbcTemplate.update("""
                DELETE FROM vocal_plan_divisions
                WHERE vocal_plan_id IN (
                    SELECT id FROM vocal_plans WHERE choir_id = ?
                )
                """, choirId);

        jdbcTemplate.update(
                "DELETE FROM singers WHERE choir_id = ?",
                choirId);

        jdbcTemplate.update(
                "DELETE FROM vocal_plans WHERE choir_id = ?",
                choirId);

        jdbcTemplate.update(
                "DELETE FROM vocal_sections WHERE choir_id = ?",
                choirId);

        jdbcTemplate.update(
                "DELETE FROM transitions WHERE choir_id = ?",
                choirId);

        jdbcTemplate.update(
                "DELETE FROM choirs WHERE id = ?",
                choirId);
    }
}

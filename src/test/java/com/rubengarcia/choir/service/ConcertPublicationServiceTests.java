package com.rubengarcia.choir.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ConcertPublicationServiceTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ConcertPublicationService publicationService;

    @Autowired
    private ConcertCastSelectionService castSelectionService;

    private final List<Long> createdDirectorIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (Long directorId : createdDirectorIds) {
            deleteDirectorData(directorId);
        }
        createdDirectorIds.clear();
    }

    @Test
    void directorCannotPublishAnotherDirectorsConcert() {
        String suffix = UUID.randomUUID().toString();

        Long directorId = insertDirector("owner-" + suffix + "@example.test");
        Long choirId = insertChoir(directorId, "Coro propietario " + suffix);
        Long concertId = insertConcert(choirId, "Concierto ajeno " + suffix);

        String otherEmail = "other-" + suffix + "@example.test";
        Long otherDirectorId = insertDirector(otherEmail);
        Long otherChoirId = insertChoir(otherDirectorId, "Otro coro " + suffix);

        assertThatThrownBy(() ->
                publicationService.publish(otherEmail, otherChoirId, concertId)
                        .block(Duration.ofSeconds(10)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .satisfies(error -> assertThat(
                        ((org.springframework.web.server.ResponseStatusException) error)
                                .getStatusCode())
                        .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND));

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM concerts WHERE id = ?",
                String.class, concertId);
        assertThat(status).isEqualTo("PLANNED");

        Integer snapshots = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM concert_cast_snapshots WHERE concert_id = ?",
                Integer.class, concertId);
        assertThat(snapshots).isZero();
    }

    @Test
    void publishingConcertCapturesCastAndVocalAssignmentSnapshots() {
        String suffix = UUID.randomUUID().toString();
        String email = "publication-" + suffix + "@example.test";

        Long directorId = insertDirector(email);
        Long choirId = insertChoir(directorId, "Coro publicación " + suffix);
        Long sectionId = insertSection(choirId, "Sopranos");
        Long singerId = insertSinger(
                choirId,
                "Nombre original",
                "SOPRANO",
                sectionId
        );
        Long repertoireItemId = insertRepertoireItem(
                choirId,
                "Obra publicación " + suffix
        );
        Long concertId = insertConcert(
                choirId,
                "Concierto publicación " + suffix
        );

        insertConcertRepertoire(concertId, repertoireItemId);
        Long vocalPlanId = insertVocalPlan(repertoireItemId, choirId);
        Long divisionId = insertVocalDivision(
                vocalPlanId,
                choirId,
                sectionId,
                2
        );
        insertVocalAssignment(
                concertId,
                repertoireItemId,
                singerId,
                divisionId,
                choirId
        );

        ConcertPublicationService.Publication publication =
                publicationService.publish(email, choirId, concertId)
                        .block(Duration.ofSeconds(10));

        assertThat(publication).isNotNull();
        assertThat(publication.status()).isEqualTo("PUBLISHED");
        assertThat(publication.publishedAt()).isNotNull();
        assertThat(publication.castSnapshots()).isEqualTo(1);
        assertThat(publication.vocalAssignmentSnapshots()).isEqualTo(1);

        String castName = jdbcTemplate.queryForObject(
                """
                SELECT singer_name_snapshot
                FROM concert_cast_snapshots
                WHERE concert_id = ?
                """,
                String.class,
                concertId
        );

        String castSection = jdbcTemplate.queryForObject(
                """
                SELECT voice_section_name_snapshot
                FROM concert_cast_snapshots
                WHERE concert_id = ?
                """,
                String.class,
                concertId
        );

        Integer castDivision = jdbcTemplate.queryForObject(
                """
                SELECT division_number_snapshot
                FROM concert_cast_snapshots
                WHERE concert_id = ?
                """,
                Integer.class,
                concertId
        );

        String assignmentName = jdbcTemplate.queryForObject(
                """
                SELECT singer_name_snapshot
                FROM concert_vocal_assignment_snapshots
                WHERE concert_id = ?
                  AND repertoire_item_id = ?
                """,
                String.class,
                concertId,
                repertoireItemId
        );

        String assignmentSection = jdbcTemplate.queryForObject(
                """
                SELECT voice_section_name_snapshot
                FROM concert_vocal_assignment_snapshots
                WHERE concert_id = ?
                  AND repertoire_item_id = ?
                """,
                String.class,
                concertId,
                repertoireItemId
        );

        Integer assignmentDivision = jdbcTemplate.queryForObject(
                """
                SELECT division_number_snapshot
                FROM concert_vocal_assignment_snapshots
                WHERE concert_id = ?
                  AND repertoire_item_id = ?
                """,
                Integer.class,
                concertId,
                repertoireItemId
        );

        assertThat(castName).isEqualTo("Nombre original");
        assertThat(castSection).isEqualTo("Sopranos");
        assertThat(castDivision).isEqualTo(null);

        assertThat(assignmentName).isEqualTo("Nombre original");
        assertThat(assignmentSection).isEqualTo("Sopranos");
        assertThat(assignmentDivision).isEqualTo(2);
    }

    @Test
    void publishedSnapshotsRemainHistoricalWhenSingerAndSectionChange() {
        String suffix = UUID.randomUUID().toString();
        String email = "history-" + suffix + "@example.test";

        Long directorId = insertDirector(email);
        Long choirId = insertChoir(directorId, "Coro histórico " + suffix);
        Long sectionId = insertSection(choirId, "Tenores");
        Long singerId = insertSinger(
                choirId,
                "Nombre histórico",
                "TENOR",
                sectionId
        );
        Long repertoireItemId = insertRepertoireItem(
                choirId,
                "Obra histórica " + suffix
        );
        Long concertId = insertConcert(
                choirId,
                "Concierto histórico " + suffix
        );

        insertConcertRepertoire(concertId, repertoireItemId);
        Long vocalPlanId = insertVocalPlan(repertoireItemId, choirId);
        Long divisionId = insertVocalDivision(
                vocalPlanId,
                choirId,
                sectionId,
                1
        );
        insertVocalAssignment(
                concertId,
                repertoireItemId,
                singerId,
                divisionId,
                choirId
        );

        publicationService.publish(email, choirId, concertId)
                .block(Duration.ofSeconds(10));

        jdbcTemplate.update(
                "UPDATE vocal_sections SET name = ? WHERE id = ?",
                "Voces graves",
                sectionId
        );

        jdbcTemplate.update(
                "UPDATE singers SET display_name = ?, voice_part = ? WHERE id = ?",
                "Nombre cambiado",
                "BARITONO",
                singerId
        );

        Long reassignedSectionId = insertSection(choirId, "Contraltos");
        jdbcTemplate.update(
                "UPDATE singers SET voice_section_id = ? WHERE id = ?",
                reassignedSectionId,
                singerId
        );

        String castName = jdbcTemplate.queryForObject(
                """
                SELECT singer_name_snapshot
                FROM concert_cast_snapshots
                WHERE concert_id = ?
                """,
                String.class,
                concertId
        );

        String castSection = jdbcTemplate.queryForObject(
                """
                SELECT voice_section_name_snapshot
                FROM concert_cast_snapshots
                WHERE concert_id = ?
                """,
                String.class,
                concertId
        );

        String assignmentName = jdbcTemplate.queryForObject(
                """
                SELECT singer_name_snapshot
                FROM concert_vocal_assignment_snapshots
                WHERE concert_id = ?
                  AND repertoire_item_id = ?
                """,
                String.class,
                concertId,
                repertoireItemId
        );

        String assignmentVoicePart = jdbcTemplate.queryForObject(
                """
                SELECT voice_part_snapshot
                FROM concert_vocal_assignment_snapshots
                WHERE concert_id = ?
                  AND repertoire_item_id = ?
                """,
                String.class,
                concertId,
                repertoireItemId
        );

        String assignmentSection = jdbcTemplate.queryForObject(
                """
                SELECT voice_section_name_snapshot
                FROM concert_vocal_assignment_snapshots
                WHERE concert_id = ?
                  AND repertoire_item_id = ?
                """,
                String.class,
                concertId,
                repertoireItemId
        );

        Integer assignmentDivision = jdbcTemplate.queryForObject(
                """
                SELECT division_number_snapshot
                FROM concert_vocal_assignment_snapshots
                WHERE concert_id = ?
                  AND repertoire_item_id = ?
                """,
                Integer.class,
                concertId,
                repertoireItemId
        );

        assertThat(castName).isEqualTo("Nombre histórico");
        assertThat(castSection).isEqualTo("Tenores");

        assertThat(assignmentName).isEqualTo("Nombre histórico");
        assertThat(assignmentVoicePart).isEqualTo("TENOR");
        assertThat(assignmentSection).isEqualTo("Tenores");
        assertThat(assignmentDivision).isEqualTo(1);
    }

    @Test
    void publishingConcertFromRehearsalCapturesCastAndChangesStatus() {
        String suffix = UUID.randomUUID().toString();
        String email = "rehearsal-" + suffix + "@example.test";

        Long directorId = insertDirector(email);
        Long choirId = insertChoir(directorId, "Coro ensayo " + suffix);
        insertSinger(choirId, "Coreuta de ensayo", "SOPRANO", null);
        Long concertId = insertConcert(choirId, "Concierto ensayo " + suffix);
        jdbcTemplate.update(
                "UPDATE concerts SET status = 'REHEARSAL' WHERE id = ?",
                concertId);

        ConcertPublicationService.Publication publication =
                publicationService.publish(email, choirId, concertId)
                        .block(Duration.ofSeconds(10));

        assertThat(publication).isNotNull();
        assertThat(publication.status()).isEqualTo("PUBLISHED");
        assertThat(publication.castSnapshots()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM concerts WHERE id = ?",
                String.class,
                concertId)).isEqualTo("PUBLISHED");
    }

    @Test
    void configuredCastSnapshotSurvivesLaterSingerChanges() {
        String suffix = UUID.randomUUID().toString();
        String email = "cast-snapshot-" + suffix + "@example.test";
        Long directorId = insertDirector(email);
        Long choirId = insertChoir(directorId, "Coro snapshot " + suffix);
        Long sectionId = insertSection(choirId, "Tenores");
        Long singerId = insertSinger(
                choirId, "Nombre al configurar", "TENOR", sectionId);
        Long concertId = insertConcert(choirId, "Concierto snapshot " + suffix);

        castSelectionService.replaceSelection(
                email, choirId, concertId, List.of(singerId))
                .block(Duration.ofSeconds(10));
        assertThat(castSelectionService.getCast(email, choirId, concertId)
                .collectList().block(Duration.ofSeconds(10)))
                .extracting(member -> member.name())
                .containsExactly("Nombre al configurar");

        jdbcTemplate.update("""
                UPDATE singers
                SET display_name = ?, active = FALSE
                WHERE id = ?
                """, "Nombre posterior", singerId);
        assertThat(castSelectionService.getCast(email, choirId, concertId)
                .collectList().block(Duration.ofSeconds(10)))
                .extracting(member -> member.name())
                .containsExactly("Nombre al configurar");

        ConcertPublicationService.Publication publication =
                publicationService.publish(email, choirId, concertId)
                        .block(Duration.ofSeconds(10));

        assertThat(publication.castSnapshots()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT singer_name_snapshot
                FROM concert_cast_snapshots
                WHERE concert_id = ?
                """, String.class, concertId))
                .isEqualTo("Nombre al configurar");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT voice_section_name_snapshot
                FROM concert_cast_snapshots
                WHERE concert_id = ?
                """, String.class, concertId))
                .isEqualTo("Tenores");
    }

    @Test
    void publishedConcertCannotBePublishedAgain() {
        String suffix = UUID.randomUUID().toString();
        String email = "repeat-" + suffix + "@example.test";

        Long directorId = insertDirector(email);
        Long choirId = insertChoir(directorId, "Coro repetición " + suffix);
        Long concertId = insertConcert(
                choirId,
                "Concierto repetición " + suffix
        );

        publicationService.publish(email, choirId, concertId)
                .block(Duration.ofSeconds(10));

        assertThatThrownBy(() ->
                publicationService.publish(email, choirId, concertId)
                        .block(Duration.ofSeconds(10))
        )
                .hasMessageContaining(
                        "Solo se pueden publicar conciertos en estado PLANNED o REHEARSAL");

        Integer castCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM concert_cast_snapshots
                WHERE concert_id = ?
                """,
                Integer.class,
                concertId
        );

        Integer assignmentCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM concert_vocal_assignment_snapshots
                WHERE concert_id = ?
                """,
                Integer.class,
                concertId
        );

        assertThat(castCount).isEqualTo(0);
        assertThat(assignmentCount).isEqualTo(0);
    }

    private Long insertDirector(String email) {
        jdbcTemplate.update(
                "INSERT INTO directors (display_name, email) VALUES (?, ?)",
                "Director de prueba",
                email
        );

        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM directors WHERE email = ?",
                Long.class,
                email
        );

        createdDirectorIds.add(id);
        return id;
    }

    private Long insertChoir(Long directorId, String name) {
        jdbcTemplate.update(
                "INSERT INTO choirs (director_id, name) VALUES (?, ?)",
                directorId,
                name
        );

        return jdbcTemplate.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ? AND name = ?",
                Long.class,
                directorId,
                name
        );
    }

    private Long insertSection(Long choirId, String name) {
        jdbcTemplate.update(
                "INSERT INTO vocal_sections (choir_id, name) VALUES (?, ?)",
                choirId,
                name
        );

        return jdbcTemplate.queryForObject(
                "SELECT id FROM vocal_sections WHERE choir_id = ? AND name = ?",
                Long.class,
                choirId,
                name
        );
    }

    private Long insertSinger(
            Long choirId,
            String name,
            String voicePart,
            Long sectionId
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO singers
                    (choir_id, display_name, voice_part, voice_section_id)
                VALUES (?, ?, ?, ?)
                """,
                choirId,
                name,
                voicePart,
                sectionId
        );

        return jdbcTemplate.queryForObject(
                "SELECT id FROM singers WHERE choir_id = ? AND display_name = ?",
                Long.class,
                choirId,
                name
        );
    }

    private Long insertRepertoireItem(Long choirId, String title) {
        jdbcTemplate.update(
                """
                INSERT INTO repertoire_items
                    (choir_id, title)
                VALUES (?, ?)
                """,
                choirId,
                title
        );

        return jdbcTemplate.queryForObject(
                "SELECT id FROM repertoire_items WHERE choir_id = ? AND title = ?",
                Long.class,
                choirId,
                title
        );
    }

    private Long insertConcert(Long choirId, String title) {
        jdbcTemplate.update(
                """
                INSERT INTO concerts
                    (choir_id, title)
                VALUES (?, ?)
                """,
                choirId,
                title
        );

        return jdbcTemplate.queryForObject(
                "SELECT id FROM concerts WHERE choir_id = ? AND title = ?",
                Long.class,
                choirId,
                title
        );
    }

    private void insertConcertRepertoire(Long concertId, Long repertoireItemId) {
        jdbcTemplate.update(
                """
                INSERT INTO concert_repertoire
                    (concert_id, repertoire_item_id, order_index)
                VALUES (?, ?, ?)
                """,
                concertId,
                repertoireItemId,
                0
        );
    }

    private Long insertVocalPlan(Long repertoireItemId, Long choirId) {
        jdbcTemplate.update(
                """
                INSERT INTO vocal_plans
                    (repertoire_item_id, choir_id)
                VALUES (?, ?)
                """,
                repertoireItemId,
                choirId
        );

        return jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM vocal_plans
                WHERE repertoire_item_id = ?
                """,
                Long.class,
                repertoireItemId
        );
    }

    private Long insertVocalDivision(
            Long vocalPlanId,
            Long choirId,
            Long sectionId,
            int divisionNumber
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO vocal_plan_divisions
                    (vocal_plan_id, choir_id, vocal_section_id, division_number)
                VALUES (?, ?, ?, ?)
                """,
                vocalPlanId,
                choirId,
                sectionId,
                divisionNumber
        );

        return jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM vocal_plan_divisions
                WHERE vocal_plan_id = ?
                  AND vocal_section_id = ?
                  AND division_number = ?
                """,
                Long.class,
                vocalPlanId,
                sectionId,
                divisionNumber
        );
    }

    private void insertVocalAssignment(
            Long concertId,
            Long repertoireItemId,
            Long singerId,
            Long divisionId,
            Long choirId
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO concert_vocal_assignments
                    (concert_id, repertoire_item_id, singer_id,
                     vocal_plan_division_id, choir_id)
                VALUES (?, ?, ?, ?, ?)
                """,
                concertId,
                repertoireItemId,
                singerId,
                divisionId,
                choirId
        );
    }

    private void deleteDirectorData(Long directorId) {
        List<Long> choirIds = jdbcTemplate.queryForList(
                "SELECT id FROM choirs WHERE director_id = ?",
                Long.class,
                directorId
        );

        for (Long choirId : choirIds) {
            deleteChoirData(choirId);
        }

        jdbcTemplate.update(
                "DELETE FROM directors WHERE id = ?",
                directorId
        );
    }

    private void deleteChoirData(Long choirId) {
        jdbcTemplate.update(
                """
                DELETE FROM concert_vocal_assignment_snapshots
                WHERE concert_id IN (
                    SELECT id FROM concerts WHERE choir_id = ?
                )
                """,
                choirId
        );

        jdbcTemplate.update(
                """
                DELETE FROM concert_cast_snapshots
                WHERE concert_id IN (
                    SELECT id FROM concerts WHERE choir_id = ?
                )
                """,
                choirId
        );

        jdbcTemplate.update(
                """
                DELETE FROM concert_vocal_assignments
                WHERE choir_id = ?
                """,
                choirId
        );

        jdbcTemplate.update(
                """
                DELETE FROM concert_repertoire
                WHERE concert_id IN (
                    SELECT id FROM concerts WHERE choir_id = ?
                )
                """,
                choirId
        );

        jdbcTemplate.update(
                """
                DELETE FROM absences
                WHERE singer_id IN (
                    SELECT id FROM singers WHERE choir_id = ?
                )
                OR concert_id IN (
                    SELECT id FROM concerts WHERE choir_id = ?
                )
                """,
                choirId,
                choirId
        );

        jdbcTemplate.update(
                "DELETE FROM concerts WHERE choir_id = ?",
                choirId
        );

        jdbcTemplate.update(
                """
                DELETE FROM vocal_plan_divisions
                WHERE vocal_plan_id IN (
                    SELECT id FROM vocal_plans WHERE choir_id = ?
                )
                """,
                choirId
        );

        jdbcTemplate.update(
                "DELETE FROM vocal_plans WHERE choir_id = ?",
                choirId
        );

        jdbcTemplate.update(
                "DELETE FROM singers WHERE choir_id = ?",
                choirId
        );

        jdbcTemplate.update(
                "DELETE FROM repertoire_items WHERE choir_id = ?",
                choirId
        );

        jdbcTemplate.update(
                "DELETE FROM vocal_sections WHERE choir_id = ?",
                choirId
        );

        jdbcTemplate.update(
                "DELETE FROM transitions WHERE choir_id = ?",
                choirId
        );

        jdbcTemplate.update(
                "DELETE FROM formations WHERE choir_id = ?",
                choirId
        );

        jdbcTemplate.update(
                "DELETE FROM choirs WHERE id = ?",
                choirId
        );
    }
}

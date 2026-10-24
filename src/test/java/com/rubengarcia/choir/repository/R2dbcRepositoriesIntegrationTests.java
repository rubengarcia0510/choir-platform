package com.rubengarcia.choir.repository;

import com.rubengarcia.choir.formation.adapter.out.persistence.FormationEntity;
import com.rubengarcia.choir.formation.adapter.out.persistence.FormationPositionRepository;
import com.rubengarcia.choir.formation.adapter.out.persistence.FormationRepository;
import com.rubengarcia.choir.formation.adapter.out.persistence.FormationVersionEntity;
import com.rubengarcia.choir.formation.adapter.out.persistence.FormationVersionRepository;
import com.rubengarcia.choir.domain.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class R2dbcRepositoriesIntegrationTests {

    private final JdbcTemplate jdbc;
    private final AbsenceRepository absences;
    private final ChoirRepository choirs;
    private final ConcertCastSnapshotRepository snapshots;
    private final ConcertRepository concerts;
    private final DirectorEmailVerificationTokenRepository tokens;
    private final DirectorRepository directors;
    private final FormationPositionRepository positions;
    private final FormationRepository formations;
    private final FormationVersionRepository formationVersions;
    private final RepertoireItemRepository repertoire;
    private final SingerRepository singers;
    private final TransitionRepository transitions;
    private final TransitionVersionRepository transitionVersions;

    R2dbcRepositoriesIntegrationTests(
            JdbcTemplate jdbc,
            AbsenceRepository absences,
            ChoirRepository choirs,
            ConcertCastSnapshotRepository snapshots,
            ConcertRepository concerts,
            DirectorEmailVerificationTokenRepository tokens,
            DirectorRepository directors,
            FormationPositionRepository positions,
            FormationRepository formations,
            FormationVersionRepository formationVersions,
            RepertoireItemRepository repertoire,
            SingerRepository singers,
            TransitionRepository transitions,
            TransitionVersionRepository transitionVersions) {
        this.jdbc = jdbc;
        this.absences = absences;
        this.choirs = choirs;
        this.snapshots = snapshots;
        this.concerts = concerts;
        this.tokens = tokens;
        this.directors = directors;
        this.positions = positions;
        this.formations = formations;
        this.formationVersions = formationVersions;
        this.repertoire = repertoire;
        this.singers = singers;
        this.transitions = transitions;
        this.transitionVersions = transitionVersions;
    }

    @Test
    void derivedQueriesAndTokenConsumptionWorkAgainstTheDatabase() {
        String suffix = UUID.randomUUID().toString();
        String email = suffix + "@example.test";

        Long directorId = null;
        Long choirId = null;
        Long activeSingerId = null;
        Long inactiveSingerId = null;
        Long concertId = null;
        Long formationId = null;
        Long versionOneId = null;
        Long versionTwoId = null;
        Long transitionId = null;
        Long tokenId = null;

        try {
            jdbc.update(
                    "INSERT INTO directors (display_name, email) VALUES (?, ?)",
                    "Director " + suffix, email);
            directorId = id(
                    "SELECT id FROM directors WHERE email = ?", email);

            jdbc.update(
                    "INSERT INTO choirs (director_id, name) VALUES (?, ?)",
                    directorId, "Choir " + suffix);
            choirId = id(
                    "SELECT id FROM choirs WHERE director_id = ? AND name = ?",
                    directorId, "Choir " + suffix);

            jdbc.update("""
                    INSERT INTO singers
                        (choir_id, display_name, email, voice_part, active)
                    VALUES (?, ?, ?, ?, TRUE)
                    """,
                    choirId, "Active " + suffix,
                    "active-" + email, "SOPRANO");
            activeSingerId = id(
                    "SELECT id FROM singers WHERE email = ?",
                    "active-" + email);

            jdbc.update("""
                    INSERT INTO singers
                        (choir_id, display_name, email, voice_part, active)
                    VALUES (?, ?, ?, ?, FALSE)
                    """,
                    choirId, "Inactive " + suffix,
                    "inactive-" + email, "ALTO");
            inactiveSingerId = id(
                    "SELECT id FROM singers WHERE email = ?",
                    "inactive-" + email);

            jdbc.update("""
                    INSERT INTO concerts (choir_id, title, concert_date, status)
                    VALUES (?, ?, ?, 'PLANNED')
                    """,
                    choirId, "Concert " + suffix, LocalDate.now());
            concertId = id(
                    "SELECT id FROM concerts WHERE choir_id = ? AND title = ?",
                    choirId, "Concert " + suffix);

            jdbc.update("""
                    INSERT INTO concert_cast_snapshots
                        (concert_id, source_singer_id,
                         singer_name_snapshot, voice_part_snapshot)
                    VALUES (?, ?, ?, ?)
                    """,
                    concertId, activeSingerId, "Snapshot " + suffix, "SOPRANO");

            jdbc.update("""
                    INSERT INTO absences
                        (singer_id, concert_id, start_date, end_date, reason, status)
                    VALUES (?, ?, ?, ?, ?, 'CONFIRMED')
                    """,
                    activeSingerId, concertId,
                    LocalDate.now(), LocalDate.now(), "Test " + suffix);

            jdbc.update("""
                    INSERT INTO repertoire_items (choir_id, title)
                    VALUES (?, ?)
                    """,
                    choirId, "Repertoire " + suffix);

            jdbc.update("""
                    INSERT INTO formations (choir_id, name)
                    VALUES (?, ?)
                    """,
                    choirId, "Formation " + suffix);
            formationId = id(
                    "SELECT id FROM formations WHERE choir_id = ? AND name = ?",
                    choirId, "Formation " + suffix);

            jdbc.update("""
                    INSERT INTO formation_versions
                        (formation_id, version_number, status)
                    VALUES (?, 1, 'DRAFT')
                    """, formationId);
            versionOneId = id("""
                    SELECT id FROM formation_versions
                    WHERE formation_id = ? AND version_number = 1
                    """, formationId);

            jdbc.update("""
                    INSERT INTO formation_versions
                        (formation_id, version_number, status)
                    VALUES (?, 2, 'DRAFT')
                    """, formationId);
            versionTwoId = id("""
                    SELECT id FROM formation_versions
                    WHERE formation_id = ? AND version_number = 2
                    """, formationId);

            jdbc.update("""
                    INSERT INTO formation_positions
                        (formation_version_id, singer_id, singer_name_snapshot,
                         voice_part_snapshot, position_label, x_coordinate, y_coordinate)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    versionOneId, activeSingerId, "Position " + suffix,
                    "SOPRANO", "Center", 1.25, 2.50);

            jdbc.update("""
                    INSERT INTO transitions (choir_id, name)
                    VALUES (?, ?)
                    """,
                    choirId, "Transition " + suffix);
            transitionId = id(
                    "SELECT id FROM transitions WHERE choir_id = ? AND name = ?",
                    choirId, "Transition " + suffix);

            jdbc.update("""
                    INSERT INTO transition_versions
                        (transition_id, version_number,
                         from_formation_version_id, to_formation_version_id, status)
                    VALUES (?, 1, ?, ?, 'DRAFT')
                    """,
                    transitionId, versionOneId, versionTwoId);

            String tokenHash = UUID.randomUUID().toString().replace("-", "");
            jdbc.update("""
                    INSERT INTO director_email_verification_tokens
                        (director_id, token_hash, expires_at)
                    VALUES (?, ?, ?)
                    """,
                    directorId, tokenHash, LocalDateTime.now().plusHours(1));
            tokenId = id(
                    "SELECT id FROM director_email_verification_tokens WHERE token_hash = ?",
                    tokenHash);

            // DirectorRepository
            assertThat(directors.findByEmailIgnoreCase(
                    email.toUpperCase(Locale.ROOT)).block())
                    .isNotNull()
                    .extracting(Director::getId)
                    .isEqualTo(directorId);

            // ChoirRepository
            assertThat(choirs.findByDirectorId(directorId).collectList().block())
                    .extracting(Choir::getId)
                    .containsExactly(choirId);

            // SingerRepository
            assertThat(singers.findByChoirId(choirId).collectList().block())
                    .extracting(Singer::getId)
                    .containsExactlyInAnyOrder(activeSingerId, inactiveSingerId);

            assertThat(singers.findByChoirIdAndActiveTrue(choirId)
                    .collectList().block())
                    .extracting(Singer::getId)
                    .containsExactly(activeSingerId);

            // ConcertRepository
            assertThat(concerts.findByChoirId(choirId).collectList().block())
                    .extracting(Concert::getId)
                    .containsExactly(concertId);

            LocalDate updatedConcertDate = LocalDate.now().plusDays(1);
            assertThat(concerts.updateIfStatus(
                    choirId,
                    concertId,
                    "PLANNED",
                    "Updated concert " + suffix,
                    updatedConcertDate,
                    "Auditorium",
                    "REHEARSAL").block())
                    .isEqualTo(1);

            jdbc.update("""
                    UPDATE concerts
                    SET status = 'PUBLISHED',
                        published_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, concertId);

            assertThat(concerts.updateIfStatus(
                    choirId,
                    concertId,
                    "REHEARSAL",
                    "Stale update " + suffix,
                    LocalDate.now().plusDays(2),
                    "Other venue",
                    "PLANNED").block())
                    .isZero();

            Concert unchangedPublishedConcert = concerts.findById(concertId).block();
            assertThat(unchangedPublishedConcert).isNotNull();
            assertThat(unchangedPublishedConcert.getStatus()).isEqualTo("PUBLISHED");
            assertThat(unchangedPublishedConcert.getPublishedAt()).isNotNull();
            assertThat(unchangedPublishedConcert.getTitle())
                    .isEqualTo("Updated concert " + suffix);
            assertThat(unchangedPublishedConcert.getConcertDate())
                    .isEqualTo(updatedConcertDate);
            assertThat(unchangedPublishedConcert.getVenue()).isEqualTo("Auditorium");

            // ConcertCastSnapshotRepository
            assertThat(snapshots.findByConcertId(concertId).collectList().block())
                    .hasSize(1);

            // AbsenceRepository
            assertThat(absences.findBySingerId(activeSingerId).collectList().block())
                    .hasSize(1);
            assertThat(absences.findByConcertId(concertId).collectList().block())
                    .hasSize(1);

            // RepertoireItemRepository
            assertThat(repertoire.findByChoirId(choirId).collectList().block())
                    .hasSize(1);

            // FormationRepository
            assertThat(formations.findByChoirId(choirId).collectList().block())
                    .extracting(FormationEntity::getId)
                    .containsExactly(formationId);

            // FormationVersionRepository
            assertThat(formationVersions.findByFormationId(formationId)
                    .collectList().block())
                    .extracting(FormationVersionEntity::getVersionNumber)
                    .containsExactlyInAnyOrder(1, 2);

            assertThat(formationVersions.findByFormationIdAndVersionNumber(
                    formationId, 2).block())
                    .isNotNull()
                    .extracting(FormationVersionEntity::getId)
                    .isEqualTo(versionTwoId);

            // FormationPositionRepository
            assertThat(positions.findByFormationVersionId(versionOneId)
                    .collectList().block())
                    .hasSize(1);
            assertThat(positions.findBySingerId(activeSingerId)
                    .collectList().block())
                    .hasSize(1);

            // TransitionRepository
            assertThat(transitions.findByChoirId(choirId).collectList().block())
                    .extracting(Transition::getId)
                    .containsExactly(transitionId);

            // TransitionVersionRepository
            assertThat(transitionVersions.findByTransitionId(transitionId)
                    .collectList().block())
                    .hasSize(1);
            assertThat(transitionVersions.findByTransitionIdAndVersionNumber(
                    transitionId, 1).block())
                    .isNotNull()
                    .extracting(TransitionVersion::getTransitionId)
                    .isEqualTo(transitionId);

            // DirectorEmailVerificationTokenRepository
            assertThat(tokens.findByTokenHash(tokenHash).block())
                    .isNotNull()
                    .extracting(DirectorEmailVerificationToken::getId)
                    .isEqualTo(tokenId);

            LocalDateTime now = LocalDateTime.now();
            assertThat(tokens.consumeIfValid(tokenId, now).block()).isEqualTo(1);
            assertThat(tokens.consumeIfValid(tokenId, now.plusSeconds(1)).block())
                    .isZero();

            tokens.deleteAllByDirectorId(directorId).block();
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM director_email_verification_tokens
                    WHERE director_id = ?
                    """, Integer.class, directorId)).isZero();

        } finally {
            if (directorId != null) {
                jdbc.update(
                        "DELETE FROM director_email_verification_tokens WHERE director_id = ?",
                        directorId);
            }
            if (transitionId != null) {
                jdbc.update(
                        "DELETE FROM transition_versions WHERE transition_id = ?",
                        transitionId);
            }
            if (versionOneId != null || versionTwoId != null) {
                jdbc.update("""
                        DELETE FROM formation_positions
                        WHERE formation_version_id IN (?, ?)
                        """, versionOneId, versionTwoId);
            }
            if (activeSingerId != null) {
                jdbc.update("DELETE FROM absences WHERE singer_id = ?", activeSingerId);
            }
            if (concertId != null) {
                jdbc.update("DELETE FROM concert_cast_snapshots WHERE concert_id = ?", concertId);
                jdbc.update("DELETE FROM absences WHERE concert_id = ?", concertId);
                jdbc.update("DELETE FROM concert_repertoire WHERE concert_id = ?", concertId);
            }
            if (concertId != null) {
                jdbc.update("DELETE FROM concerts WHERE id = ?", concertId);
            }
            if (versionOneId != null || versionTwoId != null) {
                jdbc.update(
                        "DELETE FROM transition_versions WHERE from_formation_version_id IN (?, ?) OR to_formation_version_id IN (?, ?)",
                        versionOneId, versionTwoId, versionOneId, versionTwoId);
                jdbc.update(
                        "DELETE FROM formation_versions WHERE id IN (?, ?)",
                        versionOneId, versionTwoId);
            }
            if (transitionId != null) {
                jdbc.update("DELETE FROM transitions WHERE id = ?", transitionId);
            }
            if (formationId != null) {
                jdbc.update("DELETE FROM formations WHERE id = ?", formationId);
            }
            if (choirId != null) {
                jdbc.update("DELETE FROM repertoire_items WHERE choir_id = ?", choirId);
                jdbc.update("DELETE FROM singers WHERE choir_id = ?", choirId);
                jdbc.update("DELETE FROM choirs WHERE id = ?", choirId);
            }
            if (directorId != null) {
                jdbc.update("DELETE FROM directors WHERE id = ?", directorId);
            }
        }
    }

    private Long id(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }
}

package com.rubengarcia.choir.transition;

import static org.assertj.core.api.Assertions.assertThat;

import com.rubengarcia.choir.scene.domain.SceneRuleEvaluator.Status;
import com.rubengarcia.choir.transition.application.port.out.TransitionRuleEvaluationHistoryPort;
import com.rubengarcia.choir.transition.domain.TransitionDisplacementMetrics;
import com.rubengarcia.choir.transition.domain.TransitionRuleEvaluationSnapshot;
import com.rubengarcia.choir.transition.domain.TransitionTrajectoryAnalysis;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class TransitionRuleEvaluationHistoryH2Tests {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransitionRuleEvaluationHistoryPort historyPort;

    @Test
    void persistsAndRecoversComposedEvaluationOnH2() {
        String unique = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO directors (display_name, email)
                VALUES ('CHOIR-123 H2 test', ?)
                """, "choir-123-h2-" + unique + "@example.test");
        long directorId = jdbc.queryForObject(
                "SELECT id FROM directors WHERE email = ?",
                Long.class, "choir-123-h2-" + unique + "@example.test");
        jdbc.update("INSERT INTO choirs (director_id, name) VALUES (?, ?)",
                directorId, "CHOIR-123 H2 choir " + unique);
        long choirId = jdbc.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ? AND name = ?",
                Long.class, directorId, "CHOIR-123 H2 choir " + unique);
        jdbc.update("""
                INSERT INTO concerts (choir_id, title, status)
                VALUES (?, ?, 'PLANNED')
                """, choirId, "CHOIR-123 H2 concert " + unique);
        long concertId = jdbc.queryForObject(
                "SELECT id FROM concerts WHERE choir_id = ? AND title = ?",
                Long.class, choirId, "CHOIR-123 H2 concert " + unique);
        jdbc.update("INSERT INTO repertoire_items (choir_id, title) VALUES (?, ?)",
                choirId, "CHOIR-123 H2 song " + unique);
        long repertoireItemId = jdbc.queryForObject(
                "SELECT id FROM repertoire_items WHERE choir_id = ? AND title = ?",
                Long.class, choirId, "CHOIR-123 H2 song " + unique);
        jdbc.update("""
                INSERT INTO concert_repertoire (
                    concert_id, repertoire_item_id, order_index
                ) VALUES (?, ?, 0)
                """, concertId, repertoireItemId);
        jdbc.update("""
                INSERT INTO formations (choir_id, name, concert_id, repertoire_item_id)
                VALUES (?, ?, ?, ?)
                """, choirId, "CHOIR-123 H2 FROM " + unique,
                concertId, repertoireItemId);
        long fromFormationId = jdbc.queryForObject(
                "SELECT id FROM formations WHERE choir_id = ? AND name = ?",
                Long.class, choirId, "CHOIR-123 H2 FROM " + unique);
        jdbc.update("""
                INSERT INTO formations (choir_id, name, concert_id, repertoire_item_id)
                VALUES (?, ?, ?, ?)
                """, choirId, "CHOIR-123 H2 TO " + unique,
                concertId, repertoireItemId);
        long toFormationId = jdbc.queryForObject(
                "SELECT id FROM formations WHERE choir_id = ? AND name = ?",
                Long.class, choirId, "CHOIR-123 H2 TO " + unique);
        jdbc.update("""
                INSERT INTO formation_versions (formation_id, version_number, status)
                VALUES (?, 1, 'DRAFT')
                """, fromFormationId);
        long fromVersionId = jdbc.queryForObject(
                "SELECT id FROM formation_versions WHERE formation_id = ?",
                Long.class, fromFormationId);
        jdbc.update("""
                INSERT INTO formation_versions (formation_id, version_number, status)
                VALUES (?, 1, 'DRAFT')
                """, toFormationId);
        long toVersionId = jdbc.queryForObject(
                "SELECT id FROM formation_versions WHERE formation_id = ?",
                Long.class, toFormationId);
        jdbc.update("INSERT INTO transitions (choir_id, name) VALUES (?, ?)",
                choirId, "CHOIR-123 H2 transition " + unique);
        long transitionId = jdbc.queryForObject(
                "SELECT id FROM transitions WHERE choir_id = ? AND name = ?",
                Long.class, choirId, "CHOIR-123 H2 transition " + unique);
        jdbc.update("""
                INSERT INTO transition_versions (
                    transition_id, version_number,
                    from_formation_version_id, to_formation_version_id
                ) VALUES (?, 1, ?, ?)
                """, transitionId, fromVersionId, toVersionId);
        long transitionVersionId = jdbc.queryForObject(
                "SELECT id FROM transition_versions WHERE transition_id = ?",
                Long.class, transitionId);

        var metrics = new TransitionDisplacementMetrics(
                transitionVersionId,
                fromVersionId,
                toVersionId,
                List.of(new TransitionDisplacementMetrics.SingerDistance(
                        41, new BigDecimal("0.500000"))),
                new BigDecimal("0.500000"),
                new BigDecimal("0.500000"),
                1,
                List.of());
        var trajectories = new TransitionTrajectoryAnalysis(
                transitionVersionId,
                List.of(new TransitionTrajectoryAnalysis.TrajectoryFinding(
                        41,
                        42,
                        TransitionTrajectoryAnalysis.TemporalConflictStatus.CONFIRMED)),
                List.of());
        var snapshot = new TransitionRuleEvaluationSnapshot(
                null,
                transitionVersionId,
                transitionId,
                fromVersionId,
                toVersionId,
                repertoireItemId,
                1,
                new BigDecimal("0.0500000"),
                Status.VIOLATED,
                LocalDateTime.now(),
                List.of(),
                List.of(),
                List.of(),
                metrics,
                trajectories);

        TransitionRuleEvaluationSnapshot saved = historyPort.save(snapshot).block();
        TransitionRuleEvaluationSnapshot recovered =
                historyPort.findById(saved.id()).block();

        assertThat(recovered).isNotNull();
        assertThat(recovered.displacementMetrics()).isEqualTo(metrics);
        assertThat(recovered.trajectoryAnalysis()).isEqualTo(trajectories);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM transition_rule_evaluation_singer_distances
                WHERE evaluation_id = ?
                """, Integer.class, saved.id()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM transition_rule_evaluation_trajectory_crossings
                WHERE evaluation_id = ?
                """, Integer.class, saved.id()))
                .isEqualTo(1);
    }
}

package com.rubengarcia.choir.transition.adapter.out.persistence;

import com.rubengarcia.choir.transition.application.port.out.TransitionPlanningPositionRepository;
import com.rubengarcia.choir.transition.domain.TransitionPlanningPosition;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;

@Repository
public class R2dbcTransitionPlanningPositionRepository
        implements TransitionPlanningPositionRepository {

    private final DatabaseClient database;

    public R2dbcTransitionPlanningPositionRepository(DatabaseClient database) {
        this.database = database;
    }

    @Override
    public Flux<TransitionPlanningPosition> findByTransitionVersionId(
            long transitionVersionId) {
        return database.sql("""
                SELECT
                    tv.id AS transition_version_id,
                    'FROM' AS stage,
                    fp.formation_version_id,
                    fp.singer_id,
                    fp.singer_name_snapshot,
                    fp.voice_part_snapshot,
                    fp.voice_section_id_snapshot,
                    fp.voice_section_name_snapshot,
                    fp.division_number_snapshot,
                    fp.position_label,
                    fp.x_coordinate,
                    fp.y_coordinate
                FROM transition_versions tv
                JOIN formation_positions fp
                  ON fp.formation_version_id = tv.from_formation_version_id
                WHERE tv.id = :transitionVersionId

                UNION ALL

                SELECT
                    tv.id AS transition_version_id,
                    'TO' AS stage,
                    fp.formation_version_id,
                    fp.singer_id,
                    fp.singer_name_snapshot,
                    fp.voice_part_snapshot,
                    fp.voice_section_id_snapshot,
                    fp.voice_section_name_snapshot,
                    fp.division_number_snapshot,
                    fp.position_label,
                    fp.x_coordinate,
                    fp.y_coordinate
                FROM transition_versions tv
                JOIN formation_positions fp
                  ON fp.formation_version_id = tv.to_formation_version_id
                WHERE tv.id = :transitionVersionId

                ORDER BY stage, formation_version_id, singer_name_snapshot
                """)
                .bind("transitionVersionId", transitionVersionId)
                .map((row, metadata) -> new TransitionPlanningPosition(
                        row.get("transition_version_id", Long.class),
                        row.get("stage", String.class),
                        row.get("formation_version_id", Long.class),
                        row.get("singer_id", Long.class),
                        row.get("singer_name_snapshot", String.class),
                        row.get("voice_part_snapshot", String.class),
                        row.get("voice_section_id_snapshot", Long.class),
                        row.get("voice_section_name_snapshot", String.class),
                        row.get("division_number_snapshot", Integer.class),
                        row.get("position_label", String.class),
                        row.get("x_coordinate", BigDecimal.class),
                        row.get("y_coordinate", BigDecimal.class)))
                .all();
    }
}

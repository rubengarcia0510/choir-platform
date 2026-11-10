package com.rubengarcia.choir.transition;

import static org.assertj.core.api.Assertions.assertThat;

import com.rubengarcia.choir.transition.application.port.out.ConcertOptimizationPreferencesPort;
import com.rubengarcia.choir.transition.domain.ConcertOptimizationPreferences;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class ConcertOptimizationPreferencesH2Tests {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ConcertOptimizationPreferencesPort preferencesPort;

    @Test
    void insertsUpdatesAndRecoversConcertPreferences() {
        long concertId = createConcert();

        ConcertOptimizationPreferences first =
                new ConcertOptimizationPreferences(40, 30, 20, 10);

        ConcertOptimizationPreferences saved =
                preferencesPort.save(concertId, first).block();

        assertThat(saved).isEqualTo(first);
        assertThat(preferencesPort.findByConcertId(concertId).block())
                .isEqualTo(first);

        ConcertOptimizationPreferences updated =
                new ConcertOptimizationPreferences(10, 20, 30, 40);

        ConcertOptimizationPreferences updatedResult =
                preferencesPort.save(concertId, updated).block();

        assertThat(updatedResult).isEqualTo(updated);
        assertThat(preferencesPort.findByConcertId(concertId).block())
                .isEqualTo(updated);

        Integer rows = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM concert_optimization_preferences
                WHERE concert_id = ?
                """, Integer.class, concertId);

        assertThat(rows).isEqualTo(1);
    }

    @Test
    void returnsEmptyWhenConcertHasNoStoredPreferences() {
        long concertId = createConcert();

        assertThat(preferencesPort.findByConcertId(concertId).block())
                .isNull();

        Integer rows = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM concert_optimization_preferences
                WHERE concert_id = ?
                """, Integer.class, concertId);

        assertThat(rows).isZero();
    }

    private long createConcert() {
        String unique = UUID.randomUUID().toString();

        jdbc.update("""
                INSERT INTO directors (display_name, email)
                VALUES (?, ?)
                """, "Preferences H2 test", "preferences-" + unique + "@example.test");

        Long directorId = jdbc.queryForObject("""
                SELECT id FROM directors WHERE email = ?
                """, Long.class, "preferences-" + unique + "@example.test");

        jdbc.update("""
                INSERT INTO choirs (director_id, name)
                VALUES (?, ?)
                """, directorId, "Preferences H2 choir " + unique);

        Long choirId = jdbc.queryForObject("""
                SELECT id FROM choirs
                WHERE director_id = ? AND name = ?
                """, Long.class, directorId, "Preferences H2 choir " + unique);

        jdbc.update("""
                INSERT INTO concerts (choir_id, title, status)
                VALUES (?, ?, 'PLANNED')
                """, choirId, "Preferences H2 concert " + unique);

        return jdbc.queryForObject("""
                SELECT id FROM concerts
                WHERE choir_id = ? AND title = ?
                """, Long.class, choirId, "Preferences H2 concert " + unique);
    }
}

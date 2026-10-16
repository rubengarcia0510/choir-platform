package com.rubengarcia.choir.domain;

import com.rubengarcia.choir.repository.ConcertRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ConcertPlannedDefaultIntegrationTests {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ConcertRepository concerts;

    private Long directorId;
    private Long choirId;

    @AfterEach
    void cleanup() {
        if (choirId != null) {
            jdbc.update("DELETE FROM concerts WHERE choir_id = ?", choirId);
            jdbc.update("DELETE FROM choirs WHERE id = ?", choirId);
        }
        if (directorId != null) {
            jdbc.update("DELETE FROM directors WHERE id = ?", directorId);
        }
    }

    @Test
    void concertSavedWithoutExplicitStatusIsPersistedAsPlanned() {
        String suffix = UUID.randomUUID().toString();
        String email = "concert-" + suffix + "@example.test";
        String choirName = "Coro " + suffix;

        jdbc.update(
                "INSERT INTO directors (display_name, email) VALUES (?, ?)",
                "Director de prueba", email);

        directorId = jdbc.queryForObject(
                "SELECT id FROM directors WHERE email = ?",
                Long.class, email);

        jdbc.update(
                "INSERT INTO choirs (director_id, name) VALUES (?, ?)",
                directorId, choirName);

        choirId = jdbc.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ? AND name = ?",
                Long.class, directorId, choirName);

        Concert concert = new Concert();
        concert.setChoirId(choirId);
        concert.setTitle("Concierto " + suffix);

        assertThat(concert.getStatus()).isEqualTo("PLANNED");

        Concert saved = concerts.save(concert)
                .block(Duration.ofSeconds(10));

        assertThat(saved).isNotNull();
        assertThat(saved.getStatus()).isEqualTo("PLANNED");

        String persistedStatus = jdbc.queryForObject(
                "SELECT status FROM concerts WHERE id = ?",
                String.class, saved.getId());

        assertThat(persistedStatus).isEqualTo("PLANNED");
    }
}

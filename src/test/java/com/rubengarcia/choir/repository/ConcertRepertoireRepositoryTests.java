package com.rubengarcia.choir.repository;

import com.rubengarcia.choir.domain.ConcertRepertoire;
import com.rubengarcia.choir.domain.ConcertRepertoireId;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ConcertRepertoireRepositoryTests {

    private final JdbcTemplate jdbcTemplate;
    private final ConcertRepertoireRepository repository;

    ConcertRepertoireRepositoryTests(
            JdbcTemplate jdbcTemplate,
            ConcertRepertoireRepository repository) {
        this.jdbcTemplate = jdbcTemplate;
        this.repository = repository;
    }

    @Test
    void compositeKeySupportsInsertFindOrderUpdateAndDelete() {
        String suffix = UUID.randomUUID().toString();
        Long directorId = null;
        Long choirId = null;
        Long concertId = null;
        Long firstItemId = null;
        Long secondItemId = null;

        try {
            jdbcTemplate.update("""
                    INSERT INTO directors (display_name, email)
                    VALUES (?, ?)
                    """, "Director test", suffix + "@example.test");

            directorId = jdbcTemplate.queryForObject(
                    "SELECT id FROM directors WHERE email = ?",
                    Long.class, suffix + "@example.test");

            jdbcTemplate.update("""
                    INSERT INTO choirs (director_id, name)
                    VALUES (?, ?)
                    """, directorId, "Coro " + suffix);

            choirId = jdbcTemplate.queryForObject(
                    "SELECT id FROM choirs WHERE director_id = ? AND name = ?",
                    Long.class, directorId, "Coro " + suffix);

            jdbcTemplate.update("""
                    INSERT INTO concerts (choir_id, title, status)
                    VALUES (?, ?, 'PLANNED')
                    """, choirId, "Concierto " + suffix);

            concertId = jdbcTemplate.queryForObject(
                    "SELECT id FROM concerts WHERE choir_id = ? AND title = ?",
                    Long.class, choirId, "Concierto " + suffix);

            jdbcTemplate.update("""
                    INSERT INTO repertoire_items (choir_id, title)
                    VALUES (?, ?)
                    """, choirId, "Obra A " + suffix);

            firstItemId = jdbcTemplate.queryForObject(
                    "SELECT id FROM repertoire_items WHERE choir_id = ? AND title = ?",
                    Long.class, choirId, "Obra A " + suffix);

            jdbcTemplate.update("""
                    INSERT INTO repertoire_items (choir_id, title)
                    VALUES (?, ?)
                    """, choirId, "Obra B " + suffix);

            secondItemId = jdbcTemplate.queryForObject(
                    "SELECT id FROM repertoire_items WHERE choir_id = ? AND title = ?",
                    Long.class, choirId, "Obra B " + suffix);

            ConcertRepertoire first =
                    new ConcertRepertoire(concertId, firstItemId, 10);
            ConcertRepertoire second =
                    new ConcertRepertoire(concertId, secondItemId, 20);

            ConcertRepertoire insertedFirst = repository.insert(first).block();
            ConcertRepertoire insertedSecond = repository.insert(second).block();

            assertThat(insertedFirst).isNotNull();
            assertThat(insertedFirst.getConcertId()).isEqualTo(concertId);
            assertThat(insertedFirst.getRepertoireItemId()).isEqualTo(firstItemId);
            assertThat(insertedFirst.getOrderIndex()).isEqualTo(10);
            assertThat(insertedFirst.getChoirId()).isEqualTo(choirId);

            assertThat(insertedSecond).isNotNull();
            assertThat(insertedSecond.getConcertId()).isEqualTo(concertId);
            assertThat(insertedSecond.getRepertoireItemId()).isEqualTo(secondItemId);
            assertThat(insertedSecond.getOrderIndex()).isEqualTo(20);
            assertThat(insertedSecond.getChoirId()).isEqualTo(choirId);

            ConcertRepertoireId firstKey =
                    new ConcertRepertoireId(concertId, firstItemId);

            ConcertRepertoire found = repository.findById(firstKey).block();

            assertThat(found).isNotNull();
            assertThat(found.getConcertId()).isEqualTo(concertId);
            assertThat(found.getRepertoireItemId()).isEqualTo(firstItemId);
            assertThat(found.getOrderIndex()).isEqualTo(10);

            List<ConcertRepertoire> ordered =
                    repository.findByConcertId(concertId).collectList().block();

            assertThat(ordered)
                    .extracting(ConcertRepertoire::getRepertoireItemId)
                    .containsExactly(firstItemId, secondItemId);

            assertThat(repository.updateOrderIndex(firstKey, 0).block())
                    .isEqualTo(1L);

            assertThat(repository.findById(firstKey).block().getOrderIndex())
                    .isZero();

            assertThat(repository.deleteById(firstKey).block())
                    .isEqualTo(1L);

            assertThat(repository.findById(firstKey).block()).isNull();
        } finally {
            if (concertId != null) {
                jdbcTemplate.update(
                        "DELETE FROM concert_repertoire WHERE concert_id = ?", concertId);
            }
            if (firstItemId != null) {
                jdbcTemplate.update(
                        "DELETE FROM concert_repertoire WHERE repertoire_item_id = ?",
                        firstItemId);
            }
            if (secondItemId != null) {
                jdbcTemplate.update(
                        "DELETE FROM concert_repertoire WHERE repertoire_item_id = ?",
                        secondItemId);
            }
            if (concertId != null) {
                jdbcTemplate.update("DELETE FROM concerts WHERE id = ?", concertId);
            }
            if (firstItemId != null) {
                jdbcTemplate.update(
                        "DELETE FROM repertoire_items WHERE id = ?", firstItemId);
            }
            if (secondItemId != null) {
                jdbcTemplate.update(
                        "DELETE FROM repertoire_items WHERE id = ?", secondItemId);
            }
            if (choirId != null) {
                jdbcTemplate.update("DELETE FROM choirs WHERE id = ?", choirId);
            }
            if (directorId != null) {
                jdbcTemplate.update("DELETE FROM directors WHERE id = ?", directorId);
            }
        }
    }
}

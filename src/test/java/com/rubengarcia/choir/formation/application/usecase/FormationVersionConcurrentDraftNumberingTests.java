package com.rubengarcia.choir.formation.application.usecase;

import com.rubengarcia.choir.formation.domain.FormationVersion;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FormationVersionConcurrentDraftNumberingTests {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private FormationVersionLifecycleUseCase useCase;

    @Test
    void assignsDifferentSequentialNumbersToConcurrentDrafts() throws Exception {
        Fixture fixture = fixture();

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            CompletableFuture<FormationVersion> first = CompletableFuture.supplyAsync(
                    () -> {
                        awaitStart(start);
                        return useCase.createDraft(
                                fixture.email(), fixture.choirId(),
                                fixture.formationId(), "Borrador concurrente A")
                                .block(TIMEOUT);
                    }, executor);

            CompletableFuture<FormationVersion> second = CompletableFuture.supplyAsync(
                    () -> {
                        awaitStart(start);
                        return useCase.createDraft(
                                fixture.email(), fixture.choirId(),
                                fixture.formationId(), "Borrador concurrente B")
                                .block(TIMEOUT);
                    }, executor);

            start.countDown();

            CompletableFuture.allOf(first, second).get(30, TimeUnit.SECONDS);

            assertThat(first.join()).isNotNull();
            assertThat(second.join()).isNotNull();
            assertThat(first.join().getId()).isNotEqualTo(second.join().getId());

            assertThat(jdbc.queryForList("""
                    SELECT version_number
                    FROM formation_versions
                    WHERE formation_id = ?
                    ORDER BY version_number
                    """, Integer.class, fixture.formationId()))
                    .containsExactly(1, 2);

            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(DISTINCT version_number)
                    FROM formation_versions
                    WHERE formation_id = ?
                    """, Integer.class, fixture.formationId()))
                    .isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString();
        String email = suffix + "@example.test";
        String choirName = "Coro " + suffix;
        String formationName = "Formación " + suffix;

        jdbc.update("""
                INSERT INTO directors (display_name, email)
                VALUES ('Director de prueba', ?)
                """, email);

        Long directorId = jdbc.queryForObject(
                "SELECT id FROM directors WHERE email = ?",
                Long.class, email);

        jdbc.update("""
                INSERT INTO choirs (director_id, name)
                VALUES (?, ?)
                """, directorId, choirName);

        Long choirId = jdbc.queryForObject("""
                SELECT id FROM choirs
                WHERE director_id = ? AND name = ?
                """, Long.class, directorId, choirName);

        jdbc.update("""
                INSERT INTO formations (choir_id, name)
                VALUES (?, ?)
                """, choirId, formationName);

        Long formationId = jdbc.queryForObject("""
                SELECT id FROM formations
                WHERE choir_id = ? AND name = ?
                """, Long.class, choirId, formationName);

        return new Fixture(email, choirId, formationId);
    }

    private static void awaitStart(CountDownLatch start) {
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "No se liberó la barrera de concurrencia");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrumpido al iniciar la prueba concurrente", exception);
        }
    }

    private record Fixture(String email, long choirId, long formationId) {
    }
}

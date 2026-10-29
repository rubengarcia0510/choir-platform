package com.rubengarcia.choir.formation.application.usecase;

import com.rubengarcia.choir.formation.domain.FormationVersion;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class FormationVersionOwnershipTests {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private FormationVersionLifecycleUseCase useCase;

    @Test
    void doesNotPublishVersionBelongingToAnotherFormation() {
        Fixture fixture = fixture();

        FormationVersion foreignDraft = useCase.createDraft(
                fixture.email(), fixture.choirId(),
                fixture.secondFormationId(), "Borrador de otra formación")
                .block(TIMEOUT);

        assertThat(foreignDraft).isNotNull();
        assertThat(foreignDraft.getFormationId())
                .isEqualTo(fixture.secondFormationId());

        assertThatThrownBy(() -> useCase.publish(
                fixture.email(), fixture.choirId(),
                fixture.firstFormationId(), foreignDraft.getId())
                .block(TIMEOUT))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Versión inexistente o fuera de la formación");

        assertThat(jdbc.queryForObject("""
                SELECT status
                FROM formation_versions
                WHERE id = ?
                """, String.class, foreignDraft.getId()))
                .isEqualTo("DRAFT");

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM formation_versions
                WHERE formation_id = ?
                  AND status = 'PUBLISHED'
                """, Integer.class, fixture.firstFormationId()))
                .isZero();
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString();
        String email = suffix + "@example.test";
        String choirName = "Coro " + suffix;
        String firstFormationName = "Formación A " + suffix;
        String secondFormationName = "Formación B " + suffix;

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
                """, choirId, firstFormationName);

        Long firstFormationId = jdbc.queryForObject("""
                SELECT id FROM formations
                WHERE choir_id = ? AND name = ?
                """, Long.class, choirId, firstFormationName);

        jdbc.update("""
                INSERT INTO formations (choir_id, name)
                VALUES (?, ?)
                """, choirId, secondFormationName);

        Long secondFormationId = jdbc.queryForObject("""
                SELECT id FROM formations
                WHERE choir_id = ? AND name = ?
                """, Long.class, choirId, secondFormationName);

        return new Fixture(
                email, choirId, firstFormationId, secondFormationId);
    }

    private record Fixture(
            String email,
            long choirId,
            long firstFormationId,
            long secondFormationId) {
    }
}

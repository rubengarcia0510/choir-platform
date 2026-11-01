package com.rubengarcia.choir;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.TestConstructor;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ChoirDomainMigrationTests {

    private final JdbcTemplate jdbcTemplate;
    private final List<Long> createdDirectorIds = new ArrayList<>();

    ChoirDomainMigrationTests(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @AfterEach
    void cleanup() {
        for (Long directorId : createdDirectorIds) {
            deleteDirectorData(directorId);
        }
        createdDirectorIds.clear();
    }

    @Test
    void migrationCreatesAllDomainTables() {
        List<String> tables = jdbcTemplate.execute(
                (org.springframework.jdbc.core.ConnectionCallback<List<String>>) connection -> {
                    List<String> names = new ArrayList<>();

                    try (java.sql.ResultSet resultSet =
                                 connection.getMetaData().getTables(
                                         connection.getCatalog(),
                                         null,
                                         "%",
                                         new String[]{"TABLE"})) {
                        while (resultSet.next()) {
                            String schema = resultSet.getString("TABLE_SCHEM");
                            if (schema != null && schema.equalsIgnoreCase("public")) {
                                names.add(resultSet.getString("TABLE_NAME")
                                        .toLowerCase(java.util.Locale.ROOT));
                            }
                        }
                    }
                    return names;
                });

        assertThat(tables).contains(
                "directors",
                "choirs",
                "singers",
                "concerts",
                "concert_cast_snapshots",
                "repertoire_items",
                "concert_repertoire",
                "formations",
                "formation_versions",
                "formation_positions",
                "transitions",
                "transition_versions",
                "absences"
        );
    }

    @Test
    void choirMustBelongToAnExistingDirector() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO choirs (director_id, name)
                VALUES (-999999, ?)
                """, "Orquesta " + UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void choirNameMustBeUniquePerDirector() {
        String email = UUID.randomUUID() + "@example.test";
        String choirName = "Coro " + UUID.randomUUID();

        jdbcTemplate.update(
                "INSERT INTO directors (display_name, email) VALUES (?, ?)",
                "Director de prueba", email);

        Long directorId = jdbcTemplate.queryForObject(
                "SELECT id FROM directors WHERE email = ?",
                Long.class, email);
        createdDirectorIds.add(directorId);

        jdbcTemplate.update(
                "INSERT INTO choirs (director_id, name) VALUES (?, ?)",
                directorId, choirName);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO choirs (director_id, name) VALUES (?, ?)",
                directorId, choirName))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void formationVersionNumberMustBePositive() {
        String email = UUID.randomUUID() + "@example.test";

        jdbcTemplate.update(
                "INSERT INTO directors (display_name, email) VALUES (?, ?)",
                "Director de prueba", email);

        Long directorId = jdbcTemplate.queryForObject(
                "SELECT id FROM directors WHERE email = ?",
                Long.class, email);
        createdDirectorIds.add(directorId);

        jdbcTemplate.update(
                "INSERT INTO choirs (director_id, name) VALUES (?, ?)",
                directorId, "Coro " + UUID.randomUUID());

        Long choirId = jdbcTemplate.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ?",
                Long.class, directorId);

        jdbcTemplate.update(
                "INSERT INTO formations (choir_id, name) VALUES (?, ?)",
                choirId, "Formación " + UUID.randomUUID());

        Long formationId = jdbcTemplate.queryForObject(
                "SELECT id FROM formations WHERE choir_id = ?",
                Long.class, choirId);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO formation_versions
                    (formation_id, version_number, status)
                VALUES (?, 0, 'DRAFT')
                """, formationId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }


    @Test
    void legacyFormationCanRemainWithoutConcertRepertoireAssociation() {
        Long choirId = createChoir();
        String name = "Formación legacy " + UUID.randomUUID();

        Long formationId = createFormation(choirId, name, null, null);

        java.util.Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT concert_id, repertoire_item_id FROM formations WHERE id = ?",
                formationId);

        assertThat(row.get("concert_id")).isNull();
        assertThat(row.get("repertoire_item_id")).isNull();
    }


    @Test
    void preexistingFormationSurvivesV18MigrationWithoutAssociation() {
        String databaseProduct = jdbcTemplate.execute(
                (org.springframework.jdbc.core.ConnectionCallback<String>) connection ->
                        connection.getMetaData().getDatabaseProductName());

        Assumptions.assumeTrue(
                "PostgreSQL".equalsIgnoreCase(databaseProduct),
                "Esta prueba de migración aislada requiere PostgreSQL");

        String schema = "choir_v18_legacy_"
                + UUID.randomUUID().toString().replace("-", "");

        try {
            jdbcTemplate.execute("CREATE SCHEMA " + schema);

            Flyway.configure()
                    .dataSource(jdbcTemplate.getDataSource())
                    .schemas(schema)
                    .defaultSchema(schema)
                    .locations("classpath:db/migration")
                    .target("17")
                    .load()
                    .migrate();

            String prefix = schema + ".";
            jdbcTemplate.update(
                    "INSERT INTO " + prefix
                            + "directors (display_name, email) VALUES (?, ?)",
                    "Director legacy", UUID.randomUUID() + "@example.test");

            Long directorId = jdbcTemplate.queryForObject(
                    "SELECT id FROM " + prefix
                            + "directors ORDER BY id DESC LIMIT 1",
                    Long.class);

            jdbcTemplate.update(
                    "INSERT INTO " + prefix
                            + "choirs (director_id, name) VALUES (?, ?)",
                    directorId, "Coro legacy " + UUID.randomUUID());

            Long choirId = jdbcTemplate.queryForObject(
                    "SELECT id FROM " + prefix
                            + "choirs WHERE director_id = ?",
                    Long.class, directorId);

            String formationName = "Formación pre-V18 " + UUID.randomUUID();

            jdbcTemplate.update(
                    "INSERT INTO " + prefix
                            + "formations (choir_id, name) VALUES (?, ?)",
                    choirId, formationName);

            Long formationId = jdbcTemplate.queryForObject(
                    "SELECT id FROM " + prefix
                            + "formations WHERE choir_id = ? AND name = ?",
                    Long.class, choirId, formationName);

            Flyway.configure()
                    .dataSource(jdbcTemplate.getDataSource())
                    .schemas(schema)
                    .defaultSchema(schema)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            java.util.Map<String, Object> row = jdbcTemplate.queryForMap("""
                    SELECT id, concert_id, repertoire_item_id
                    FROM %sformations
                    WHERE id = ?
                    """.formatted(prefix), formationId);

            assertThat(((Number) row.get("id")).longValue())
                    .isEqualTo(formationId);
            assertThat(row.get("concert_id")).isNull();
            assertThat(row.get("repertoire_item_id")).isNull();
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void auditIntegrityAndTransitionPublicationHistoryRemainImmutable() {
        String schema = "choir_audit_integrity_"
                + UUID.randomUUID().toString().replace("-", "");
        SingleConnectionDataSource isolatedDataSource = null;
        java.sql.Connection isolatedConnection = null;
        String originalSchema = null;

        try {
            String databaseProduct = jdbcTemplate.execute(
                    (org.springframework.jdbc.core.ConnectionCallback<String>) connection ->
                            connection.getMetaData().getDatabaseProductName());
            if (!"PostgreSQL".equalsIgnoreCase(databaseProduct)) {
                schema = schema.toUpperCase(java.util.Locale.ROOT);
            }
            jdbcTemplate.execute("CREATE SCHEMA " + schema);
            isolatedConnection = jdbcTemplate.getDataSource().getConnection();
            originalSchema = isolatedConnection.getSchema();
            isolatedConnection.setSchema(schema);
            isolatedDataSource = new SingleConnectionDataSource(isolatedConnection, true);
            JdbcTemplate isolatedJdbc = new JdbcTemplate(isolatedDataSource);
            List<String> locations = new ArrayList<>(List.of("classpath:db/migration"));
            if ("PostgreSQL".equalsIgnoreCase(databaseProduct)) {
                locations.add("classpath:db/migration-postgresql");
            } else {
                locations.add("classpath:db/migration-h2");
            }

            Flyway.configure()
                    .dataSource(isolatedDataSource)
                    .schemas(schema)
                    .defaultSchema(schema)
                    .locations(locations.toArray(String[]::new))
                    .load()
                    .migrate();

            Long choirId = createChoir(isolatedJdbc);
            Long fromVersionA = createFormationVersion(isolatedJdbc, choirId);
            Long toVersionA = createFormationVersion(isolatedJdbc, choirId);
            Long fromVersionB = createFormationVersion(isolatedJdbc, choirId);
            Long toVersionB = createFormationVersion(isolatedJdbc, choirId);
            Long transitionVersionA = createTransitionVersion(
                    isolatedJdbc, choirId, fromVersionA, toVersionA);
            Long transitionVersionB = createTransitionVersion(
                    isolatedJdbc, choirId, fromVersionB, toVersionB);
            Long transitionA = transitionIdForVersion(isolatedJdbc, transitionVersionA);
            Long transitionB = transitionIdForVersion(isolatedJdbc, transitionVersionB);
            Long repertoireItemId = createRepertoireItem(isolatedJdbc, choirId);
            Long evaluationA = createEvaluation(
                    isolatedJdbc, transitionVersionA, transitionA,
                    fromVersionA, toVersionA, repertoireItemId);
            Long evaluationB = createEvaluation(
                    isolatedJdbc, transitionVersionB, transitionB,
                    fromVersionB, toVersionB, repertoireItemId);

            assertThatThrownBy(() -> createEvaluation(
                    isolatedJdbc, transitionVersionA, transitionB,
                    fromVersionB, toVersionB, repertoireItemId))
                    .isInstanceOf(DataIntegrityViolationException.class);

            isolatedJdbc.update("""
                    INSERT INTO transition_rule_evaluation_warnings (
                        evaluation_id, result_order, warning_order,
                        code, explanation, status
                    ) VALUES (?, NULL, 0, 'STAGE_SEPARATION',
                              'Advertencia de prueba', 'VIOLATED')
                    """, evaluationA);

            assertThatThrownBy(() -> isolatedJdbc.update("""
                    INSERT INTO transition_publication_evidence (
                        transition_version_id, evaluation_id, transition_id,
                        from_formation_version_id, to_formation_version_id,
                        separation_settings_revision, global_separation_threshold,
                        published_by_email
                    ) VALUES (?, ?, ?, ?, ?, 1, 0.0500000, 'director@example.test')
                    """, transitionVersionA, evaluationB, transitionA,
                    fromVersionA, toVersionA))
                    .isInstanceOf(DataIntegrityViolationException.class);

            assertThatThrownBy(() -> insertPublicationDecision(
                    isolatedJdbc, evaluationB, transitionVersionA, -1, 0))
                    .isInstanceOf(DataIntegrityViolationException.class);

            assertThatThrownBy(() -> insertPublicationDecision(
                    isolatedJdbc, evaluationA, transitionVersionA, -1, 1))
                    .isInstanceOf(DataIntegrityViolationException.class);

            isolatedJdbc.update("""
                    INSERT INTO transition_publication_evidence (
                        transition_version_id, evaluation_id, transition_id,
                        from_formation_version_id, to_formation_version_id,
                        separation_settings_revision, global_separation_threshold,
                        published_by_email
                    ) VALUES (?, ?, ?, ?, ?, 1, 0.0500000, 'director@example.test')
                    """, transitionVersionA, evaluationA, transitionA,
                    fromVersionA, toVersionA);
            insertPublicationDecision(
                    isolatedJdbc, evaluationA, transitionVersionA, -1, 0);

            assertThat(isolatedJdbc.queryForObject("""
                    SELECT COUNT(*) FROM transition_publication_evidence
                    WHERE transition_version_id = ? AND evaluation_id = ?
                    """, Integer.class, transitionVersionA, evaluationA)).isEqualTo(1);
            assertThat(isolatedJdbc.queryForObject("""
                    SELECT COUNT(*) FROM transition_publication_decisions
                    WHERE evaluation_id = ? AND transition_version_id = ?
                      AND result_order = -1 AND warning_order = 0
                    """, Integer.class, evaluationA, transitionVersionA)).isEqualTo(1);

            if ("PostgreSQL".equalsIgnoreCase(databaseProduct)) {
                assertPostgresqlPublicationHistoryIsImmutable(
                        isolatedJdbc, evaluationA, transitionVersionA);
            }
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException(
                    "No se pudo preparar el esquema aislado de integridad", exception);
        } finally {
            try {
                if (isolatedConnection != null && originalSchema != null) {
                    isolatedConnection.setSchema(originalSchema);
                }
            } catch (java.sql.SQLException exception) {
                throw new IllegalStateException(
                        "No se pudo restaurar el esquema de la conexión de prueba", exception);
            } finally {
                if (isolatedDataSource != null) {
                    isolatedDataSource.destroy();
                }
            }
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    private void assertPostgresqlPublicationHistoryIsImmutable(
            JdbcTemplate jdbc, Long evaluationId, Long transitionVersionId) {
        assertThatThrownBy(() -> jdbc.update("""
                UPDATE transition_rule_evaluations
                SET status = 'VIOLATED'
                WHERE id = ?
                """, evaluationId))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbc.update("""
                UPDATE transition_rule_evaluation_warnings
                SET explanation = 'Advertencia modificada'
                WHERE evaluation_id = ? AND warning_order = 0
                """, evaluationId))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbc.update("""
                UPDATE transition_publication_evidence
                SET published_by_email = 'changed@example.test'
                WHERE transition_version_id = ?
                """, transitionVersionId))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbc.update("""
                UPDATE transition_publication_decisions
                SET reason = 'Motivo modificado'
                WHERE evaluation_id = ? AND transition_version_id = ?
                """, evaluationId, transitionVersionId))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbc.update("""
                DELETE FROM transition_publication_decisions
                WHERE evaluation_id = ? AND transition_version_id = ?
                """, evaluationId, transitionVersionId))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbc.update("""
                DELETE FROM transition_publication_evidence
                WHERE transition_version_id = ?
                """, transitionVersionId))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbc.update("""
                DELETE FROM transition_rule_evaluation_warnings
                WHERE evaluation_id = ? AND warning_order = 0
                """, evaluationId))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbc.update("""
                DELETE FROM transition_rule_evaluations
                WHERE id = ?
                """, evaluationId))
                .isInstanceOf(DataAccessException.class);

        jdbc.update("""
                UPDATE transition_versions
                SET status = 'PUBLISHED', published_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, transitionVersionId);

        assertThatThrownBy(() -> jdbc.update("""
                UPDATE transition_versions
                SET instructions = 'Instrucciones modificadas'
                WHERE id = ?
                """, transitionVersionId))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbc.update("""
                DELETE FROM transition_versions WHERE id = ?
                """, transitionVersionId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void upgradeFromPreV18PreservesPublishedAndSelectedHistoricalData() {
        String databaseProduct = jdbcTemplate.execute(
                (org.springframework.jdbc.core.ConnectionCallback<String>) connection ->
                        connection.getMetaData().getDatabaseProductName());

        Assumptions.assumeTrue(
                "PostgreSQL".equalsIgnoreCase(databaseProduct),
                "La prueba de upgrade histórico requiere PostgreSQL");

        String schema = "choir_upgrade_history_"
                + UUID.randomUUID().toString().replace("-", "");

        try {
            jdbcTemplate.execute("CREATE SCHEMA " + schema);
            Flyway.configure()
                    .dataSource(jdbcTemplate.getDataSource())
                    .schemas(schema)
                    .defaultSchema(schema)
                    .locations("classpath:db/migration")
                    .target("17")
                    .load()
                    .migrate();

            String prefix = schema + ".";
            String suffix = UUID.randomUUID().toString();
            String email = suffix + "@example.test";
            jdbcTemplate.update(
                    "INSERT INTO " + prefix
                            + "directors (display_name, email) VALUES (?, ?)",
                    "Director histórico", email);
            Long directorId = jdbcTemplate.queryForObject(
                    "SELECT id FROM " + prefix + "directors WHERE email = ?",
                    Long.class, email);
            jdbcTemplate.update(
                    "INSERT INTO " + prefix + "choirs (director_id, name) VALUES (?, ?)",
                    directorId, "Coro histórico " + suffix);
            Long choirId = jdbcTemplate.queryForObject(
                    "SELECT id FROM " + prefix + "choirs WHERE director_id = ?",
                    Long.class, directorId);

            jdbcTemplate.update(
                    "INSERT INTO " + prefix + "formations (choir_id, name) VALUES (?, ?)",
                    choirId, "Origen legacy " + suffix);
            Long fromFormationId = jdbcTemplate.queryForObject(
                    "SELECT id FROM " + prefix + "formations WHERE choir_id = ? AND name = ?",
                    Long.class, choirId, "Origen legacy " + suffix);
            jdbcTemplate.update(
                    "INSERT INTO " + prefix
                            + "formation_versions (formation_id, version_number, status) "
                            + "VALUES (?, 1, 'PUBLISHED')",
                    fromFormationId);
            Long fromVersionId = jdbcTemplate.queryForObject(
                    "SELECT id FROM " + prefix
                            + "formation_versions WHERE formation_id = ? AND version_number = 1",
                    Long.class, fromFormationId);

            jdbcTemplate.update(
                    "INSERT INTO " + prefix + "formations (choir_id, name) VALUES (?, ?)",
                    choirId, "Destino legacy " + suffix);
            Long toFormationId = jdbcTemplate.queryForObject(
                    "SELECT id FROM " + prefix + "formations WHERE choir_id = ? AND name = ?",
                    Long.class, choirId, "Destino legacy " + suffix);
            jdbcTemplate.update(
                    "INSERT INTO " + prefix
                            + "formation_versions (formation_id, version_number, status) "
                            + "VALUES (?, 1, 'PUBLISHED')",
                    toFormationId);
            Long toVersionId = jdbcTemplate.queryForObject(
                    "SELECT id FROM " + prefix
                            + "formation_versions WHERE formation_id = ? AND version_number = 1",
                    Long.class, toFormationId);

            jdbcTemplate.update(
                    "INSERT INTO " + prefix + "transitions (choir_id, name) VALUES (?, ?)",
                    choirId, "Transición publicada " + suffix);
            Long transitionId = jdbcTemplate.queryForObject(
                    "SELECT id FROM " + prefix + "transitions WHERE choir_id = ?",
                    Long.class, choirId);
            jdbcTemplate.update(
                    "INSERT INTO " + prefix + """
                            transition_versions (
                                transition_id, version_number,
                                from_formation_version_id, to_formation_version_id,
                                status
                            ) VALUES (?, 1, ?, ?, 'PUBLISHED')
                            """,
                    transitionId, fromVersionId, toVersionId);
            Long transitionVersionId = jdbcTemplate.queryForObject(
                    "SELECT id FROM " + prefix
                            + "transition_versions WHERE transition_id = ?",
                    Long.class, transitionId);

            jdbcTemplate.update(
                    "INSERT INTO " + prefix + "concerts (choir_id, title) VALUES (?, ?)",
                    choirId, "Concierto histórico " + suffix);
            Long concertId = jdbcTemplate.queryForObject(
                    "SELECT id FROM " + prefix + "concerts WHERE choir_id = ?",
                    Long.class, choirId);
            jdbcTemplate.update(
                    "INSERT INTO " + prefix
                            + "repertoire_items (choir_id, title) VALUES (?, ?)",
                    choirId, "Obra histórica " + suffix);
            Long repertoireItemId = jdbcTemplate.queryForObject(
                    "SELECT id FROM " + prefix + "repertoire_items WHERE choir_id = ?",
                    Long.class, choirId);
            jdbcTemplate.update(
                    "INSERT INTO " + prefix
                            + "concert_repertoire (concert_id, repertoire_item_id, order_index) "
                            + "VALUES (?, ?, 0)",
                    concertId, repertoireItemId);

            Flyway.configure()
                    .dataSource(jdbcTemplate.getDataSource())
                    .schemas(schema)
                    .defaultSchema(schema)
                    .locations(
                            "classpath:db/migration",
                            "classpath:db/migration-postgresql")
                    .target("29")
                    .load()
                    .migrate();

            jdbcTemplate.update(
                    "INSERT INTO " + prefix
                            + "formations (choir_id, name, concert_id, repertoire_item_id) "
                            + "VALUES (?, ?, ?, ?)",
                    choirId, "Formación seleccionada " + suffix, concertId, repertoireItemId);
            Long selectedFormationId = jdbcTemplate.queryForObject(
                    "SELECT id FROM " + prefix
                            + "formations WHERE choir_id = ? AND name = ?",
                    Long.class, choirId, "Formación seleccionada " + suffix);
            jdbcTemplate.update(
                    "INSERT INTO " + prefix
                            + "formation_versions (formation_id, version_number, status) "
                            + "VALUES (?, 1, 'PUBLISHED')",
                    selectedFormationId);
            Long selectedFormationVersionId = jdbcTemplate.queryForObject(
                    "SELECT id FROM " + prefix
                            + "formation_versions WHERE formation_id = ? AND version_number = 1",
                    Long.class, selectedFormationId);
            jdbcTemplate.update(
                    "UPDATE " + prefix + "concert_repertoire "
                            + "SET selected_formation_id = ?, "
                            + "selected_formation_version_id = ? "
                            + "WHERE concert_id = ? AND repertoire_item_id = ?",
                    selectedFormationId, selectedFormationVersionId,
                    concertId, repertoireItemId);
            jdbcTemplate.update(
                    "INSERT INTO " + prefix + """
                            formation_version_selection_history (
                                concert_id, repertoire_item_id,
                                selected_formation_id, selected_formation_version_id,
                                actor_email
                            ) VALUES (?, ?, ?, ?, ?)
                            """,
                    concertId, repertoireItemId, selectedFormationId,
                    selectedFormationVersionId, email);

            jdbcTemplate.update(
                    "INSERT INTO " + prefix + """
                            transition_rule_evaluations (
                                transition_version_id, transition_id,
                                from_formation_version_id, to_formation_version_id,
                                repertoire_item_id, separation_settings_revision,
                                global_separation_threshold, status
                            ) VALUES (?, ?, ?, ?, ?, 1, 0.0500000, 'VIOLATED')
                            """,
                    transitionVersionId, transitionId, fromVersionId, toVersionId,
                    repertoireItemId);
            Long evaluationId = jdbcTemplate.queryForObject(
                    "SELECT id FROM " + prefix
                            + "transition_rule_evaluations WHERE transition_version_id = ?",
                    Long.class, transitionVersionId);
            jdbcTemplate.update(
                    "INSERT INTO " + prefix + """
                            transition_rule_evaluation_warnings (
                                evaluation_id, result_order, warning_order,
                                code, explanation, status
                            ) VALUES (?, NULL, 0, 'STAGE_SEPARATION',
                                      'Advertencia legacy aceptada', 'VIOLATED')
                            """,
                    evaluationId);
            jdbcTemplate.update(
                    "INSERT INTO " + prefix + """
                            transition_publication_decisions (
                                evaluation_id, transition_version_id,
                                result_order, warning_order, warning_code,
                                warning_explanation, decision, director_email, reason
                            ) VALUES (?, ?, -1, 0, 'STAGE_SEPARATION',
                                      'Advertencia legacy aceptada', 'ACCEPTED', ?, ?)
                            """,
                    evaluationId, transitionVersionId, email, "Motivo histórico");
            jdbcTemplate.update(
                    "INSERT INTO " + prefix + """
                            transition_publication_evidence (
                                transition_version_id, evaluation_id, transition_id,
                                from_formation_version_id, to_formation_version_id,
                                separation_settings_revision, global_separation_threshold,
                                published_by_email
                            ) VALUES (?, ?, ?, ?, ?, 1, 0.0500000, ?)
                            """,
                    transitionVersionId, evaluationId, transitionId,
                    fromVersionId, toVersionId, email);

            Flyway.configure()
                    .dataSource(jdbcTemplate.getDataSource())
                    .schemas(schema)
                    .defaultSchema(schema)
                    .locations(
                            "classpath:db/migration",
                            "classpath:db/migration-postgresql")
                    .load()
                    .migrate();

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT concert_id FROM " + prefix + "formations WHERE id = ?",
                    Long.class, fromFormationId)).isNull();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM " + prefix + "formation_versions WHERE id = ?",
                    String.class, fromVersionId)).isEqualTo("PUBLISHED");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM " + prefix + "transition_versions WHERE id = ?",
                    String.class, transitionVersionId)).isEqualTo("PUBLISHED");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + prefix
                            + "transition_publication_evidence "
                            + "WHERE transition_version_id = ? AND evaluation_id = ?",
                    Integer.class, transitionVersionId, evaluationId)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + prefix
                            + "transition_publication_decisions "
                            + "WHERE evaluation_id = ? AND result_order = -1 "
                            + "AND warning_order = 0",
                    Integer.class, evaluationId)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT selected_formation_version_id FROM " + prefix
                            + "concert_repertoire WHERE concert_id = ? "
                            + "AND repertoire_item_id = ?",
                    Long.class, concertId, repertoireItemId))
                    .isEqualTo(selectedFormationVersionId);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + prefix
                            + "formation_version_selection_history "
                            + "WHERE concert_id = ? AND repertoire_item_id = ? "
                            + "AND selected_formation_version_id = ?",
                    Integer.class, concertId, repertoireItemId,
                    selectedFormationVersionId)).isEqualTo(1);
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void formationCanReferenceConcertRepertoireOccurrence() {
        Long choirId = createChoir();
        Long concertId = createConcert(choirId);
        Long repertoireItemId = createRepertoireItem(choirId);
        jdbcTemplate.update("""
                INSERT INTO concert_repertoire
                    (concert_id, repertoire_item_id, order_index)
                VALUES (?, ?, 0)
                """, concertId, repertoireItemId);

        Long formationId = createFormation(
                choirId, "Formación " + UUID.randomUUID(), concertId, repertoireItemId);

        java.util.Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT concert_id, repertoire_item_id
                FROM formations
                WHERE id = ?
                """, formationId);

        assertThat(((Number) row.get("concert_id")).longValue()).isEqualTo(concertId);
        assertThat(((Number) row.get("repertoire_item_id")).longValue())
                .isEqualTo(repertoireItemId);
    }

    @Test
    void formationAssociationMustHaveBothIdentifiersOrNeither() {
        Long choirId = createChoir();
        Long concertId = createConcert(choirId);

        assertThatThrownBy(() -> createFormation(
                choirId, "Formación incompleta " + UUID.randomUUID(), concertId, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void formationCannotReferenceWorkNotIncludedInConcert() {
        Long choirId = createChoir();
        Long concertId = createConcert(choirId);
        Long repertoireItemId = createRepertoireItem(choirId);

        assertThatThrownBy(() -> createFormation(
                choirId, "Formación sin inclusión " + UUID.randomUUID(),
                concertId, repertoireItemId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void formationCannotAssociateConcertAndWorkFromDifferentChoirs() {
        Long concertChoirId = createChoir();
        Long repertoireChoirId = createChoir();
        Long concertId = createConcert(concertChoirId);
        Long repertoireItemId = createRepertoireItem(repertoireChoirId);

        // La inclusión existe, pero el concierto y la obra pertenecen a coros distintos.
        jdbcTemplate.update("""
                INSERT INTO concert_repertoire
                    (concert_id, repertoire_item_id, order_index)
                VALUES (?, ?, 0)
                """, concertId, repertoireItemId);

        assertThatThrownBy(() -> createFormation(
                concertChoirId, "Formación incompatible " + UUID.randomUUID(),
                concertId, repertoireItemId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void multipleFormationsCanReferenceTheSameConcertRepertoireOccurrence() {
        Long choirId = createChoir();
        Long concertId = createConcert(choirId);
        Long repertoireItemId = createRepertoireItem(choirId);
        jdbcTemplate.update("""
                INSERT INTO concert_repertoire
                    (concert_id, repertoire_item_id, order_index)
                VALUES (?, ?, 0)
                """, concertId, repertoireItemId);

        createFormation(choirId, "Alternativa A " + UUID.randomUUID(),
                concertId, repertoireItemId);
        createFormation(choirId, "Alternativa B " + UUID.randomUUID(),
                concertId, repertoireItemId);

        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM formations
                WHERE choir_id = ?
                  AND concert_id = ?
                  AND repertoire_item_id = ?
                """, Integer.class, choirId, concertId, repertoireItemId);

        assertThat(count).isEqualTo(2);
    }

    private Long createChoir() {
        String email = UUID.randomUUID() + "@example.test";
        jdbcTemplate.update(
                "INSERT INTO directors (display_name, email) VALUES (?, ?)",
                "Director de prueba", email);

        Long directorId = jdbcTemplate.queryForObject(
                "SELECT id FROM directors WHERE email = ?",
                Long.class, email);
        createdDirectorIds.add(directorId);

        jdbcTemplate.update(
                "INSERT INTO choirs (director_id, name) VALUES (?, ?)",
                directorId, "Coro " + UUID.randomUUID());

        return jdbcTemplate.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ?",
                Long.class, directorId);
    }

    private Long createFormationVersion(JdbcTemplate jdbc, Long choirId) {
        String formationName = "Formación versionada " + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO formations (choir_id, name) VALUES (?, ?)
                """, choirId, formationName);
        Long formationId = jdbc.queryForObject("""
                SELECT id FROM formations WHERE choir_id = ? AND name = ?
                """, Long.class, choirId, formationName);
        jdbc.update("""
                INSERT INTO formation_versions (formation_id, version_number, status)
                VALUES (?, 1, 'PUBLISHED')
                """, formationId);
        return jdbc.queryForObject("""
                SELECT id FROM formation_versions
                WHERE formation_id = ? AND version_number = 1
                """, Long.class, formationId);
    }

    private Long createTransitionVersion(
            JdbcTemplate jdbc,
            Long choirId,
            Long fromFormationVersionId,
            Long toFormationVersionId) {
        jdbc.update(
                "INSERT INTO transitions (choir_id, name) VALUES (?, ?)",
                choirId, "Transición " + UUID.randomUUID());
        Long transitionId = jdbc.queryForObject("""
                SELECT id FROM transitions
                WHERE choir_id = ? ORDER BY id DESC LIMIT 1
                """, Long.class, choirId);
        jdbc.update("""
                INSERT INTO transition_versions (
                    transition_id, version_number,
                    from_formation_version_id, to_formation_version_id
                ) VALUES (?, 1, ?, ?)
                """, transitionId, fromFormationVersionId, toFormationVersionId);
        return jdbc.queryForObject("""
                SELECT id FROM transition_versions
                WHERE transition_id = ? AND version_number = 1
                """, Long.class, transitionId);
    }

    private Long transitionIdForVersion(
            JdbcTemplate jdbc, Long transitionVersionId) {
        return jdbc.queryForObject("""
                SELECT transition_id FROM transition_versions WHERE id = ?
                """, Long.class, transitionVersionId);
    }

    private Long createEvaluation(
            JdbcTemplate jdbc,
            Long transitionVersionId,
            Long transitionId,
            Long fromFormationVersionId,
            Long toFormationVersionId,
            Long repertoireItemId) {
        jdbc.update("""
                INSERT INTO transition_rule_evaluations (
                    transition_version_id, transition_id,
                    from_formation_version_id, to_formation_version_id,
                    repertoire_item_id, separation_settings_revision,
                    global_separation_threshold, status
                ) VALUES (?, ?, ?, ?, ?, 1, 0.0500000, 'SATISFIED')
                """, transitionVersionId, transitionId,
                fromFormationVersionId, toFormationVersionId, repertoireItemId);
        return jdbc.queryForObject("""
                SELECT id FROM transition_rule_evaluations
                WHERE transition_version_id = ?
                ORDER BY id DESC LIMIT 1
                """, Long.class, transitionVersionId);
    }

    private void insertPublicationDecision(
            JdbcTemplate jdbc,
            Long evaluationId,
            Long transitionVersionId,
            int resultOrder,
            int warningOrder) {
        jdbc.update("""
                INSERT INTO transition_publication_decisions (
                    evaluation_id, transition_version_id,
                    result_order, warning_order, warning_code,
                    warning_explanation, decision, director_email, reason
                ) VALUES (?, ?, ?, ?, 'STAGE_SEPARATION',
                          'Advertencia de prueba', 'ACCEPTED',
                          'director@example.test', 'Motivo de prueba')
                """, evaluationId, transitionVersionId, resultOrder, warningOrder);
    }

    private Long createChoir(JdbcTemplate jdbc) {
        String email = UUID.randomUUID() + "@example.test";
        jdbc.update(
                "INSERT INTO directors (display_name, email) VALUES (?, ?)",
                "Director de prueba", email);
        Long directorId = jdbc.queryForObject(
                "SELECT id FROM directors WHERE email = ?",
                Long.class, email);
        jdbc.update(
                "INSERT INTO choirs (director_id, name) VALUES (?, ?)",
                directorId, "Coro " + UUID.randomUUID());
        return jdbc.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ?",
                Long.class, directorId);
    }

    private Long createRepertoireItem(JdbcTemplate jdbc, Long choirId) {
        jdbc.update(
                "INSERT INTO repertoire_items (choir_id, title) VALUES (?, ?)",
                choirId, "Obra " + UUID.randomUUID());
        return jdbc.queryForObject("""
                SELECT id FROM repertoire_items
                WHERE choir_id = ?
                ORDER BY id DESC
                LIMIT 1
                """, Long.class, choirId);
    }

    private Long createConcert(Long choirId) {
        jdbcTemplate.update(
                "INSERT INTO concerts (choir_id, title) VALUES (?, ?)",
                choirId, "Concierto " + UUID.randomUUID());

        return jdbcTemplate.queryForObject("""
                SELECT id FROM concerts
                WHERE choir_id = ?
                ORDER BY id DESC
                LIMIT 1
                """, Long.class, choirId);
    }

    private Long createRepertoireItem(Long choirId) {
        jdbcTemplate.update(
                "INSERT INTO repertoire_items (choir_id, title) VALUES (?, ?)",
                choirId, "Obra " + UUID.randomUUID());

        return jdbcTemplate.queryForObject("""
                SELECT id FROM repertoire_items
                WHERE choir_id = ?
                ORDER BY id DESC
                LIMIT 1
                """, Long.class, choirId);
    }

    private Long createFormation(
            Long choirId,
            String name,
            Long concertId,
            Long repertoireItemId) {
        jdbcTemplate.update("""
                INSERT INTO formations
                    (choir_id, name, concert_id, repertoire_item_id)
                VALUES (?, ?, ?, ?)
                """, choirId, name, concertId, repertoireItemId);

        return jdbcTemplate.queryForObject("""
                SELECT id FROM formations
                WHERE choir_id = ? AND name = ?
                """, Long.class, choirId, name);
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
                DELETE FROM transition_publication_decisions
                WHERE transition_version_id IN (
                    SELECT tv.id FROM transition_versions tv
                    JOIN transitions t ON t.id = tv.transition_id
                    WHERE t.choir_id = ?
                )
                """, choirId);

        jdbcTemplate.update("""
                DELETE FROM transition_publication_evidence
                WHERE transition_version_id IN (
                    SELECT tv.id FROM transition_versions tv
                    JOIN transitions t ON t.id = tv.transition_id
                    WHERE t.choir_id = ?
                )
                """, choirId);

        jdbcTemplate.update("""
                DELETE FROM transition_rule_evaluation_warnings
                WHERE evaluation_id IN (
                    SELECT e.id FROM transition_rule_evaluations e
                    JOIN transitions t ON t.id = e.transition_id
                    WHERE t.choir_id = ?
                )
                """, choirId);

        jdbcTemplate.update("""
                DELETE FROM transition_rule_evaluation_results
                WHERE evaluation_id IN (
                    SELECT e.id FROM transition_rule_evaluations e
                    JOIN transitions t ON t.id = e.transition_id
                    WHERE t.choir_id = ?
                )
                """, choirId);

        jdbcTemplate.update("""
                DELETE FROM transition_rule_evaluations
                WHERE transition_id IN (
                    SELECT id FROM transitions WHERE choir_id = ?
                )
                """, choirId);

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

        jdbcTemplate.update(
                "DELETE FROM singers WHERE choir_id = ?",
                choirId);

        jdbcTemplate.update(
                "DELETE FROM transitions WHERE choir_id = ?",
                choirId);

        jdbcTemplate.update(
                "DELETE FROM choirs WHERE id = ?",
                choirId);
    }
}

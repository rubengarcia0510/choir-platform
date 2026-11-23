package com.rubengarcia.choir.formation.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rubengarcia.choir.formation.application.exception.FormationNotFoundException;
import com.rubengarcia.choir.formation.domain.FormationMobilityConstraint;
import com.rubengarcia.choir.formation.domain.MobilityRegion;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class FormationMobilityConstraintPersistenceAdapterTests {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final MobilityRegion REGION = new MobilityRegion(
            new BigDecimal("1.250"),
            new BigDecimal("8.750"),
            new BigDecimal("0.500"),
            new BigDecimal("12.000"));

    private static final MobilityRegion OTHER_REGION = new MobilityRegion(
            new BigDecimal("2.000"),
            new BigDecimal("3.000"),
            new BigDecimal("0.000"),
            new BigDecimal("5.000"));

    @Autowired
    private FormationMobilityConstraintPersistenceAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Fixture fixture;

    private final List<Fixture> createdFixtures = new ArrayList<>();

    @BeforeEach
    void setUp() {
        fixture = newFixture();
    }

    @AfterEach
    void cleanUpAll() {
        for (Fixture created : createdFixtures) {
            cleanUp(created);
        }
    }

    private Fixture newFixture() {
        Fixture created = createFixture();
        createdFixtures.add(created);
        return created;
    }

    private void cleanUp(Fixture fixture) {
        jdbcTemplate.update(
                "DELETE FROM formation_mobility_constraints WHERE choir_id = ?",
                fixture.choirId());
        jdbcTemplate.update(
                """
                DELETE FROM formation_positions
                WHERE formation_version_id IN (
                    SELECT v.id FROM formation_versions v
                    JOIN formations f ON f.id = v.formation_id
                    WHERE f.choir_id = ?
                )
                """,
                fixture.choirId());
        jdbcTemplate.update(
                """
                DELETE FROM formation_versions
                WHERE formation_id IN (
                    SELECT id FROM formations WHERE choir_id = ?
                )
                """,
                fixture.choirId());
        jdbcTemplate.update(
                "DELETE FROM formations WHERE choir_id = ?",
                fixture.choirId());
        jdbcTemplate.update(
                "DELETE FROM concert_vocal_assignments WHERE choir_id = ?",
                fixture.choirId());
        jdbcTemplate.update(
                """
                DELETE FROM concert_repertoire
                WHERE concert_id IN (
                    SELECT id FROM concerts WHERE choir_id = ?
                )
                """,
                fixture.choirId());
        jdbcTemplate.update(
                "DELETE FROM vocal_plan_divisions WHERE choir_id = ?",
                fixture.choirId());
        jdbcTemplate.update(
                "DELETE FROM vocal_plans WHERE choir_id = ?",
                fixture.choirId());
        jdbcTemplate.update(
                "DELETE FROM concerts WHERE choir_id = ?",
                fixture.choirId());
        jdbcTemplate.update(
                "DELETE FROM singers WHERE choir_id = ?",
                fixture.choirId());
        jdbcTemplate.update(
                "DELETE FROM vocal_sections WHERE choir_id = ?",
                fixture.choirId());
        jdbcTemplate.update(
                "DELETE FROM repertoire_items WHERE choir_id = ?",
                fixture.choirId());
        jdbcTemplate.update(
                "DELETE FROM choirs WHERE id = ?",
                fixture.choirId());
        jdbcTemplate.update(
                "DELETE FROM directors WHERE id = ?",
                fixture.directorId());
    }

    @Test
    void createsListsUpdatesAndDeletesConstraint() {
        FormationMobilityConstraint created = adapter.create(
                        fixture.choirId(),
                        fixture.concertId(),
                        fixture.repertoireItemId(),
                        fixture.formationId(),
                        fixture.singerOneId(),
                        REGION)
                .block(TIMEOUT);

        assertThat(created).isNotNull();
        assertThat(created.id()).isNotNull();
        assertThat(created.formationId()).isEqualTo(fixture.formationId());
        assertThat(created.singerId()).isEqualTo(fixture.singerOneId());
        assertThat(created.region()).isEqualTo(REGION);
        assertThat(created.active()).isTrue();
        assertThat(created.createdAt()).isNotNull();

        var listed = adapter.findForFormation(
                        fixture.choirId(),
                        fixture.concertId(),
                        fixture.repertoireItemId(),
                        fixture.formationId())
                .collectList()
                .block(TIMEOUT);
        assertThat(listed).containsExactly(created);

        MobilityRegion updatedRegion = new MobilityRegion(
                new BigDecimal("2.000"),
                new BigDecimal("3.000"),
                new BigDecimal("0.000"),
                new BigDecimal("5.000"));

        FormationMobilityConstraint updated = adapter.update(
                        fixture.choirId(),
                        fixture.concertId(),
                        fixture.repertoireItemId(),
                        fixture.formationId(),
                        created.id(),
                        updatedRegion,
                        false)
                .block(TIMEOUT);

        assertThat(updated).isNotNull();
        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.region()).isEqualTo(updatedRegion);
        assertThat(updated.active()).isFalse();

        assertThat(adapter.delete(
                        fixture.choirId(),
                        fixture.concertId(),
                        fixture.repertoireItemId(),
                        fixture.formationId(),
                        created.id())
                .block(TIMEOUT)).isTrue();
        assertThat(countConstraints(fixture.formationId())).isZero();
    }

    @Test
    void deletingMissingConstraintReturnsFalse() {
        assertThat(adapter.delete(
                        fixture.choirId(),
                        fixture.concertId(),
                        fixture.repertoireItemId(),
                        fixture.formationId(),
                        987_654_321L)
                .block(TIMEOUT)).isFalse();
    }

    @Test
    void doesNotReturnOrModifyConstraintsOfAnotherFormation() {
        long otherFormationId = insertFormation(
                fixture, "Formación alternativa " + UUID.randomUUID());
        Long otherVersionId = createDraftVersion(otherFormationId, 1);
        insertPosition(otherVersionId, fixture.singerOneId(), "Otra posición");

        FormationMobilityConstraint created = adapter.create(
                        fixture.choirId(),
                        fixture.concertId(),
                        fixture.repertoireItemId(),
                        fixture.formationId(),
                        fixture.singerOneId(),
                        REGION)
                .block(TIMEOUT);

        assertThat(adapter.findForFormation(
                        fixture.choirId(),
                        fixture.concertId(),
                        fixture.repertoireItemId(),
                        otherFormationId)
                .collectList()
                .block(TIMEOUT)).isEmpty();

        assertThat(adapter.update(
                        fixture.choirId(),
                        fixture.concertId(),
                        fixture.repertoireItemId(),
                        otherFormationId,
                        created.id(),
                        REGION,
                        false)
                .block(TIMEOUT)).isNull();

        assertThat(adapter.delete(
                        fixture.choirId(),
                        fixture.concertId(),
                        fixture.repertoireItemId(),
                        otherFormationId,
                        created.id())
                .block(TIMEOUT)).isFalse();

        assertThat(countConstraints(fixture.formationId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT active FROM formation_mobility_constraints WHERE id = ?",
                Boolean.class,
                created.id())).isTrue();
    }

    @Test
    void updateRejectsMismatchedConcertContext() {
        FormationMobilityConstraint created = adapter.create(
                        fixture.choirId(),
                        fixture.concertId(),
                        fixture.repertoireItemId(),
                        fixture.formationId(),
                        fixture.singerOneId(),
                        REGION)
                .block(TIMEOUT);

        assertThat(adapter.update(
                        fixture.choirId(),
                        987_654_321L,
                        fixture.repertoireItemId(),
                        fixture.formationId(),
                        created.id(),
                        REGION,
                        false)
                .block(TIMEOUT)).isNull();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT active FROM formation_mobility_constraints WHERE id = ?",
                Boolean.class,
                created.id())).isTrue();
    }

    @Test
    void rejectsCreationWhenFormationDoesNotMatchContext() {
        assertThatThrownBy(() -> adapter.create(
                        fixture.choirId(),
                        987_654_321L,
                        fixture.repertoireItemId(),
                        fixture.formationId(),
                        fixture.singerOneId(),
                        REGION)
                .block(TIMEOUT))
                .isInstanceOf(FormationNotFoundException.class);

        assertThat(countConstraints(fixture.formationId())).isZero();
    }

    @Test
    void rejectsCreationWhenSingerHasNoPositionInDraft() {
        jdbcTemplate.update(
                """
                DELETE FROM formation_positions
                WHERE formation_version_id = ? AND singer_id = ?
                """,
                fixture.versionId(),
                fixture.singerTwoId());

        assertThatThrownBy(() -> adapter.create(
                        fixture.choirId(),
                        fixture.concertId(),
                        fixture.repertoireItemId(),
                        fixture.formationId(),
                        fixture.singerTwoId(),
                        REGION)
                .block(TIMEOUT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("borrador");

        assertThat(countConstraints(fixture.formationId())).isZero();
    }

    @Test
    void preservesTheFourDecimalBoundsExactly() {
        FormationMobilityConstraint created = adapter.create(
                        fixture.choirId(),
                        fixture.concertId(),
                        fixture.repertoireItemId(),
                        fixture.formationId(),
                        fixture.singerOneId(),
                        REGION)
                .block(TIMEOUT);

        assertThat(created.region().minX()).isEqualTo(new BigDecimal("1.250"));
        assertThat(created.region().maxX()).isEqualTo(new BigDecimal("8.750"));
        assertThat(created.region().minY()).isEqualTo(new BigDecimal("0.500"));
        assertThat(created.region().maxY()).isEqualTo(new BigDecimal("12.000"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT min_x FROM formation_mobility_constraints WHERE id = ?",
                BigDecimal.class,
                created.id())).isEqualTo(new BigDecimal("1.250"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT max_x FROM formation_mobility_constraints WHERE id = ?",
                BigDecimal.class,
                created.id())).isEqualTo(new BigDecimal("8.750"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT min_y FROM formation_mobility_constraints WHERE id = ?",
                BigDecimal.class,
                created.id())).isEqualTo(new BigDecimal("0.500"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT max_y FROM formation_mobility_constraints WHERE id = ?",
                BigDecimal.class,
                created.id())).isEqualTo(new BigDecimal("12.000"));
    }

    @Test
    void databaseRejectsInvalidBoundsEvenWithoutTheDomainModel() {
        assertThatThrownBy(() -> insertRaw(
                        new BigDecimal("5.000"), new BigDecimal("5.000"),
                        new BigDecimal("0.000"), new BigDecimal("10.000")))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> insertRaw(
                        new BigDecimal("0.000"), new BigDecimal("10.000"),
                        new BigDecimal("6.000"), new BigDecimal("5.000")))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> insertRaw(
                        null, new BigDecimal("10.000"),
                        new BigDecimal("0.000"), new BigDecimal("10.000")))
                .isInstanceOf(DataAccessException.class);

        assertThat(countConstraints(fixture.formationId())).isZero();
    }

    @Test
    void databaseCheckRejectsValuesOutsideTheScaleAndAcceptsTheEdges() {
        assertThatThrownBy(() -> insertRaw(
                        new BigDecimal("-0.001"), new BigDecimal("8.000"),
                        new BigDecimal("0.000"), new BigDecimal("10.000")))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> insertRaw(
                        new BigDecimal("1.000"), new BigDecimal("100.001"),
                        new BigDecimal("0.000"), new BigDecimal("10.000")))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> insertRaw(
                        new BigDecimal("1.000"), new BigDecimal("8.000"),
                        new BigDecimal("0.000"), new BigDecimal("100.500")))
                .isInstanceOf(DataAccessException.class);

        insertRaw(new BigDecimal("0.000"), new BigDecimal("100.000"),
                new BigDecimal("0.000"), new BigDecimal("100.000"));

        assertThat(countConstraints(fixture.formationId())).isEqualTo(1);
    }

    private void insertRaw(
            BigDecimal minX,
            BigDecimal maxX,
            BigDecimal minY,
            BigDecimal maxY) {
        jdbcTemplate.update(
                """
                INSERT INTO formation_mobility_constraints
                    (formation_id, choir_id, singer_id,
                     min_x, max_x, min_y, max_y, active)
                VALUES (?, ?, ?, ?, ?, ?, ?, TRUE)
                """,
                fixture.formationId(),
                fixture.choirId(),
                fixture.singerOneId(),
                minX,
                maxX,
                minY,
                maxY);
    }

    @Test
    void rejectsSecondMobilityConstraintForSameSingerInFormation() {
        adapter.create(
                        fixture.choirId(),
                        fixture.concertId(),
                        fixture.repertoireItemId(),
                        fixture.formationId(),
                        fixture.singerOneId(),
                        REGION)
                .block(TIMEOUT);

        assertThatThrownBy(() -> adapter.create(
                        fixture.choirId(),
                        fixture.concertId(),
                        fixture.repertoireItemId(),
                        fixture.formationId(),
                        fixture.singerOneId(),
                        OTHER_REGION)
                .block(TIMEOUT))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(countConstraints(fixture.formationId())).isEqualTo(1);
    }

    @Test
    void allowsTheSameSingerInAnotherFormation() {
        long otherFormationId = insertFormation(
                fixture, "Formación alternativa " + UUID.randomUUID());
        Long otherVersionId = createDraftVersion(otherFormationId, 1);
        insertPosition(otherVersionId, fixture.singerOneId(), "Otra posición");

        adapter.create(
                        fixture.choirId(),
                        fixture.concertId(),
                        fixture.repertoireItemId(),
                        fixture.formationId(),
                        fixture.singerOneId(),
                        REGION)
                .block(TIMEOUT);

        FormationMobilityConstraint otherCreated = adapter.create(
                        fixture.choirId(),
                        fixture.concertId(),
                        fixture.repertoireItemId(),
                        otherFormationId,
                        fixture.singerOneId(),
                        OTHER_REGION)
                .block(TIMEOUT);

        assertThat(otherCreated).isNotNull();
        assertThat(otherCreated.formationId()).isEqualTo(otherFormationId);
    }

    @Test
    void foreignContextsCannotUpdateTheRecord() {
        FormationMobilityConstraint created = createOnFixture();
        Map<String, Object> before = snapshot(created.id());
        Fixture other = newFixture();
        long otherFormationId = insertFormation(
                fixture, "Formación alternativa " + UUID.randomUUID());

        assertUpdateRejected(other.choirId(), fixture.concertId(),
                fixture.repertoireItemId(), fixture.formationId(), created.id());
        assertUpdateRejected(fixture.choirId(), other.concertId(),
                fixture.repertoireItemId(), fixture.formationId(), created.id());
        assertUpdateRejected(fixture.choirId(), fixture.concertId(),
                other.repertoireItemId(), fixture.formationId(), created.id());
        assertUpdateRejected(fixture.choirId(), fixture.concertId(),
                fixture.repertoireItemId(), otherFormationId, created.id());

        assertThat(snapshot(created.id())).isEqualTo(before);
    }

    @Test
    void foreignContextsCannotDeleteTheRecord() {
        FormationMobilityConstraint created = createOnFixture();
        Map<String, Object> before = snapshot(created.id());
        Fixture other = newFixture();
        long otherFormationId = insertFormation(
                fixture, "Formación alternativa " + UUID.randomUUID());

        assertDeleteRejected(other.choirId(), fixture.concertId(),
                fixture.repertoireItemId(), fixture.formationId(), created.id());
        assertDeleteRejected(fixture.choirId(), other.concertId(),
                fixture.repertoireItemId(), fixture.formationId(), created.id());
        assertDeleteRejected(fixture.choirId(), fixture.concertId(),
                other.repertoireItemId(), fixture.formationId(), created.id());
        assertDeleteRejected(fixture.choirId(), fixture.concertId(),
                fixture.repertoireItemId(), otherFormationId, created.id());

        assertThat(countConstraints(fixture.formationId())).isEqualTo(1);
        assertThat(snapshot(created.id())).isEqualTo(before);
    }

    @Test
    void listingIsIsolatedByContext() {
        FormationMobilityConstraint created = createOnFixture();
        Map<String, Object> before = snapshot(created.id());
        Fixture other = newFixture();
        long otherFormationId = insertFormation(
                fixture, "Formación alternativa " + UUID.randomUUID());

        assertThat(listFor(
                fixture.choirId(), fixture.concertId(),
                fixture.repertoireItemId(), fixture.formationId()))
                .containsExactly(created);

        assertThat(listFor(
                other.choirId(), other.concertId(),
                other.repertoireItemId(), other.formationId())).isEmpty();
        assertThat(listFor(
                other.choirId(), fixture.concertId(),
                fixture.repertoireItemId(), fixture.formationId())).isEmpty();
        assertThat(listFor(
                fixture.choirId(), other.concertId(),
                fixture.repertoireItemId(), fixture.formationId())).isEmpty();
        assertThat(listFor(
                fixture.choirId(), fixture.concertId(),
                other.repertoireItemId(), fixture.formationId())).isEmpty();
        assertThat(listFor(
                fixture.choirId(), fixture.concertId(),
                fixture.repertoireItemId(), otherFormationId)).isEmpty();

        assertThat(snapshot(created.id())).isEqualTo(before);
    }

    private FormationMobilityConstraint createOnFixture() {
        return adapter.create(
                        fixture.choirId(),
                        fixture.concertId(),
                        fixture.repertoireItemId(),
                        fixture.formationId(),
                        fixture.singerOneId(),
                        REGION)
                .block(TIMEOUT);
    }

    private void assertUpdateRejected(
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId,
            long constraintId) {
        assertThat(adapter.update(
                        choirId,
                        concertId,
                        repertoireItemId,
                        formationId,
                        constraintId,
                        OTHER_REGION,
                        false)
                .block(TIMEOUT)).isNull();
    }

    private void assertDeleteRejected(
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId,
            long constraintId) {
        assertThat(adapter.delete(
                        choirId,
                        concertId,
                        repertoireItemId,
                        formationId,
                        constraintId)
                .block(TIMEOUT)).isFalse();
    }

    private List<FormationMobilityConstraint> listFor(
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId) {
        return adapter.findForFormation(
                        choirId, concertId, repertoireItemId, formationId)
                .collectList()
                .block(TIMEOUT);
    }

    private Map<String, Object> snapshot(Long constraintId) {
        return jdbcTemplate.queryForMap(
                "SELECT * FROM formation_mobility_constraints WHERE id = ?",
                constraintId);
    }

    private int countConstraints(Long formationId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM formation_mobility_constraints
                WHERE formation_id = ?
                """,
                Integer.class,
                formationId);
    }

    private Fixture createFixture() {
        String suffix = UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO directors (display_name, email) VALUES (?, ?)",
                "Director movilidad " + suffix,
                "mobility-" + suffix + "@example.test");
        Long directorId = jdbcTemplate.queryForObject(
                "SELECT id FROM directors WHERE email = ?",
                Long.class,
                "mobility-" + suffix + "@example.test");

        jdbcTemplate.update(
                "INSERT INTO choirs (director_id, name) VALUES (?, ?)",
                directorId,
                "Coro movilidad " + suffix);
        Long choirId = jdbcTemplate.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ? AND name = ?",
                Long.class,
                directorId,
                "Coro movilidad " + suffix);

        Long singerOneId = insertSinger(choirId, "Primera cantante " + suffix);
        Long singerTwoId = insertSinger(choirId, "Segunda cantante " + suffix);

        jdbcTemplate.update(
                "INSERT INTO concerts (choir_id, title) VALUES (?, ?)",
                choirId,
                "Concierto movilidad " + suffix);
        Long concertId = jdbcTemplate.queryForObject(
                "SELECT id FROM concerts WHERE choir_id = ? AND title = ?",
                Long.class,
                choirId,
                "Concierto movilidad " + suffix);

        jdbcTemplate.update(
                "INSERT INTO repertoire_items (choir_id, title) VALUES (?, ?)",
                choirId,
                "Obra movilidad " + suffix);
        Long repertoireItemId = jdbcTemplate.queryForObject(
                "SELECT id FROM repertoire_items WHERE choir_id = ? AND title = ?",
                Long.class,
                choirId,
                "Obra movilidad " + suffix);

        jdbcTemplate.update(
                """
                INSERT INTO concert_repertoire
                    (concert_id, repertoire_item_id, order_index)
                VALUES (?, ?, 0)
                """,
                concertId,
                repertoireItemId);

        jdbcTemplate.update(
                """
                INSERT INTO vocal_sections (choir_id, name, sort_order)
                VALUES (?, ?, 0)
                """,
                choirId,
                "Sección movilidad " + suffix);
        Long sectionId = jdbcTemplate.queryForObject(
                "SELECT id FROM vocal_sections WHERE choir_id = ? AND name = ?",
                Long.class,
                choirId,
                "Sección movilidad " + suffix);

        jdbcTemplate.update(
                "INSERT INTO vocal_plans (repertoire_item_id, choir_id) VALUES (?, ?)",
                repertoireItemId,
                choirId);
        Long vocalPlanId = jdbcTemplate.queryForObject(
                "SELECT id FROM vocal_plans WHERE repertoire_item_id = ?",
                Long.class,
                repertoireItemId);

        jdbcTemplate.update(
                """
                INSERT INTO vocal_plan_divisions
                    (vocal_plan_id, choir_id, vocal_section_id, division_number)
                VALUES (?, ?, ?, 1)
                """,
                vocalPlanId,
                choirId,
                sectionId);
        Long divisionId = jdbcTemplate.queryForObject(
                """
                SELECT id FROM vocal_plan_divisions
                WHERE vocal_plan_id = ? AND vocal_section_id = ?
                """,
                Long.class,
                vocalPlanId,
                sectionId);

        insertAssignment(choirId, concertId, repertoireItemId, singerOneId, divisionId);
        insertAssignment(choirId, concertId, repertoireItemId, singerTwoId, divisionId);

        Fixture base = new Fixture(
                directorId,
                choirId,
                concertId,
                repertoireItemId,
                null,
                null,
                singerOneId,
                singerTwoId);

        Long formationId = insertFormation(base, "Formación movilidad " + suffix);
        Long versionId = createDraftVersion(formationId, 1);
        insertPosition(versionId, singerOneId, "Primera cantante");
        insertPosition(versionId, singerTwoId, "Segunda cantante");

        return new Fixture(
                directorId,
                choirId,
                concertId,
                repertoireItemId,
                formationId,
                versionId,
                singerOneId,
                singerTwoId);
    }

    private long insertFormation(Fixture base, String name) {
        jdbcTemplate.update(
                """
                INSERT INTO formations
                    (choir_id, concert_id, repertoire_item_id, name, description)
                VALUES (?, ?, ?, ?, ?)
                """,
                base.choirId(),
                base.concertId(),
                base.repertoireItemId(),
                name,
                "Fixture de restricciones de movilidad");
        return jdbcTemplate.queryForObject(
                """
                SELECT id FROM formations
                WHERE choir_id = ? AND concert_id = ?
                  AND repertoire_item_id = ? AND name = ?
                """,
                Long.class,
                base.choirId(),
                base.concertId(),
                base.repertoireItemId(),
                name);
    }

    private Long insertSinger(Long choirId, String name) {
        jdbcTemplate.update(
                """
                INSERT INTO singers (choir_id, display_name, voice_part, active)
                VALUES (?, ?, 'ALTO', TRUE)
                """,
                choirId,
                name);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM singers WHERE choir_id = ? AND display_name = ?",
                Long.class,
                choirId,
                name);
    }

    private void insertAssignment(
            Long choirId,
            Long concertId,
            Long repertoireItemId,
            Long singerId,
            Long divisionId) {
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
                choirId);
    }

    private Long createDraftVersion(Long formationId, int versionNumber) {
        jdbcTemplate.update(
                """
                INSERT INTO formation_versions (formation_id, version_number, status)
                VALUES (?, ?, 'DRAFT')
                """,
                formationId,
                versionNumber);
        return jdbcTemplate.queryForObject(
                """
                SELECT id FROM formation_versions
                WHERE formation_id = ? AND version_number = ?
                """,
                Long.class,
                formationId,
                versionNumber);
    }

    private void insertPosition(Long versionId, Long singerId, String label) {
        String singerName = jdbcTemplate.queryForObject(
                "SELECT display_name FROM singers WHERE id = ?",
                String.class,
                singerId);
        jdbcTemplate.update(
                """
                INSERT INTO formation_positions
                    (formation_version_id, singer_id, singer_name_snapshot,
                     voice_part_snapshot, position_label, x_coordinate, y_coordinate)
                VALUES (?, ?, ?, 'ALTO', ?, 10.000, 20.000)
                """,
                versionId,
                singerId,
                singerName,
                label);
    }

    private record Fixture(
            Long directorId,
            Long choirId,
            Long concertId,
            Long repertoireItemId,
            Long formationId,
            Long versionId,
            Long singerOneId,
            Long singerTwoId) {
    }
}

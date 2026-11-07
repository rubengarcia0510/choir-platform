package com.rubengarcia.choir.formation.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rubengarcia.choir.formation.application.port.out.FormationCatalogPort.CreatedFormation;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class FormationCatalogInitialDraftIntegrationTests {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String FAILURE_CONSTRAINT =
            "choir100_force_initial_position_failure";

    @Autowired
    private FormationCatalogPersistenceAdapter adapter;

    @Autowired
    private JdbcTemplate jdbc;

    private Fixture fixture;

    @BeforeEach
    void setUp() {
        fixture = createFixture();
    }

    @AfterEach
    void cleanUp() {
        try {
            jdbc.execute(
                    "ALTER TABLE formation_positions DROP CONSTRAINT IF EXISTS "
                            + FAILURE_CONSTRAINT);
        } finally {
            jdbc.update(
                    """
                    DELETE FROM formation_positions
                    WHERE formation_version_id IN (
                        SELECT fv.id
                        FROM formation_versions fv
                        JOIN formations f ON f.id = fv.formation_id
                        WHERE f.choir_id = ?
                    )
                    """,
                    fixture.choirId());

            jdbc.update(
                    """
                    DELETE FROM formation_versions
                    WHERE formation_id IN (
                        SELECT id FROM formations WHERE choir_id = ?
                    )
                    """,
                    fixture.choirId());

            jdbc.update(
                    "DELETE FROM formations WHERE choir_id = ?",
                    fixture.choirId());

            jdbc.update(
                    "DELETE FROM concert_vocal_assignments WHERE choir_id = ?",
                    fixture.choirId());

            jdbc.update(
                    "DELETE FROM concert_vocal_adjustments WHERE choir_id = ?",
                    fixture.choirId());

            jdbc.update(
                    """
                    DELETE FROM concert_repertoire
                    WHERE concert_id IN (
                        SELECT id FROM concerts WHERE choir_id = ?
                    )
                    """,
                    fixture.choirId());

            jdbc.update(
                    "DELETE FROM concerts WHERE choir_id = ?",
                    fixture.choirId());

            jdbc.update(
                    "DELETE FROM vocal_plan_divisions WHERE choir_id = ?",
                    fixture.choirId());

            jdbc.update(
                    "DELETE FROM vocal_plans WHERE choir_id = ?",
                    fixture.choirId());

            jdbc.update(
                    "DELETE FROM singers WHERE choir_id = ?",
                    fixture.choirId());

            jdbc.update(
                    "DELETE FROM vocal_sections WHERE choir_id = ?",
                    fixture.choirId());

            jdbc.update(
                    "DELETE FROM repertoire_items WHERE choir_id = ?",
                    fixture.choirId());

            jdbc.update(
                    "DELETE FROM choirs WHERE id = ?",
                    fixture.choirId());

            jdbc.update(
                    "DELETE FROM directors WHERE id = ?",
                    fixture.directorId());
        }
    }

    @Test
    void createsInitialDraftAndCopiesActiveAssignmentSnapshots() {
        CreatedFormation created = adapter.createWithInitialDraft(
                        fixture.choirId(),
                        fixture.concertId(),
                        fixture.repertoireItemId(),
                        fixture.formationName(),
                        "Formación creada por prueba de integración")
                .block(TIMEOUT);

        assertThat(created).isNotNull();
        assertThat(created.formation().getId()).isNotNull();
        assertThat(created.formation().getChoirId())
                .isEqualTo(fixture.choirId());
        assertThat(created.formation().getConcertId())
                .isEqualTo(fixture.concertId());
        assertThat(created.formation().getRepertoireItemId())
                .isEqualTo(fixture.repertoireItemId());

        assertThat(created.draftVersion().getId()).isNotNull();
        assertThat(created.draftVersion().getFormationId())
                .isEqualTo(created.formation().getId());
        assertThat(created.draftVersion().getVersionNumber()).isEqualTo(1);
        assertThat(created.draftVersion().getStatus()).isEqualTo("DRAFT");

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM formation_positions "
                        + "WHERE formation_version_id = ?",
                Integer.class,
                created.draftVersion().getId()))
                .isEqualTo(1);

        Map<String, Object> snapshot = jdbc.queryForMap(
                """
                SELECT singer_id, singer_name_snapshot, voice_part_snapshot,
                       voice_section_id_snapshot, voice_section_name_snapshot,
                       division_number_snapshot, x_coordinate, y_coordinate
                FROM formation_positions
                WHERE formation_version_id = ?
                """,
                created.draftVersion().getId());

        assertThat(((Number) snapshot.get("singer_id")).longValue())
                .isEqualTo(fixture.singerId());
        assertThat(snapshot.get("singer_name_snapshot"))
                .isEqualTo(fixture.singerName());
        assertThat(snapshot.get("voice_part_snapshot")).isEqualTo("SOPRANO");
        assertThat(((Number) snapshot.get("voice_section_id_snapshot")).longValue())
                .isEqualTo(fixture.sectionId());
        assertThat(snapshot.get("voice_section_name_snapshot"))
                .isEqualTo(fixture.sectionName());
        assertThat(((Number) snapshot.get("division_number_snapshot")).intValue())
                .isEqualTo(1);
        assertThat(snapshot.get("x_coordinate").toString()).isEqualTo("10.000");
        assertThat(snapshot.get("y_coordinate").toString()).isEqualTo("50.000");
    }

    @Test
    void distributesUnequalSectionsBySortOrderAndSingerOrderDeterministically() {
        jdbc.update(
                "UPDATE vocal_sections SET sort_order = 3 WHERE id = ?",
                fixture.sectionId());

        SectionFixture sortFour = createSection("Sección CHOIR-109 sort 4", 4);
        SectionFixture sortZero = createSection("Sección CHOIR-109 sort 0", 0);
        SectionFixture sortTwo = createSection("Sección CHOIR-109 sort 2", 2);
        SectionFixture sortOne = createSection("Sección CHOIR-109 sort 1", 1);
        List<SectionFixture> sectionsBySortOrder =
                List.of(sortZero, sortOne, sortTwo,
                        new SectionFixture(fixture.sectionId(), fixture.sectionName(), 3),
                        sortFour);

        Long vocalPlanId = jdbc.queryForObject(
                "SELECT id FROM vocal_plans WHERE repertoire_item_id = ?",
                Long.class,
                fixture.repertoireItemId());
        Map<Long, Map<Integer, Long>> divisions = new HashMap<>();
        divisions.put(
                fixture.sectionId(),
                new HashMap<>(Map.of(1, jdbc.queryForObject(
                        "SELECT id FROM vocal_plan_divisions "
                                + "WHERE vocal_plan_id = ? AND vocal_section_id = ? "
                                + "AND division_number = 1",
                        Long.class,
                        vocalPlanId,
                        fixture.sectionId()))));
        Map<Long, List<ExpectedSinger>> expectedBySection = new HashMap<>();
        expectedBySection.put(
                fixture.sectionId(),
                new ArrayList<>(List.of(new ExpectedSinger(
                        fixture.singerId(), fixture.singerName(), "SOPRANO", 1))));

        addAssignedSinger(vocalPlanId, sortZero, divisions, expectedBySection,
                "Zulu sort 0 division 2", "TENOR", 2);
        addAssignedSinger(vocalPlanId, sortZero, divisions, expectedBySection,
                "Beta sort 0 division 1", "ALTO", 1);
        addAssignedSinger(vocalPlanId, sortZero, divisions, expectedBySection,
                "Alpha sort 0 division 1", "ALTO", 1);
        addAssignedSinger(vocalPlanId, sortZero, divisions, expectedBySection,
                "Gamma sort 0 division 2", "TENOR", 2);

        addAssignedSinger(vocalPlanId, sortOne, divisions, expectedBySection,
                "Delta sort 1 division 1", "BASS", 1);
        addAssignedSinger(vocalPlanId, sortOne, divisions, expectedBySection,
                "Alpha sort 1 division 1", "BASS", 1);

        addAssignedSinger(vocalPlanId, sortTwo, divisions, expectedBySection,
                "Zulu sort 2 division 1", "ALTO", 1);
        addAssignedSinger(vocalPlanId, sortTwo, divisions, expectedBySection,
                "Alpha sort 2 division 1", "ALTO", 1);
        addAssignedSinger(vocalPlanId, sortTwo, divisions, expectedBySection,
                "Beta sort 2 division 2", "SOPRANO", 2);

        addAssignedSinger(vocalPlanId, sectionsBySortOrder.get(3), divisions,
                expectedBySection, "Zulu sort 3 division 1", "SOPRANO", 1);
        addAssignedSinger(vocalPlanId, sectionsBySortOrder.get(3), divisions,
                expectedBySection, "Alpha sort 3 division 1", "SOPRANO", 1);
        addAssignedSinger(vocalPlanId, sectionsBySortOrder.get(3), divisions,
                expectedBySection, "Gamma sort 3 division 1", "SOPRANO", 1);
        addAssignedSinger(vocalPlanId, sectionsBySortOrder.get(3), divisions,
                expectedBySection, "Delta sort 3 division 2", "SOPRANO", 2);
        addAssignedSinger(vocalPlanId, sectionsBySortOrder.get(3), divisions,
                expectedBySection, "Bravo sort 3 division 2", "SOPRANO", 2);
        addAssignedSinger(vocalPlanId, sectionsBySortOrder.get(3), divisions,
                expectedBySection, "Echo sort 3 division 2", "SOPRANO", 2);
        addAssignedSinger(vocalPlanId, sectionsBySortOrder.get(3), divisions,
                expectedBySection, "Charlie sort 3 division 2", "SOPRANO", 2);

        addAssignedSinger(vocalPlanId, sortFour, divisions, expectedBySection,
                "Solo sort 4 division 1", "TENOR", 1);

        CreatedFormation first = createFormation("Primera formación agrupada");
        List<Map<String, Object>> firstRows = positionsFor(first.draftVersion().getId());

        assertThat(firstRows).hasSize(18);
        assertThat(firstRows.stream()
                        .map(row -> ((Number) row.get("voice_section_id_snapshot")).longValue())
                        .distinct()
                        .toList())
                .containsExactlyElementsOf(sectionsBySortOrder.stream()
                        .map(SectionFixture::id)
                        .toList());

        List<BigDecimal> sectionXCoordinates = new ArrayList<>();
        Set<Coordinate> coordinates = new HashSet<>();
        for (SectionFixture section : sectionsBySortOrder) {
            List<Map<String, Object>> sectionRows = firstRows.stream()
                    .filter(row -> section.id().equals(
                            ((Number) row.get("voice_section_id_snapshot")).longValue()))
                    .toList();
            List<ExpectedSinger> expectedSingers = expectedBySection.get(section.id()).stream()
                    .sorted(Comparator.comparingInt(ExpectedSinger::divisionNumber)
                            .thenComparing(ExpectedSinger::name)
                            .thenComparing(ExpectedSinger::id))
                    .toList();

            assertThat(sectionRows).hasSize(expectedSingers.size());
            assertThat(sectionRows.stream()
                            .map(row -> (BigDecimal) row.get("x_coordinate"))
                            .distinct()
                            .toList())
                    .hasSize(1);
            sectionXCoordinates.add((BigDecimal) sectionRows.getFirst().get("x_coordinate"));
            assertThat(sectionRows.stream()
                            .map(row -> ((Number) row.get("singer_id")).longValue())
                            .toList())
                    .containsExactlyElementsOf(expectedSingers.stream()
                            .map(ExpectedSinger::id)
                            .toList());

            List<BigDecimal> expectedYCoordinates =
                    expectedYCoordinates(expectedSingers.size());
            for (int index = 0; index < sectionRows.size(); index++) {
                Map<String, Object> row = sectionRows.get(index);
                ExpectedSinger expectedSinger = expectedSingers.get(index);
                BigDecimal x = (BigDecimal) row.get("x_coordinate");
                BigDecimal y = (BigDecimal) row.get("y_coordinate");

                assertThat(row.get("voice_section_name_snapshot"))
                        .isEqualTo(section.name());
                assertThat(row.get("division_number_snapshot"))
                        .isEqualTo(expectedSinger.divisionNumber());
                assertThat(row.get("voice_part_snapshot")).isEqualTo(expectedSinger.voicePart());
                assertThat(y).isEqualTo(expectedYCoordinates.get(index));
                assertThat(x).isBetween(BigDecimal.ZERO, BigDecimal.valueOf(100));
                assertThat(y).isBetween(BigDecimal.ZERO, BigDecimal.valueOf(100));
                assertThat(coordinates.add(new Coordinate(x, y))).isTrue();
            }
        }

        assertThat(sectionXCoordinates).isSorted();
        assertThat(sectionXCoordinates.stream().distinct().toList())
                .hasSize(sectionsBySortOrder.size());
        assertThat(coordinates).hasSize(firstRows.size());

        CreatedFormation second = createFormation("Segunda formación agrupada");
        List<Map<String, Object>> secondRows = positionsFor(second.draftVersion().getId());
        assertThat(coordinatesBySinger(secondRows)).isEqualTo(coordinatesBySinger(firstRows));
        assertThat(coordinatesBySinger(positionsFor(first.draftVersion().getId())))
                .isEqualTo(coordinatesBySinger(firstRows));
    }

    @Test
    void persistenceFailureRollsBackFormationAndInitialDraft() {
        int formationsBefore = countFormations();
        int versionsBefore = countVersions();

        jdbc.execute(
                "ALTER TABLE formation_positions ADD CONSTRAINT "
                        + FAILURE_CONSTRAINT
                        + " CHECK (singer_id <> " + fixture.singerId() + ")");

        try {
            assertThatThrownBy(() -> adapter.createWithInitialDraft(
                            fixture.choirId(),
                            fixture.concertId(),
                            fixture.repertoireItemId(),
                            fixture.formationName(),
                            "Esta creación debe revertirse")
                    .block(TIMEOUT))
                    .isNotNull();

            assertThat(countFormations()).isEqualTo(formationsBefore);
            assertThat(countVersions()).isEqualTo(versionsBefore);

            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM formations "
                            + "WHERE choir_id = ? AND concert_id = ? "
                            + "AND repertoire_item_id = ? AND name = ?",
                    Integer.class,
                    fixture.choirId(),
                    fixture.concertId(),
                    fixture.repertoireItemId(),
                    fixture.formationName()))
                    .isZero();
        } finally {
            jdbc.execute(
                    "ALTER TABLE formation_positions DROP CONSTRAINT IF EXISTS "
                            + FAILURE_CONSTRAINT);
        }
    }

    private Fixture createFixture() {
        String suffix = UUID.randomUUID().toString();
        String email = "choir100-" + suffix + "@example.test";
        String choirName = "Coro CHOIR-100 " + suffix;
        String singerName = "Cantante CHOIR-100 " + suffix;
        String concertTitle = "Concierto CHOIR-100 " + suffix;
        String repertoireTitle = "Obra CHOIR-100 " + suffix;
        String sectionName = "Sección CHOIR-100 " + suffix;
        String formationName = "Formación CHOIR-100 " + suffix;

        jdbc.update(
                "INSERT INTO directors (display_name, email) VALUES (?, ?)",
                "Director CHOIR-100 " + suffix,
                email);

        Long directorId = jdbc.queryForObject(
                "SELECT id FROM directors WHERE email = ?",
                Long.class,
                email);

        jdbc.update(
                "INSERT INTO choirs (director_id, name) VALUES (?, ?)",
                directorId,
                choirName);

        Long choirId = jdbc.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ? AND name = ?",
                Long.class,
                directorId,
                choirName);

        jdbc.update(
                "INSERT INTO concerts (choir_id, title) VALUES (?, ?)",
                choirId,
                concertTitle);

        Long concertId = jdbc.queryForObject(
                "SELECT id FROM concerts WHERE choir_id = ? AND title = ?",
                Long.class,
                choirId,
                concertTitle);

        jdbc.update(
                "INSERT INTO repertoire_items (choir_id, title) VALUES (?, ?)",
                choirId,
                repertoireTitle);

        Long repertoireItemId = jdbc.queryForObject(
                "SELECT id FROM repertoire_items WHERE choir_id = ? AND title = ?",
                Long.class,
                choirId,
                repertoireTitle);

        jdbc.update(
                """
                INSERT INTO concert_repertoire
                    (concert_id, repertoire_item_id, order_index)
                VALUES (?, ?, ?)
                """,
                concertId,
                repertoireItemId,
                0);

        jdbc.update(
                "INSERT INTO vocal_sections (choir_id, name, sort_order) "
                        + "VALUES (?, ?, ?)",
                choirId,
                sectionName,
                0);

        Long sectionId = jdbc.queryForObject(
                "SELECT id FROM vocal_sections WHERE choir_id = ? AND name = ?",
                Long.class,
                choirId,
                sectionName);

        jdbc.update(
                "INSERT INTO singers "
                        + "(choir_id, display_name, voice_part, active, voice_section_id) "
                        + "VALUES (?, ?, ?, TRUE, ?)",
                choirId,
                singerName,
                "SOPRANO",
                sectionId);

        Long singerId = jdbc.queryForObject(
                "SELECT id FROM singers WHERE choir_id = ? AND display_name = ?",
                Long.class,
                choirId,
                singerName);

        jdbc.update(
                "INSERT INTO vocal_plans (repertoire_item_id, choir_id) "
                        + "VALUES (?, ?)",
                repertoireItemId,
                choirId);

        Long vocalPlanId = jdbc.queryForObject(
                "SELECT id FROM vocal_plans WHERE repertoire_item_id = ?",
                Long.class,
                repertoireItemId);

        jdbc.update(
                """
                INSERT INTO vocal_plan_divisions
                    (vocal_plan_id, choir_id, vocal_section_id, division_number)
                VALUES (?, ?, ?, ?)
                """,
                vocalPlanId,
                choirId,
                sectionId,
                1);

        Long divisionId = jdbc.queryForObject(
                "SELECT id FROM vocal_plan_divisions "
                        + "WHERE vocal_plan_id = ? AND vocal_section_id = ? "
                        + "AND division_number = ?",
                Long.class,
                vocalPlanId,
                sectionId,
                1);

        jdbc.update(
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

        return new Fixture(
                directorId,
                choirId,
                concertId,
                repertoireItemId,
                singerId,
                singerName,
                sectionId,
                sectionName,
                formationName);
    }

    private Long insertSinger(
            long choirId,
            String singerName,
            String voicePart,
            Long sectionId) {
        jdbc.update(
                "INSERT INTO singers "
                        + "(choir_id, display_name, voice_part, active, voice_section_id) "
                        + "VALUES (?, ?, ?, TRUE, ?)",
                choirId,
                singerName,
                voicePart,
                sectionId);

        return jdbc.queryForObject(
                "SELECT id FROM singers WHERE choir_id = ? AND display_name = ?",
                Long.class,
                choirId,
                singerName);
    }

    private SectionFixture createSection(String name, int sortOrder) {
        jdbc.update(
                "INSERT INTO vocal_sections (choir_id, name, sort_order) VALUES (?, ?, ?)",
                fixture.choirId(),
                name,
                sortOrder);
        Long sectionId = jdbc.queryForObject(
                "SELECT id FROM vocal_sections WHERE choir_id = ? AND name = ?",
                Long.class,
                fixture.choirId(),
                name);
        return new SectionFixture(sectionId, name, sortOrder);
    }

    private void addAssignedSinger(
            Long vocalPlanId,
            SectionFixture section,
            Map<Long, Map<Integer, Long>> divisions,
            Map<Long, List<ExpectedSinger>> expectedBySection,
            String singerName,
            String voicePart,
            int divisionNumber) {
        Map<Integer, Long> sectionDivisions = divisions.computeIfAbsent(
                section.id(), ignored -> new HashMap<>());
        Long divisionId = sectionDivisions.computeIfAbsent(
                divisionNumber,
                number -> createDivision(vocalPlanId, section.id(), number));
        Long singerId = insertSinger(
                fixture.choirId(),
                singerName,
                voicePart,
                section.id());
        jdbc.update(
                """
                INSERT INTO concert_vocal_assignments
                    (concert_id, repertoire_item_id, singer_id,
                     vocal_plan_division_id, choir_id)
                VALUES (?, ?, ?, ?, ?)
                """,
                fixture.concertId(),
                fixture.repertoireItemId(),
                singerId,
                divisionId,
                fixture.choirId());
        expectedBySection.computeIfAbsent(section.id(), ignored -> new ArrayList<>())
                .add(new ExpectedSinger(singerId, singerName, voicePart, divisionNumber));
    }

    private Long createDivision(Long vocalPlanId, Long sectionId, int divisionNumber) {
        jdbc.update(
                "INSERT INTO vocal_plan_divisions "
                        + "(vocal_plan_id, choir_id, vocal_section_id, division_number) "
                        + "VALUES (?, ?, ?, ?)",
                vocalPlanId,
                fixture.choirId(),
                sectionId,
                divisionNumber);
        return jdbc.queryForObject(
                "SELECT id FROM vocal_plan_divisions "
                        + "WHERE vocal_plan_id = ? AND vocal_section_id = ? "
                        + "AND division_number = ?",
                Long.class,
                vocalPlanId,
                sectionId,
                divisionNumber);
    }

    private CreatedFormation createFormation(String name) {
        return adapter.createWithInitialDraft(
                        fixture.choirId(),
                        fixture.concertId(),
                        fixture.repertoireItemId(),
                        name,
                        "Formación con posiciones iniciales agrupadas")
                .block(TIMEOUT);
    }

    private List<Map<String, Object>> positionsFor(Long formationVersionId) {
        return jdbc.queryForList(
                """
                SELECT singer_id, voice_section_id_snapshot, voice_section_name_snapshot,
                       division_number_snapshot, voice_part_snapshot,
                       x_coordinate, y_coordinate
                FROM formation_positions
                WHERE formation_version_id = ?
                ORDER BY x_coordinate, y_coordinate, singer_id
                """,
                formationVersionId);
    }

    private List<BigDecimal> expectedYCoordinates(int positionCount) {
        if (positionCount == 1) {
            return List.of(new BigDecimal("50.000"));
        }

        BigDecimal availableHeight = BigDecimal.valueOf(80);
        List<BigDecimal> coordinates = new ArrayList<>(positionCount);
        for (int index = 0; index < positionCount; index++) {
            coordinates.add(BigDecimal.TEN.add(availableHeight
                    .multiply(BigDecimal.valueOf(index))
                    .divide(
                            BigDecimal.valueOf(positionCount - 1),
                            3,
                            RoundingMode.HALF_UP)));
        }
        return coordinates;
    }

    private Map<Long, Coordinate> coordinatesBySinger(List<Map<String, Object>> rows) {
        Map<Long, Coordinate> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            result.put(
                    ((Number) row.get("singer_id")).longValue(),
                    new Coordinate(
                            (BigDecimal) row.get("x_coordinate"),
                            (BigDecimal) row.get("y_coordinate")));
        }
        return result;
    }

    private int countFormations() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM formations WHERE choir_id = ?",
                Integer.class,
                fixture.choirId());
    }

    private int countVersions() {
        return jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM formation_versions fv
                JOIN formations f ON f.id = fv.formation_id
                WHERE f.choir_id = ?
                """,
                Integer.class,
                fixture.choirId());
    }

    private record Fixture(
            Long directorId,
            Long choirId,
            Long concertId,
            Long repertoireItemId,
            Long singerId,
            String singerName,
            Long sectionId,
            String sectionName,
            String formationName) {
    }

    private record SectionFixture(Long id, String name, int sortOrder) {}

    private record ExpectedSinger(
            Long id,
            String name,
            String voicePart,
            int divisionNumber) {}

    private record Coordinate(BigDecimal x, BigDecimal y) {}
}

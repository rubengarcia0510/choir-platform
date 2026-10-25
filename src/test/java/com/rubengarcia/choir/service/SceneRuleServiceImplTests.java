package com.rubengarcia.choir.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import com.rubengarcia.choir.domain.SceneRuleRevision;
import com.rubengarcia.choir.repository.SceneRuleRevisionRepository;
import com.rubengarcia.choir.scene.application.port.out.SceneRuleHistoryPort;
import com.rubengarcia.choir.scene.domain.SceneRuleRevisionSnapshot;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

@SpringBootTest
class SceneRuleServiceImplTests {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SceneRuleService sceneRuleService;

    @Autowired
    private SceneRuleHistoryPort sceneRuleHistoryPort;

    @MockitoSpyBean
    private SceneRuleRevisionRepository revisionRepository;

    private final List<Long> createdDirectorIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (Long directorId : createdDirectorIds) {
            List<Long> choirIds = jdbc.queryForList(
                    "SELECT id FROM choirs WHERE director_id = ?",
                    Long.class,
                    directorId);
            for (Long choirId : choirIds) {
                jdbc.update("""
                        DELETE FROM concert_repertoire
                        WHERE concert_id IN (
                            SELECT id FROM concerts WHERE choir_id = ?)
                        """, choirId);
                jdbc.update("DELETE FROM concerts WHERE choir_id = ?", choirId);
                jdbc.update("DELETE FROM repertoire_items WHERE choir_id = ?", choirId);
                jdbc.update("DELETE FROM singers WHERE choir_id = ?", choirId);
                jdbc.update("DELETE FROM choirs WHERE id = ?", choirId);
            }
            jdbc.update("DELETE FROM directors WHERE id = ?", directorId);
        }
        createdDirectorIds.clear();
    }

    @Test
    void supportsCreateReadEditReorderAndSoftDeactivation() {
        Fixture fixture = createFixture("crud");

        SceneRuleService.RuleView first = sceneRuleService.create(
                fixture.email(),
                fixture.choirId(),
                fixture.concertId(),
                fixture.repertoireItemId(),
                new SceneRuleService.RuleData(
                        "MOVEMENT",
                        "Desplazamiento inicial",
                        List.of(new SceneRuleService.GroupData("Entrada", 4))))
                .block(Duration.ofSeconds(10));
        SceneRuleService.RuleView second = sceneRuleService.create(
                fixture.email(),
                fixture.choirId(),
                fixture.concertId(),
                fixture.repertoireItemId(),
                new SceneRuleService.RuleData(
                        "EXIT",
                        "Salida en dos grupos",
                        List.of(
                                new SceneRuleService.GroupData("Izquierda", 4),
                                new SceneRuleService.GroupData("Derecha", 4))))
                .block(Duration.ofSeconds(10));

        assertThat(first).isNotNull();
        assertThat(first.order()).isZero();
        assertThat(second.order()).isEqualTo(1);
        assertThat(second.groups())
                .extracting(SceneRuleService.GroupView::label)
                .containsExactly("Izquierda", "Derecha");

        SceneRuleService.RuleView updated = sceneRuleService.update(
                fixture.email(),
                fixture.choirId(),
                fixture.concertId(),
                fixture.repertoireItemId(),
                first.id(),
                new SceneRuleService.RuleData(
                        "CAST_DIVISION",
                        "Repartir elenco",
                        List.of(
                                new SceneRuleService.GroupData("Grupo A", 5),
                                new SceneRuleService.GroupData("Grupo B", 3))))
                .block(Duration.ofSeconds(10));

        assertThat(updated.ruleType()).isEqualTo("CAST_DIVISION");
        assertThat(updated.description()).isEqualTo("Repartir elenco");
        assertThat(updated.groups())
                .extracting(SceneRuleService.GroupView::label)
                .containsExactly("Grupo A", "Grupo B");

        assertThat(sceneRuleService.get(
                fixture.email(),
                fixture.choirId(),
                fixture.concertId(),
                fixture.repertoireItemId(),
                first.id())
                .block(Duration.ofSeconds(10)))
                .isEqualTo(updated);

        sceneRuleService.reorder(
                fixture.email(),
                fixture.choirId(),
                fixture.concertId(),
                fixture.repertoireItemId(),
                List.of(second.id(), first.id()))
                .block(Duration.ofSeconds(10));

        List<SceneRuleService.RuleView> reordered = sceneRuleService.list(
                fixture.email(),
                fixture.choirId(),
                fixture.concertId(),
                fixture.repertoireItemId())
                .collectList()
                .block(Duration.ofSeconds(10));
        assertThat(reordered)
                .extracting(SceneRuleService.RuleView::id)
                .containsExactly(second.id(), first.id());
        assertThat(reordered)
                .extracting(SceneRuleService.RuleView::order)
                .containsExactly(0, 1);

        sceneRuleService.deactivate(
                fixture.email(),
                fixture.choirId(),
                fixture.concertId(),
                fixture.repertoireItemId(),
                first.id())
                .block(Duration.ofSeconds(10));

        List<SceneRuleService.RuleView> afterDeactivation =
                sceneRuleService.list(
                        fixture.email(),
                        fixture.choirId(),
                        fixture.concertId(),
                        fixture.repertoireItemId())
                        .collectList()
                        .block(Duration.ofSeconds(10));
        assertThat(afterDeactivation).hasSize(2);
        assertThat(afterDeactivation.get(1).id()).isEqualTo(first.id());
        assertThat(afterDeactivation.get(1).active()).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM scene_rule_groups WHERE scene_rule_id = ?",
                Integer.class,
                first.id()))
                .isEqualTo(2);

        assertThat(jdbc.queryForList("""
                SELECT revision_kind FROM scene_rule_revisions
                WHERE scene_rule_id = ? ORDER BY revision_number
                """, String.class, first.id()))
                .containsExactly("CREATE", "UPDATE", "REORDER", "DEACTIVATE");

        assertThat(jdbc.queryForList("""
                SELECT revision_number FROM scene_rule_revisions
                WHERE scene_rule_id = ? ORDER BY revision_number
                """, Integer.class, first.id()))
                .containsExactly(1, 2, 3, 4);

        assertThat(jdbc.queryForObject("""
                SELECT active FROM scene_rule_revisions
                WHERE scene_rule_id = ? AND revision_number = 3
                """, Boolean.class, first.id()))
                .isTrue();

        assertThat(jdbc.queryForObject("""
                SELECT active FROM scene_rule_revisions
                WHERE scene_rule_id = ? AND revision_number = 4
                """, Boolean.class, first.id()))
                .isFalse();

        assertThat(jdbc.queryForObject("""
                SELECT rule_type FROM scene_rule_revisions
                WHERE scene_rule_id = ? AND revision_number = 1
                """, String.class, first.id()))
                .isEqualTo("MOVEMENT");
        assertThat(jdbc.queryForObject("""
                SELECT description FROM scene_rule_revisions
                WHERE scene_rule_id = ? AND revision_number = 1
                """, String.class, first.id()))
                .isEqualTo("Desplazamiento inicial");
        assertThat(jdbc.queryForObject("""
                SELECT rule_type FROM scene_rule_revisions
                WHERE scene_rule_id = ? AND revision_number = 2
                """, String.class, first.id()))
                .isEqualTo("CAST_DIVISION");
        assertThat(jdbc.queryForObject("""
                SELECT rule_order FROM scene_rule_revisions
                WHERE scene_rule_id = ? AND revision_number = 3
                """, Integer.class, first.id()))
                .isEqualTo(1);
        assertThat(jdbc.queryForList("""
                SELECT revision_group.group_label
                FROM scene_rule_revision_groups revision_group
                JOIN scene_rule_revisions revision
                    ON revision.id = revision_group.revision_id
                WHERE revision.scene_rule_id = ?
                  AND revision.revision_number = 1
                ORDER BY revision_group.group_order
                """, String.class, first.id()))
                .containsExactly("Entrada");
        assertThat(jdbc.queryForList("""
                SELECT revision_group.group_label
                FROM scene_rule_revision_groups revision_group
                JOIN scene_rule_revisions revision
                    ON revision.id = revision_group.revision_id
                WHERE revision.scene_rule_id = ?
                  AND revision.revision_number = 2
                ORDER BY revision_group.group_order
                """, String.class, first.id()))
                .containsExactly("Grupo A", "Grupo B");

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM scene_rule_revision_groups
                WHERE revision_id IN (
                    SELECT id FROM scene_rule_revisions
                    WHERE scene_rule_id = ?
                )
                """, Integer.class, first.id()))
                .isEqualTo(7);
    }

    @Test
    void preservesIdentifiedGroupMembersInImmutableRevisions() {
        Fixture fixture = createFixture("identified-groups");
        long firstSingerId = createSinger(fixture, "Primera coreuta");
        long secondSingerId = createSinger(fixture, "Segundo coreuta");

        SceneRuleService.RuleView created = sceneRuleService.create(
                fixture.email(),
                fixture.choirId(),
                fixture.concertId(),
                fixture.repertoireItemId(),
                new SceneRuleService.RuleData(
                        "MOVEMENT",
                        "Entrada identificada",
                        List.of(new SceneRuleService.GroupData(
                                "Entrada",
                                "IDENTIFIED",
                                null,
                                List.of(firstSingerId, secondSingerId)))))
                .block(Duration.ofSeconds(10));

        assertThat(created.groups()).containsExactly(new SceneRuleService.GroupView(
                0,
                "Entrada",
                "IDENTIFIED",
                2,
                List.of(firstSingerId, secondSingerId)));

        SceneRuleService.RuleView updated = sceneRuleService.update(
                fixture.email(),
                fixture.choirId(),
                fixture.concertId(),
                fixture.repertoireItemId(),
                created.id(),
                new SceneRuleService.RuleData(
                        "MOVEMENT",
                        "Entrada actualizada",
                        List.of(new SceneRuleService.GroupData(
                                "Entrada",
                                "IDENTIFIED",
                                null,
                                List.of(secondSingerId)))))
                .block(Duration.ofSeconds(10));

        assertThat(updated.groups()).containsExactly(new SceneRuleService.GroupView(
                0,
                "Entrada",
                "IDENTIFIED",
                1,
                List.of(secondSingerId)));
        assertThat(jdbc.queryForList("""
                SELECT member.singer_id
                FROM scene_rule_revision_group_members member
                JOIN scene_rule_revisions revision
                    ON revision.id = member.revision_id
                WHERE revision.scene_rule_id = ?
                  AND revision.revision_number = 1
                ORDER BY member.member_order
                """, Long.class, created.id()))
                .containsExactly(firstSingerId, secondSingerId);
        assertThat(jdbc.queryForList("""
                SELECT member.singer_id
                FROM scene_rule_revision_group_members member
                JOIN scene_rule_revisions revision
                    ON revision.id = member.revision_id
                WHERE revision.scene_rule_id = ?
                  AND revision.revision_number = 2
                ORDER BY member.member_order
                """, Long.class, created.id()))
                .containsExactly(secondSingerId);
        assertThat(jdbc.queryForList("""
                SELECT composition_mode
                FROM scene_rule_revision_groups group_snapshot
                JOIN scene_rule_revisions revision
                    ON revision.id = group_snapshot.revision_id
                WHERE revision.scene_rule_id = ?
                ORDER BY revision.revision_number
                """, String.class, created.id()))
                .containsExactly("IDENTIFIED", "IDENTIFIED");
    }

    @Test
    void readsCompleteRevisionSnapshotsWithoutUsingCurrentSingerData() {
        Fixture fixture = createFixture("revision-history-read");
        long firstSingerId = createSinger(fixture, "Coreuta histórica");
        long secondSingerId = createSinger(fixture, "Otra coreuta histórica");
        SceneRuleService.RuleView created = sceneRuleService.create(
                fixture.email(),
                fixture.choirId(),
                fixture.concertId(),
                fixture.repertoireItemId(),
                new SceneRuleService.RuleData(
                        "MOVEMENT",
                        "Entrada histórica",
                        List.of(new SceneRuleService.GroupData(
                                "Entrada",
                                "IDENTIFIED",
                                null,
                                List.of(firstSingerId, secondSingerId)))))
                .block(Duration.ofSeconds(10));
        SceneRuleService.RuleView withoutGroups = sceneRuleService.create(
                fixture.email(),
                fixture.choirId(),
                fixture.concertId(),
                fixture.repertoireItemId(),
                new SceneRuleService.RuleData(
                        "EXIT", "Salida sin grupos", List.of()))
                .block(Duration.ofSeconds(10));
        sceneRuleService.update(
                fixture.email(),
                fixture.choirId(),
                fixture.concertId(),
                fixture.repertoireItemId(),
                created.id(),
                new SceneRuleService.RuleData(
                        "MOVEMENT",
                        "Entrada actualizada",
                        List.of(new SceneRuleService.GroupData(
                                "Elenco",
                                3))))
                .block(Duration.ofSeconds(10));
        sceneRuleService.deactivate(
                fixture.email(),
                fixture.choirId(),
                fixture.concertId(),
                fixture.repertoireItemId(),
                created.id())
                .block(Duration.ofSeconds(10));

        List<SceneRuleRevisionSnapshot> revisions =
                sceneRuleHistoryPort
                        .findAllRevisionsByRepertoireItemId(
                                fixture.repertoireItemId())
                        .collectList()
                        .block(Duration.ofSeconds(10));

        assertThat(revisions)
                .extracting(SceneRuleRevisionSnapshot::revisionNumber)
                .containsExactly(1, 2, 3, 1);
        assertThat(revisions)
                .extracting(SceneRuleRevisionSnapshot::revisionKind)
                .containsExactly("CREATE", "UPDATE", "DEACTIVATE", "CREATE");
        assertThat(revisions).hasSize(4);
        assertThat(revisions.get(3).sceneRuleId()).isEqualTo(withoutGroups.id());
        assertThat(revisions.get(3).groups()).isEmpty();
        assertThat(revisions.get(0).groups()).containsExactly(
                new SceneRuleRevisionSnapshot.GroupSnapshot(
                        0,
                        "Entrada",
                        2,
                        "IDENTIFIED",
                        List.of(firstSingerId, secondSingerId)));
        assertThat(revisions.get(1).groups()).containsExactly(
                new SceneRuleRevisionSnapshot.GroupSnapshot(
                        0, "Elenco", 3, "COUNT", List.of()));
        assertThat(revisions.get(2).active()).isFalse();
        assertThat(revisions.get(2).groups())
                .isEqualTo(revisions.get(1).groups());

        jdbc.update("DELETE FROM singers WHERE id = ?", firstSingerId);

        List<SceneRuleRevisionSnapshot> afterSingerDeletion =
                sceneRuleHistoryPort
                        .findAllRevisionsByRepertoireItemId(
                                fixture.repertoireItemId())
                        .collectList()
                        .block(Duration.ofSeconds(10));
        assertThat(afterSingerDeletion.get(0).groups().get(0).singerIds())
                .containsExactly(firstSingerId, secondSingerId);
    }

    @Test
    void rejectsIdentifiedGroupMembersFromAnotherChoir() {
        Fixture owner = createFixture("identified-owner");
        Fixture other = createFixture("identified-other");
        long foreignSingerId = createSinger(other, "Coreuta ajena");

        assertThatThrownBy(() -> sceneRuleService.create(
                owner.email(),
                owner.choirId(),
                owner.concertId(),
                owner.repertoireItemId(),
                new SceneRuleService.RuleData(
                        "MOVEMENT",
                        "Entrada no permitida",
                        List.of(new SceneRuleService.GroupData(
                                "Entrada",
                                "IDENTIFIED",
                                null,
                                List.of(foreignSingerId)))))
                .block(Duration.ofSeconds(10)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(
                        ((ResponseStatusException) exception).getStatusCode().value())
                        .isEqualTo(400));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM scene_rules WHERE repertoire_item_id = ?",
                Integer.class,
                owner.repertoireItemId()))
                .isZero();
    }

    @Test
    void rejectsUnauthorizedDirectorAndRuleFromAnotherSong() {
        Fixture owner = createFixture("owner");
        Fixture other = createFixture("other");
        SceneRuleService.RuleView rule = sceneRuleService.create(
                owner.email(),
                owner.choirId(),
                owner.concertId(),
                owner.repertoireItemId(),
                new SceneRuleService.RuleData(
                        "EXIT", "Salida", List.of()))
                .block(Duration.ofSeconds(10));

        assertThatThrownBy(() -> sceneRuleService.create(
                other.email(),
                other.choirId(),
                owner.concertId(),
                owner.repertoireItemId(),
                new SceneRuleService.RuleData(
                        "EXIT", "No autorizada", List.of()))
                .block(Duration.ofSeconds(10)))
                .isInstanceOf(ResponseStatusException.class);

        assertThatThrownBy(() -> sceneRuleService.update(
                owner.email(),
                owner.choirId(),
                owner.concertId(),
                other.repertoireItemId(),
                rule.id(),
                new SceneRuleService.RuleData(
                        "EXIT", "Obra incorrecta", List.of()))
                .block(Duration.ofSeconds(10)))
                .isInstanceOf(ResponseStatusException.class);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM scene_rules WHERE repertoire_item_id = ?",
                Integer.class,
                owner.repertoireItemId()))
                .isEqualTo(1);
    }

    @Test
    void rejectsPartialAndDuplicateReorderWithoutChangingOrder() {
        Fixture fixture = createFixture("reorder");
        SceneRuleService.RuleView first = createRule(fixture, "Primera");
        SceneRuleService.RuleView second = createRule(fixture, "Segunda");

        assertThatThrownBy(() -> sceneRuleService.reorder(
                fixture.email(),
                fixture.choirId(),
                fixture.concertId(),
                fixture.repertoireItemId(),
                List.of(first.id(), first.id()))
                .block(Duration.ofSeconds(10)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> sceneRuleService.reorder(
                fixture.email(),
                fixture.choirId(),
                fixture.concertId(),
                fixture.repertoireItemId(),
                List.of(first.id()))
                .block(Duration.ofSeconds(10)))
                .isInstanceOf(ResponseStatusException.class);

        assertThat(sceneRuleService.list(
                fixture.email(),
                fixture.choirId(),
                fixture.concertId(),
                fixture.repertoireItemId())
                .map(SceneRuleService.RuleView::id)
                .collectList()
                .block(Duration.ofSeconds(10)))
                .containsExactly(first.id(), second.id());
    }

    @Test
    void rejectsInvalidRuleDataBeforeWriting() {
        Fixture fixture = createFixture("invalid");

        assertThatThrownBy(() -> sceneRuleService.create(
                fixture.email(),
                fixture.choirId(),
                fixture.concertId(),
                fixture.repertoireItemId(),
                new SceneRuleService.RuleData(
                        "UNKNOWN", "Descripción", List.of()))
                .block(Duration.ofSeconds(10)))
                .isInstanceOf(ResponseStatusException.class);

        assertThatThrownBy(() -> sceneRuleService.create(
                fixture.email(),
                fixture.choirId(),
                fixture.concertId(),
                fixture.repertoireItemId(),
                new SceneRuleService.RuleData(
                        "CAST_DIVISION", "Falta la división", List.of()))
                .block(Duration.ofSeconds(10)))
                .isInstanceOf(ResponseStatusException.class);

        assertThatThrownBy(() -> sceneRuleService.create(
                fixture.email(),
                fixture.choirId(),
                fixture.concertId(),
                fixture.repertoireItemId(),
                new SceneRuleService.RuleData(
                        "MOVEMENT",
                        "Descripción",
                        List.of(new SceneRuleService.GroupData("  ", 0))))
                .block(Duration.ofSeconds(10)))
                .isInstanceOf(ResponseStatusException.class);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM scene_rules WHERE repertoire_item_id = ?",
                Integer.class,
                fixture.repertoireItemId()))
                .isZero();
    }

    @Test
    void rollsBackRuleAndGroupsWhenCreateRevisionFails() {
        Fixture fixture = createFixture("create-revision-failure");
        doReturn(Mono.error(new IllegalStateException("forced revision failure")))
                .when(revisionRepository)
                .save(any(SceneRuleRevision.class));

        assertThatThrownBy(() -> createRule(fixture, "No debe persistirse"))
                .isInstanceOf(RuntimeException.class);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM scene_rules WHERE repertoire_item_id = ?",
                Integer.class,
                fixture.repertoireItemId()))
                .isZero();
        assertThat(jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM scene_rule_groups groups
                JOIN scene_rules rules ON rules.id = groups.scene_rule_id
                WHERE rules.repertoire_item_id = ?
                """,
                Integer.class,
                fixture.repertoireItemId()))
                .isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM scene_rule_revisions revisions
                JOIN scene_rules rules ON rules.id = revisions.scene_rule_id
                WHERE rules.repertoire_item_id = ?
                """, Integer.class, fixture.repertoireItemId()))
                .isZero();
    }

    @Test
    void rollsBackRuleAndGroupsWhenUpdateRevisionFails() {
        Fixture fixture = createFixture("update-revision-failure");
        SceneRuleService.RuleView rule = sceneRuleService.create(
                fixture.email(),
                fixture.choirId(),
                fixture.concertId(),
                fixture.repertoireItemId(),
                new SceneRuleService.RuleData(
                        "MOVEMENT",
                        "Descripción original",
                        List.of(new SceneRuleService.GroupData("Original", 3))))
                .block(Duration.ofSeconds(10));
        doReturn(Mono.error(new IllegalStateException("forced revision failure")))
                .when(revisionRepository)
                .save(any(SceneRuleRevision.class));

        assertThatThrownBy(() -> sceneRuleService.update(
                fixture.email(),
                fixture.choirId(),
                fixture.concertId(),
                fixture.repertoireItemId(),
                rule.id(),
                new SceneRuleService.RuleData(
                        "EXIT",
                        "Descripción editada",
                        List.of(new SceneRuleService.GroupData("Nueva", 5))))
                .block(Duration.ofSeconds(10)))
                .isInstanceOf(RuntimeException.class);

        assertThat(jdbc.queryForObject(
                "SELECT description FROM scene_rules WHERE id = ?",
                String.class,
                rule.id()))
                .isEqualTo("Descripción original");
        assertThat(jdbc.queryForList("""
                SELECT group_label FROM scene_rule_groups
                WHERE scene_rule_id = ?
                ORDER BY group_order
                """, String.class, rule.id()))
                .containsExactly("Original");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM scene_rule_revisions
                WHERE scene_rule_id = ?
                """, Integer.class, rule.id()))
                .isEqualTo(1);
    }

    private SceneRuleService.RuleView createRule(Fixture fixture, String description) {
        return sceneRuleService.create(
                fixture.email(),
                fixture.choirId(),
                fixture.concertId(),
                fixture.repertoireItemId(),
                new SceneRuleService.RuleData(
                        "MOVEMENT", description, List.of()))
                .block(Duration.ofSeconds(10));
    }

    private long createSinger(Fixture fixture, String name) {
        jdbc.update("""
                INSERT INTO singers (choir_id, display_name, voice_part, active)
                VALUES (?, ?, 'SOPRANO', TRUE)
                """, fixture.choirId(), name);
        return jdbc.queryForObject(
                "SELECT id FROM singers WHERE choir_id = ? AND display_name = ?",
                Long.class,
                fixture.choirId(),
                name);
    }

    private Fixture createFixture(String label) {
        String suffix = label + "-" + UUID.randomUUID();
        String email = suffix + "@example.test";
        jdbc.update(
                "INSERT INTO directors (display_name, email) VALUES (?, ?)",
                "Director " + suffix,
                email);
        Long directorId = jdbc.queryForObject(
                "SELECT id FROM directors WHERE email = ?",
                Long.class,
                email);
        createdDirectorIds.add(directorId);

        jdbc.update(
                "INSERT INTO choirs (director_id, name) VALUES (?, ?)",
                directorId,
                "Coro " + suffix);
        Long choirId = jdbc.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ? AND name = ?",
                Long.class,
                directorId,
                "Coro " + suffix);

        jdbc.update(
                "INSERT INTO concerts (choir_id, title, status) VALUES (?, ?, 'PLANNED')",
                choirId,
                "Concierto " + suffix);
        Long concertId = jdbc.queryForObject(
                "SELECT id FROM concerts WHERE choir_id = ? AND title = ?",
                Long.class,
                choirId,
                "Concierto " + suffix);

        jdbc.update(
                "INSERT INTO repertoire_items (choir_id, title) VALUES (?, ?)",
                choirId,
                "Obra " + suffix);
        Long repertoireItemId = jdbc.queryForObject(
                "SELECT id FROM repertoire_items WHERE choir_id = ? AND title = ?",
                Long.class,
                choirId,
                "Obra " + suffix);
        jdbc.update("""
                INSERT INTO concert_repertoire
                    (concert_id, repertoire_item_id, order_index)
                VALUES (?, ?, 0)
                """, concertId, repertoireItemId);

        return new Fixture(email, choirId, concertId, repertoireItemId);
    }

    private record Fixture(
            String email,
            Long choirId,
            Long concertId,
            Long repertoireItemId) {
    }
}

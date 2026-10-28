package com.rubengarcia.choir;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class SceneRuleIntegrityConstraintTests {

    private static final long MISSING_ID = -999999L;

    private final JdbcTemplate jdbc;

    SceneRuleIntegrityConstraintTests(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Test
    void persistsRulesAndGroupsInTheirDeclaredOrder() {
        long itemId = createRepertoireItem();
        long ruleId = insertRule(itemId, 1, "MOVEMENT", "Desplazamiento");

        insertRule(itemId, 0, "EXIT", "Salida");
        insertGroup(ruleId, 1, "Segundo grupo", 4);
        insertGroup(ruleId, 0, "Primer grupo", 4);

        assertThat(jdbc.queryForList("""
                SELECT rule_order FROM scene_rules
                WHERE repertoire_item_id = ?
                ORDER BY rule_order
                """, Integer.class, itemId))
                .containsExactly(0, 1);

        assertThat(jdbc.queryForList("""
                SELECT group_label FROM scene_rule_groups
                WHERE scene_rule_id = ?
                ORDER BY group_order
                """, String.class, ruleId))
                .containsExactly("Primer grupo", "Segundo grupo");

        assertThat(jdbc.queryForObject("""
                SELECT active FROM scene_rules WHERE id = ?
                """, Boolean.class, ruleId))
                .isTrue();

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM scene_rule_groups
                WHERE scene_rule_id = ?
                """, Integer.class, ruleId))
                .isEqualTo(2);
    }

    @Test
    void rejectsInvalidRulesGroupsAndReferences() {
        long itemId = createRepertoireItem();
        long ruleId = insertRule(itemId, 0, "MOVEMENT", "Movimiento");

        assertThatThrownBy(() ->
                insertRule(itemId, 1, "INVALID", "Tipo inválido"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() ->
                insertRule(itemId, -1, "EXIT", "Orden inválido"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() ->
                insertRule(itemId, 0, "EXIT", "Orden duplicado"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() ->
                insertRule(MISSING_ID, 2, "EXIT", "Obra inexistente"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() ->
                insertGroup(ruleId, -1, "Orden inválido", 4))
                .isInstanceOf(DataIntegrityViolationException.class);

        insertGroup(ruleId, 0, "Primer grupo", 4);

        assertThatThrownBy(() ->
                insertGroup(ruleId, 0, "Orden duplicado", 3))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() ->
                insertGroup(ruleId, 1, "Sin integrantes", 0))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() ->
                insertGroup(MISSING_ID, 1, "Regla inexistente", 4))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingRuleOrRepertoireItemDeletesDependentRecords() {
        long itemId = createRepertoireItem();
        long firstRuleId = insertRule(itemId, 0, "MOVEMENT", "Movimiento");
        long secondRuleId = insertRule(itemId, 1, "EXIT", "Salida");

        insertGroup(firstRuleId, 0, "Grupo de la primera regla", 4);
        insertGroup(secondRuleId, 0, "Grupo de la segunda regla", 4);

        jdbc.update("DELETE FROM scene_rules WHERE id = ?", firstRuleId);

        assertThat(countGroups(firstRuleId)).isZero();
        assertThat(countGroups(secondRuleId)).isEqualTo(1);

        jdbc.update("DELETE FROM repertoire_items WHERE id = ?", itemId);

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM scene_rules WHERE repertoire_item_id = ?
                """, Integer.class, itemId))
                .isZero();

        assertThat(countGroups(secondRuleId)).isZero();
    }

    private long createRepertoireItem() {
        String suffix = UUID.randomUUID().toString();
        String email = suffix + "@example.test";
        String choirName = "Coro " + suffix;
        String title = "Obra " + suffix;

        jdbc.update("""
                INSERT INTO directors (display_name, email)
                VALUES (?, ?)
                """, "Director de prueba", email);

        Long directorId = jdbc.queryForObject("""
                SELECT id FROM directors WHERE email = ?
                """, Long.class, email);

        jdbc.update("""
                INSERT INTO choirs (director_id, name)
                VALUES (?, ?)
                """, directorId, choirName);

        Long choirId = jdbc.queryForObject("""
                SELECT id FROM choirs WHERE director_id = ? AND name = ?
                """, Long.class, directorId, choirName);

        jdbc.update("""
                INSERT INTO repertoire_items (choir_id, title)
                VALUES (?, ?)
                """, choirId, title);

        return jdbc.queryForObject("""
                SELECT id FROM repertoire_items WHERE choir_id = ? AND title = ?
                """, Long.class, choirId, title);
    }

    private long insertRule(
            long itemId, int order, String type, String description) {
        jdbc.update("""
                INSERT INTO scene_rules
                    (repertoire_item_id, rule_order, rule_type, description)
                VALUES (?, ?, ?, ?)
                """, itemId, order, type, description);

        return jdbc.queryForObject("""
                SELECT id FROM scene_rules
                WHERE repertoire_item_id = ? AND rule_order = ?
                """, Long.class, itemId, order);
    }

    private void insertGroup(
            long ruleId, int order, String label, int memberCount) {
        jdbc.update("""
                INSERT INTO scene_rule_groups
                    (scene_rule_id, group_order, group_label, member_count)
                VALUES (?, ?, ?, ?)
                """, ruleId, order, label, memberCount);
    }

    private int countGroups(long ruleId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM scene_rule_groups WHERE scene_rule_id = ?
                """, Integer.class, ruleId);
    }
}

package com.rubengarcia.choir;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ChoirDomainMigrationTests {

private final JdbcTemplate jdbcTemplate;

ChoirDomainMigrationTests(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
}

@Test
void migrationCreatesAllDomainTables() {
    List<String> tables = jdbcTemplate.queryForList("""
            SELECT TABLE_NAME
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_SCHEMA = 'PUBLIC'
            """, String.class).stream()
            .map(String::toLowerCase)
            .toList();

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

}

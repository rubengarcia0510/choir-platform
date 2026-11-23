package com.rubengarcia.choir.formation.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.rubengarcia.choir.formation.application.port.out.FormationVisibilityRosterPort;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestConstructor;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class FormationVisibilityRosterPersistenceAdapterTests {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final FormationVisibilityRosterPort roster;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final List<Long> directorIds = new ArrayList<>();
    private final List<Long> choirIds = new ArrayList<>();

    FormationVisibilityRosterPersistenceAdapterTests(
            FormationVisibilityRosterPort roster,
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder) {
        this.roster = roster;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @AfterEach
    void cleanUp() {
        for (Long choirId : choirIds) {
            jdbcTemplate.update("DELETE FROM concert_vocal_assignments WHERE choir_id = ?", choirId);
            jdbcTemplate.update("""
                    DELETE FROM concert_repertoire WHERE concert_id IN (
                        SELECT id FROM concerts WHERE choir_id = ?)
                    """, choirId);
            jdbcTemplate.update("DELETE FROM vocal_plan_divisions WHERE choir_id = ?", choirId);
            jdbcTemplate.update("DELETE FROM vocal_plans WHERE choir_id = ?", choirId);
            jdbcTemplate.update("DELETE FROM concerts WHERE choir_id = ?", choirId);
            jdbcTemplate.update("DELETE FROM singers WHERE choir_id = ?", choirId);
            jdbcTemplate.update("DELETE FROM vocal_sections WHERE choir_id = ?", choirId);
            jdbcTemplate.update("DELETE FROM repertoire_items WHERE choir_id = ?", choirId);
            jdbcTemplate.update("DELETE FROM choirs WHERE id = ?", choirId);
        }
        for (Long directorId : directorIds) {
            jdbcTemplate.update("DELETE FROM directors WHERE id = ?", directorId);
        }
        choirIds.clear();
        directorIds.clear();
    }

    @Test
    void returnsActiveAssignedSingersOfAnUnpublishedConcert() {
        Fixture f = newFixture();
        long active = singer(f.choirId(), "activa");
        long other = singer(f.choirId(), "otra");
        assign(f, active);
        assign(f, other);

        assertThat(rosterFor(f.choirId(), f.concertId(), f.repertoireId()))
                .containsExactlyInAnyOrder(active, other);
    }

    @Test
    void returnsTheSameRosterOncePublished() {
        Fixture f = newFixture();
        long active = singer(f.choirId(), "publicada");
        assign(f, active);
        jdbcTemplate.update("UPDATE concerts SET status = 'PUBLISHED' WHERE id = ?", f.concertId());

        assertThat(rosterFor(f.choirId(), f.concertId(), f.repertoireId()))
                .containsExactly(active);
    }

    @Test
    void includesSingersWithoutAnyDraftPosition() {
        Fixture f = newFixture();
        long withoutPosition = singer(f.choirId(), "sin posición");
        assign(f, withoutPosition);

        assertThat(rosterFor(f.choirId(), f.concertId(), f.repertoireId()))
                .containsExactly(withoutPosition);
    }

    @Test
    void excludesInactiveSingers() {
        Fixture f = newFixture();
        long active = singer(f.choirId(), "activa");
        long inactive = singerWithStatus(f.choirId(), "inactiva", false);
        assign(f, active);
        assign(f, inactive);

        assertThat(rosterFor(f.choirId(), f.concertId(), f.repertoireId()))
                .containsExactly(active);
    }

    @Test
    void doesNotMixChoirs() {
        Fixture first = newFixture();
        Fixture second = newFixture();
        long firstSinger = singer(first.choirId(), "primer coro");
        long secondSinger = singer(second.choirId(), "segundo coro");
        assign(first, firstSinger);
        assign(second, secondSinger);

        assertThat(rosterFor(first.choirId(), first.concertId(), first.repertoireId()))
                .containsExactly(firstSinger);
        assertThat(rosterFor(second.choirId(), first.concertId(), first.repertoireId()))
                .isEmpty();
    }

    @Test
    void doesNotMixConcerts() {
        Fixture f = newFixture();
        long singerInFirstConcert = singer(f.choirId(), "concierto uno");
        long singerInSecondConcert = singer(f.choirId(), "concierto dos");
        assign(f, singerInFirstConcert);
        long secondConcert = insertConcert(f.choirId(), f.repertoireId());
        assignInConcert(f, secondConcert, singerInSecondConcert);

        assertThat(rosterFor(f.choirId(), f.concertId(), f.repertoireId()))
                .containsExactly(singerInFirstConcert);
        assertThat(rosterFor(f.choirId(), secondConcert, f.repertoireId()))
                .containsExactly(singerInSecondConcert);
    }

    @Test
    void doesNotMixRepertoireItems() {
        Fixture f = newFixture();
        long singerInFirstWork = singer(f.choirId(), "obra uno");
        long singerInSecondWork = singer(f.choirId(), "obra dos");
        assign(f, singerInFirstWork);
        long secondWork = insertRepertoireItem(f.choirId());
        jdbcTemplate.update("""
                INSERT INTO concert_repertoire (concert_id, repertoire_item_id, order_index)
                VALUES (?, ?, 1)
                """, f.concertId(), secondWork);
        long division = insertDivision(f.choirId(), secondWork, f.sectionId());
        assignRaw(f.choirId(), f.concertId(), secondWork, singerInSecondWork, division);

        assertThat(rosterFor(f.choirId(), f.concertId(), f.repertoireId()))
                .containsExactly(singerInFirstWork);
        assertThat(rosterFor(f.choirId(), f.concertId(), secondWork))
                .containsExactly(singerInSecondWork);
    }

    @Test
    void neverReturnsDuplicateIds() {
        Fixture f = newFixture();
        long singer = singer(f.choirId(), "única");
        assign(f, singer);

        List<Long> ids = rosterFor(f.choirId(), f.concertId(), f.repertoireId());

        assertThat(ids).hasSize(1).containsExactly(singer);
    }

    // ------------------------------------------------------------------

    private List<Long> rosterFor(long choirId, long concertId, long repertoireId) {
        return roster.findActiveAssignedSingerIds(choirId, concertId, repertoireId)
                .collectList()
                .block(TIMEOUT);
    }

    private Fixture newFixture() {
        String unique = UUID.randomUUID().toString();
        String email = "roster-" + unique + "@example.test";
        jdbcTemplate.update("""
                INSERT INTO directors (display_name, email, password_hash, email_verified)
                VALUES (?, ?, ?, TRUE)
                """, "Director roster " + unique, email, passwordEncoder.encode("Roster-Test-2026!"));
        Long directorId = jdbcTemplate.queryForObject(
                "SELECT id FROM directors WHERE email = ?", Long.class, email);
        directorIds.add(directorId);

        jdbcTemplate.update("INSERT INTO choirs (director_id, name) VALUES (?, ?)",
                directorId, "Coro roster " + unique);
        long choirId = jdbcTemplate.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ? AND name = ?",
                Long.class, directorId, "Coro roster " + unique);
        choirIds.add(choirId);

        long concertId = insertConcert(choirId, 0L);
        long repertoireId = insertRepertoireItem(choirId);
        jdbcTemplate.update("""
                INSERT INTO concert_repertoire (concert_id, repertoire_item_id, order_index)
                VALUES (?, ?, 0)
                """, concertId, repertoireId);

        jdbcTemplate.update("""
                INSERT INTO vocal_sections (choir_id, name, sort_order)
                VALUES (?, ?, 0)
                """, choirId, "Sección roster " + unique);
        long sectionId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM vocal_sections WHERE choir_id = ?", Long.class, choirId);

        long divisionId = insertDivision(choirId, repertoireId, sectionId);

        return new Fixture(choirId, concertId, repertoireId, sectionId, divisionId);
    }

    private long insertConcert(long choirId, long repertoireId) {
        jdbcTemplate.update("INSERT INTO concerts (choir_id, title) VALUES (?, ?)",
                choirId, "Concierto roster " + UUID.randomUUID());
        long concertId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM concerts WHERE choir_id = ?", Long.class, choirId);
        if (repertoireId != 0L) {
            jdbcTemplate.update("""
                    INSERT INTO concert_repertoire (concert_id, repertoire_item_id, order_index)
                    VALUES (?, ?, 0)
                    """, concertId, repertoireId);
        }
        return concertId;
    }

    private long insertRepertoireItem(long choirId) {
        jdbcTemplate.update("INSERT INTO repertoire_items (choir_id, title) VALUES (?, ?)",
                choirId, "Obra roster " + UUID.randomUUID());
        return jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM repertoire_items WHERE choir_id = ?", Long.class, choirId);
    }

    private long insertDivision(long choirId, long repertoireId, long sectionId) {
        jdbcTemplate.update(
                "INSERT INTO vocal_plans (repertoire_item_id, choir_id) VALUES (?, ?)",
                repertoireId, choirId);
        long planId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM vocal_plans WHERE repertoire_item_id = ?", Long.class, repertoireId);
        jdbcTemplate.update("""
                INSERT INTO vocal_plan_divisions
                    (vocal_plan_id, choir_id, vocal_section_id, division_number)
                VALUES (?, ?, ?, 1)
                """, planId, choirId, sectionId);
        return jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM vocal_plan_divisions WHERE vocal_plan_id = ?", Long.class, planId);
    }

    private long singer(long choirId, String name) {
        return singerWithStatus(choirId, name, true);
    }

    private long singerWithStatus(long choirId, String name, boolean active) {
        String displayName = name + " " + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO singers (choir_id, display_name, voice_part, active)
                VALUES (?, ?, 'ALTO', ?)
                """, choirId, displayName, active);
        return jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM singers WHERE choir_id = ? AND display_name = ?",
                Long.class, choirId, displayName);
    }

    private void assign(Fixture f, long singerId) {
        assignInConcert(f, f.concertId(), singerId);
    }

    private void assignInConcert(Fixture f, long concertId, long singerId) {
        assignRaw(f.choirId(), concertId, f.repertoireId(), singerId, f.divisionId());
    }

    private void assignRaw(long choirId, long concertId, long repertoireId,
                           long singerId, long divisionId) {
        jdbcTemplate.update("""
                INSERT INTO concert_vocal_assignments
                    (concert_id, repertoire_item_id, singer_id, vocal_plan_division_id, choir_id)
                VALUES (?, ?, ?, ?, ?)
                """, concertId, repertoireId, singerId, divisionId, choirId);
    }

    private record Fixture(
            long choirId,
            long concertId,
            long repertoireId,
            long sectionId,
            long divisionId) {
    }
}

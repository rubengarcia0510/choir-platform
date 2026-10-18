package com.rubengarcia.choir.service;

import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class VocalPlanValidationService {

    private final DatabaseClient database;

    public VocalPlanValidationService(DatabaseClient database) {
        this.database = database;
    }

    public record SectionValidation(
            long vocalSectionId,
            String vocalSectionName,
            int activeSingerCount,
            int divisionCount,
            boolean contiguousNumbering,
            boolean enoughSingers) {}

    public record ValidationReport(
            long choirId,
            long workId,
            long vocalPlanId,
            boolean reviewRequired,
            String reviewReason,
            String populationSignature,
            LocalDateTime lastValidatedAt,
            List<SectionValidation> sections) {}

    private record PlanState(
            long id,
            boolean reviewRequired,
            String reviewReason,
            String validatedSignature,
            LocalDateTime lastValidatedAt) {}

    private record Division(
            long sectionId,
            String sectionName,
            int number) {}

    private record SingerPopulation(long sectionId, long singerId) {}

    public Mono<ValidationReport> inspect(
            String email, long choirId, long workId) {
        return loadReport(email, choirId, workId, false);
    }

    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<ValidationReport> validate(
            String email, long choirId, long workId) {
        return loadReport(email, choirId, workId, true);
    }

    private Mono<ValidationReport> loadReport(
            String email, long choirId, long workId, boolean acknowledge) {
        return ownedWork(email, choirId, workId)
                .then(findPlan(choirId, workId))
                .flatMap(plan -> Mono.zip(
                        loadDivisions(plan.id()).collectList(),
                        loadPopulation(choirId, plan.id()).collectList(),
                        Mono.just(plan))
                    .flatMap(data -> {
                        List<Division> divisions = data.getT1();
                        List<SingerPopulation> singers = data.getT2();
                        PlanState state = data.getT3();

                        Map<Long, List<Division>> bySection = new LinkedHashMap<>();
                        for (Division division : divisions) {
                            bySection.computeIfAbsent(
                                    division.sectionId(), ignored -> new ArrayList<>())
                                    .add(division);
                        }

                        Map<Long, Integer> populationCounts = new LinkedHashMap<>();
                        for (SingerPopulation singer : singers) {
                            populationCounts.merge(singer.sectionId(), 1, Integer::sum);
                        }

                        List<SectionValidation> sections = new ArrayList<>();
                        List<String> issues = new ArrayList<>();

                        for (Map.Entry<Long, List<Division>> entry : bySection.entrySet()) {
                            List<Division> sectionDivisions = entry.getValue();
                            sectionDivisions.sort(
                                    java.util.Comparator.comparingInt(Division::number));

                            int divisionCount = sectionDivisions.size();
                            boolean contiguous = true;

                            for (int i = 0; i < divisionCount; i++) {
                                if (sectionDivisions.get(i).number() != i + 1) {
                                    contiguous = false;
                                    break;
                                }
                            }

                            int activeSingerCount =
                                    populationCounts.getOrDefault(entry.getKey(), 0);
                            Division first = sectionDivisions.get(0);
                            boolean enoughSingers = activeSingerCount >= divisionCount;

                            sections.add(new SectionValidation(
                                    entry.getKey(),
                                    first.sectionName(),
                                    activeSingerCount,
                                    divisionCount,
                                    contiguous,
                                    enoughSingers));

                            if (!contiguous) {
                                issues.add("La cuerda «" + first.sectionName()
                                        + "» tiene huecos en la numeración de divisiones.");
                            }

                            if (!enoughSingers) {
                                issues.add("La cuerda «" + first.sectionName()
                                        + "» tiene N=" + activeSingerCount
                                        + " coreutas activos y k=" + divisionCount
                                        + " divisiones; requiere revisión.");
                            }
                        }

                        String currentSignature = populationSignature(divisions, singers);
                        boolean populationChanged = state.validatedSignature() != null
                                && !state.validatedSignature().equals(currentSignature);

                        if (populationChanged && !acknowledge) {
                            issues.add("Cambió la población activa desde la última validación.");
                        }

                        if (state.validatedSignature() == null && !acknowledge) {
                            issues.add("El plan todavía no tiene una población validada.");
                        }

                        boolean structurallyValid = sections.stream().allMatch(section ->
                                section.contiguousNumbering() && section.enoughSingers());

                        boolean reviewRequired;
                        String reason;
                        String signatureToStore = state.validatedSignature();
                        LocalDateTime lastValidatedAt = state.lastValidatedAt();

                        if (acknowledge && structurallyValid) {
                            reviewRequired = false;
                            reason = null;
                            signatureToStore = currentSignature;
                            lastValidatedAt = LocalDateTime.now();
                        } else {
                            reviewRequired = state.reviewRequired()
                                    || !issues.isEmpty()
                                    || !structurallyValid;

                            reason = issues.isEmpty()
                                    ? state.reviewReason()
                                    : String.join(" ", issues);

                            if (reason == null && reviewRequired) {
                                reason = "El plan requiere validación explícita del director.";
                            }
                        }

                        final String storedSignature = signatureToStore;
                        final LocalDateTime storedValidatedAt = lastValidatedAt;
                        final boolean storedReviewRequired = reviewRequired;
                        final String storedReason = reason;

                        return database.sql("""
                                UPDATE vocal_plans
                                SET review_required = :reviewRequired,
                                    review_reason = :reviewReason,
                                    validated_population_signature = :signature,
                                    last_validated_at = :validatedAt
                                WHERE id = :planId
                                  AND choir_id = :choirId
                                  AND repertoire_item_id = :workId
                                """)
                                .bind("reviewRequired", storedReviewRequired)
                                .bind("reviewReason", storedReason)
                                .bind("signature", storedSignature)
                                .bind("validatedAt", storedValidatedAt)
                                .bind("planId", state.id())
                                .bind("choirId", choirId)
                                .bind("workId", workId)
                                .fetch()
                                .rowsUpdated()
                                .thenReturn(new ValidationReport(
                                        choirId,
                                        workId,
                                        state.id(),
                                        storedReviewRequired,
                                        storedReason,
                                        currentSignature,
                                        storedValidatedAt,
                                        List.copyOf(sections)));
                    }));
    }

    private Mono<PlanState> findPlan(long choirId, long workId) {
        return database.sql("""
                SELECT id, review_required, review_reason,
                       validated_population_signature, last_validated_at
                FROM vocal_plans
                WHERE choir_id = :choirId
                  AND repertoire_item_id = :workId
                """)
                .bind("choirId", choirId)
                .bind("workId", workId)
                .map((row, meta) -> new PlanState(
                        row.get("id", Long.class),
                        Boolean.TRUE.equals(row.get("review_required", Boolean.class)),
                        row.get("review_reason", String.class),
                        row.get("validated_population_signature", String.class),
                        row.get("last_validated_at", LocalDateTime.class)))
                .one()
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "La obra no tiene un plan vocal.")));
    }

    private reactor.core.publisher.Flux<Division> loadDivisions(long planId) {
        return database.sql("""
                SELECT d.vocal_section_id, d.division_number, s.name
                FROM vocal_plan_divisions d
                JOIN vocal_sections s
                  ON s.id = d.vocal_section_id
                 AND s.choir_id = d.choir_id
                WHERE d.vocal_plan_id = :planId
                ORDER BY d.vocal_section_id, d.division_number
                """)
                .bind("planId", planId)
                .map((row, meta) -> new Division(
                        row.get("vocal_section_id", Long.class),
                        row.get("name", String.class),
                        row.get("division_number", Integer.class)))
                .all();
    }

    private reactor.core.publisher.Flux<SingerPopulation> loadPopulation(
            long choirId, long planId) {
        return database.sql("""
                SELECT s.voice_section_id AS section_id, s.id AS singer_id
                FROM singers s
                WHERE s.choir_id = :choirId
                  AND s.active = TRUE
                  AND s.voice_section_id IN (
                      SELECT DISTINCT d.vocal_section_id
                      FROM vocal_plan_divisions d
                      WHERE d.vocal_plan_id = :planId
                  )
                ORDER BY s.voice_section_id, s.id
                """)
                .bind("choirId", choirId)
                .bind("planId", planId)
                .map((row, meta) -> new SingerPopulation(
                        row.get("section_id", Long.class),
                        row.get("singer_id", Long.class)))
                .all();
    }

    private String populationSignature(
            List<Division> divisions, List<SingerPopulation> singers) {
        StringBuilder canonical = new StringBuilder();

        divisions.stream()
                .map(Division::sectionId)
                .distinct()
                .sorted()
                .forEach(id -> canonical.append("section:").append(id).append('\n'));

        singers.stream()
                .sorted(java.util.Comparator
                        .comparingLong(SingerPopulation::sectionId)
                        .thenComparingLong(SingerPopulation::singerId))
                .forEach(singer -> canonical.append(singer.sectionId())
                        .append(':').append(singer.singerId()).append('\n'));

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no está disponible", exception);
        }
    }

    private Mono<Void> ownedWork(String email, long choirId, long workId) {
        if (email == null || email.isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        }

        return database.sql("""
                SELECT COUNT(*) AS n
                FROM repertoire_items r
                JOIN choirs c ON c.id = r.choir_id
                JOIN directors d ON d.id = c.director_id
                WHERE r.id = :workId
                  AND r.choir_id = :choirId
                  AND LOWER(d.email) = LOWER(:email)
                """)
                .bind("workId", workId)
                .bind("choirId", choirId)
                .bind("email", email)
                .map((row, meta) -> {
                    Long count = row.get("n", Long.class);
                    return count != null && count > 0;
                })
                .one()
                .defaultIfEmpty(false)
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Obra inexistente o fuera del coro del director.")))
                .then();
    }
}

package com.rubengarcia.choir.service;

import com.rubengarcia.choir.domain.Choir;
import com.rubengarcia.choir.domain.Singer;
import com.rubengarcia.choir.domain.VocalSection;
import com.rubengarcia.choir.repository.ChoirRepository;
import com.rubengarcia.choir.repository.DirectorRepository;
import com.rubengarcia.choir.repository.SingerRepository;
import com.rubengarcia.choir.repository.VocalSectionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class VoicePartMigrationService {

    private final DirectorRepository directorRepository;
    private final ChoirRepository choirRepository;
    private final SingerRepository singerRepository;
    private final VocalSectionRepository vocalSectionRepository;

    public VoicePartMigrationService(
            DirectorRepository directorRepository,
            ChoirRepository choirRepository,
            SingerRepository singerRepository,
            VocalSectionRepository vocalSectionRepository) {
        this.directorRepository = directorRepository;
        this.choirRepository = choirRepository;
        this.singerRepository = singerRepository;
        this.vocalSectionRepository = vocalSectionRepository;
    }

    public Mono<MigrationProfile> profile(String email, Long choirId) {
        return ownedChoir(email, choirId)
                .flatMap(choir -> Mono.zip(
                        singerRepository.findByChoirId(choir.getId()).collectList(),
                        vocalSectionRepository
                                .findByChoirIdOrderBySortOrderAscNameAsc(choir.getId())
                                .collectList()
                ))
                .map(data -> buildProfile(data.getT1(), data.getT2()));
    }

    @Transactional
    public Mono<MigrationResult> confirm(
            String email,
            Long choirId,
            boolean confirmed,
            List<MappingConfirmation> mappings) {

        if (!confirmed) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La confirmación explícita es obligatoria."));
        }

        if (mappings == null || mappings.isEmpty()) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Debe indicarse al menos una correspondencia."));
        }

        Set<String> legacyValues = new HashSet<>();

        for (MappingConfirmation mapping : mappings) {
            if (mapping == null
                    || mapping.legacyValue() == null
                    || mapping.legacyValue().isBlank()
                    || mapping.sectionId() == null) {
                return Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Cada correspondencia debe tener valor legado y cuerda."));
            }

            if (!legacyValues.add(mapping.legacyValue())) {
                return Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "No se puede confirmar más de una cuerda para el mismo valor legado."));
            }
        }

        return ownedChoir(email, choirId)
                .flatMap(choir -> Flux.fromIterable(mappings)
                        .concatMap(mapping -> validateSection(choir, mapping)
                                .then(Mono.defer(() -> migrateValue(choir, mapping))))
                        .reduce(0L, Long::sum)
                        .map(count -> new MigrationResult(
                                choir.getId(),
                                count,
                                mappings.size(),
                                "Se conservaron voice_part y los snapshots históricos. "
                                        + "Los cantantes con cuerda ya asignada no se sobrescribieron."
                        )));
    }

    private Mono<Void> validateSection(
            Choir choir,
            MappingConfirmation mapping) {

        return vocalSectionRepository.findById(mapping.sectionId())
                .filter(section -> choir.getId().equals(section.getChoirId()))
                .filter(VocalSection::isActive)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "La cuerda indicada no existe, no pertenece al coro o está inactiva."
                )))
                .then();
    }

    private Mono<Long> migrateValue(
            Choir choir,
            MappingConfirmation mapping) {

        return singerRepository.findByChoirId(choir.getId())
                .filter(singer -> Objects.equals(
                        singer.getVoicePart(), mapping.legacyValue()))
                .filter(singer -> singer.getVoiceSectionId() == null)
                .concatMap(singer -> {
                    singer.setVoiceSectionId(mapping.sectionId());
                    return singerRepository.save(singer);
                })
                .count();
    }

    private MigrationProfile buildProfile(
            List<Singer> singers,
            List<VocalSection> sections) {

        List<LegacyValueCount> values = new ArrayList<>();

        for (Singer singer : singers) {
            String value = singer.getVoicePart();

            LegacyValueCount existing = values.stream()
                    .filter(item -> Objects.equals(item.legacyValue(), value))
                    .findFirst()
                    .orElse(null);

            if (existing == null) {
                values.add(new LegacyValueCount(value, 1));
            } else {
                int index = values.indexOf(existing);
                values.set(index, new LegacyValueCount(
                        existing.legacyValue(),
                        existing.singerCount() + 1));
            }
        }

        List<LegacyValueProfile> profiles = values.stream()
                .map(item -> {
                    List<SectionCandidate> candidates = sections.stream()
                            .map(section -> new SectionCandidate(
                                    section.getId(),
                                    section.getName(),
                                    section.isActive(),
                                    section.isActive()
                                            && normalize(item.legacyValue())
                                            .equals(normalize(section.getName()))
                            ))
                            .toList();

                    List<SectionCandidate> matches = candidates.stream()
                            .filter(SectionCandidate::nameMatch)
                            .toList();

                    Long suggestedId = matches.size() == 1
                            ? matches.get(0).id()
                            : null;

                    return new LegacyValueProfile(
                            item.legacyValue(),
                            item.singerCount(),
                            candidates,
                            suggestedId,
                            matches.size() > 1,
                            true
                    );
                })
                .toList();

        return new MigrationProfile(
                singers.size(),
                profiles,
                "Solo lectura: no se modificaron cantantes ni datos históricos. "
                        + "Las sugerencias requieren confirmación explícita."
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private Mono<Choir> ownedChoir(String email, Long choirId) {
        if (email == null || email.isBlank()) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED));
        }

        return directorRepository.findByEmailIgnoreCase(email)
                .switchIfEmpty(Mono.error(notFound()))
                .flatMap(director -> choirRepository.findById(choirId)
                        .filter(choir ->
                                director.getId().equals(choir.getDirectorId()))
                        .switchIfEmpty(Mono.error(notFound())));
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private record LegacyValueCount(
            String legacyValue,
            long singerCount) {}

    public record MappingConfirmation(
            String legacyValue,
            Long sectionId) {}

    public record MigrationResult(
            Long choirId,
            long migratedSingerCount,
            int confirmedMappingCount,
            String notice) {}

    public record MigrationProfile(
            int totalSingers,
            List<LegacyValueProfile> values,
            String notice) {}

    public record LegacyValueProfile(
            String legacyValue,
            long singerCount,
            List<SectionCandidate> availableSections,
            Long suggestedSectionId,
            boolean ambiguous,
            boolean requiresConfirmation) {}

    public record SectionCandidate(
            Long id,
            String name,
            boolean active,
            boolean nameMatch) {}
}

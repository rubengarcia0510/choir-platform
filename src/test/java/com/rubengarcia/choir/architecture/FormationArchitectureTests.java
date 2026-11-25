package com.rubengarcia.choir.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Límites arquitectónicos de {@code formation} verificados sobre el código fuente de producción.
 * Usa solo el JDK y JUnit, sin dependencias nuevas. Analiza los imports declarados y el contenido
 * de cada archivo; no resuelve referencias totalmente calificadas dentro del cuerpo de las clases.
 */
class FormationArchitectureTests {

    private static final Path SOURCES = Path.of("src/main/java/com/rubengarcia/choir");
    private static final String BASE = "com.rubengarcia.choir.";
    private static final Pattern PACKAGE = Pattern.compile("^package\\s+([\\w.]+);", Pattern.MULTILINE);
    private static final Pattern IMPORT = Pattern.compile("^import\\s+(?:static\\s+)?([\\w.]+);", Pattern.MULTILINE);
    private static final Pattern SQL =
            Pattern.compile("\\b(SELECT|INSERT INTO|DELETE FROM|UPDATE\\s+\\w+\\s+SET)\\b");

    /**
     * Defecto conocido de producción, no corregido en CHOIR-88 (el paso 4 prohíbe modificar código de
     * producción). La capa de aplicación inspecciona el SQLSTATE de una excepción R2DBC para detectar la
     * violación de unicidad. Si el defecto se corrige, esta prueba falla y la excepción debe quitarse.
     */
    private static final Map<String, String> KNOWN_APPLICATION_INFRASTRUCTURE = Map.of(
            "formation/application/usecase/FormationMobilityConstraintService.java",
            "io.r2dbc.spi.R2dbcException");

    private static final List<String> INFRASTRUCTURE_PREFIXES = List.of(
            "io.r2dbc.", "org.springframework.r2dbc.", "org.springframework.data.r2dbc.",
            "org.springframework.data.relational.", "org.springframework.jdbc.");

    private static final List<String> WEB_OR_PERSISTENCE_PREFIXES = List.of(
            "org.springframework.web.", "org.springframework.http.", "org.springframework.data.",
            "org.springframework.r2dbc.", "org.springframework.jdbc.", "io.r2dbc.");

    record Source(String relativePath, String packageName, String content, List<String> imports) {
    }

    @Test
    void formationDomainDoesNotDependOnTransitionOrAdapters() {
        List<String> offenders = violations(sources("formation.domain"),
                i -> i.startsWith(BASE + "transition.") || i.startsWith(BASE + "formation.adapter."));

        assertTrue(offenders.isEmpty(), "formation.domain depende de transition o de adapters: " + offenders);
    }

    @Test
    void formationDomainDoesNotDependOnWebOrPersistenceInfrastructure() {
        List<String> offenders = violations(sources("formation.domain"), i -> startsWithAny(i, WEB_OR_PERSISTENCE_PREFIXES));

        assertTrue(offenders.isEmpty(),
                "formation.domain depende de Spring Web, R2DBC o persistencia: " + offenders);
    }

    @Test
    void formationApplicationDependsOnPortsNotOnInfrastructure() {
        List<String> offenders = violations(sources("formation.application"),
                i -> i.startsWith(BASE + "formation.adapter.") || startsWithAny(i, INFRASTRUCTURE_PREFIXES));

        List<String> unexpected = offenders.stream().filter(o -> !isKnown(o)).toList();
        assertTrue(unexpected.isEmpty(), "formation.application depende de infraestructura: " + unexpected);

        for (Map.Entry<String, String> known : KNOWN_APPLICATION_INFRASTRUCTURE.entrySet()) {
            assertTrue(offenders.contains(known.getKey() + " -> " + known.getValue()),
                    "excepción conocida ya resuelta; quitarla de KNOWN_APPLICATION_INFRASTRUCTURE: "
                            + known.getKey());
        }
    }

    @Test
    void controllersDoNotAccessPersistenceDirectly() {
        List<String> offenders = violations(sources("formation.adapter.in"),
                i -> i.startsWith(BASE + "formation.adapter.out.")
                        || (i.startsWith(BASE) && lastSegment(i).endsWith("Repository")));

        assertTrue(offenders.isEmpty(),
                "los controladores acceden a persistencia directamente: " + offenders);
    }

    @Test
    void sqlStaysInPersistenceAdapters() {
        List<String> offenders = new ArrayList<>();
        for (Source source : sources("formation.domain")) {
            if (SQL.matcher(source.content()).find()) {
                offenders.add(source.relativePath());
            }
        }
        for (Source source : sources("formation.application")) {
            if (SQL.matcher(source.content()).find()) {
                offenders.add(source.relativePath());
            }
        }
        for (Source source : sources("formation.adapter.in")) {
            if (SQL.matcher(source.content()).find()) {
                offenders.add(source.relativePath());
            }
        }

        assertTrue(offenders.isEmpty(), "SQL fuera de los adapters de persistencia: " + offenders);
    }

    @Test
    void crossDomainDependenciesUseContractsOnly() {
        List<String> offenders = new ArrayList<>();
        offenders.addAll(violations(sources("formation"),
                i -> i.startsWith(BASE + "transition.") && isPersistenceDetail(i)));
        offenders.addAll(violations(sources("transition"),
                i -> i.startsWith(BASE + "formation.") && isPersistenceDetail(i)));

        assertTrue(offenders.isEmpty(),
                "dependencias entre dominios que usan adapters, repositorios o entidades: " + offenders);
    }

    @Test
    void constraintValidatorCompositionStaysInsideFormation() {
        List<String> validatorNames = List.of(
                "CompositeFormationConstraintValidator",
                "LockedPositionValidator",
                "ProximityConstraintValidator",
                "MobilityConstraintValidator",
                "FormationConstraintValidationResult");
        List<Source> outside = allSources().stream()
                .filter(s -> !isWithin(s.packageName(), "formation"))
                .toList();

        List<String> offenders = violations(outside,
                i -> i.startsWith(BASE + "formation.") && validatorNames.stream().anyMatch(i::endsWith));

        assertTrue(offenders.isEmpty(),
                "validadores de formation usados fuera de formation: " + offenders);
    }

    @Test
    void scannerCoversFormationDomainSources() {
        assertTrue(sources("formation.domain").size() >= 30,
                "el escáner debe encontrar las fuentes del dominio de formation");
    }

    // ------------------------------------------------------------------

    private static boolean isKnown(String violation) {
        return KNOWN_APPLICATION_INFRASTRUCTURE.entrySet().stream()
                .anyMatch(e -> violation.equals(e.getKey() + " -> " + e.getValue()));
    }

    private static boolean isPersistenceDetail(String i) {
        return i.contains(".adapter.") || i.endsWith("Repository") || i.endsWith("Entity");
    }

    private static boolean isWithin(String packageName, String module) {
        String prefix = BASE + module;
        return packageName.equals(prefix) || packageName.startsWith(prefix + ".");
    }

    private static boolean startsWithAny(String value, List<String> prefixes) {
        return prefixes.stream().anyMatch(value::startsWith);
    }

    private static String lastSegment(String qualifiedName) {
        return qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
    }

    private static List<String> violations(List<Source> sources, Predicate<String> forbidden) {
        List<String> found = new ArrayList<>();
        for (Source source : sources) {
            for (String imported : source.imports()) {
                if (forbidden.test(imported)) {
                    found.add(source.relativePath() + " -> " + imported);
                }
            }
        }
        return found;
    }

    private static List<Source> sources(String module) {
        return allSources().stream()
                .filter(s -> isWithin(s.packageName(), module))
                .toList();
    }

    private static List<Source> allSources() {
        try (Stream<Path> files = Files.walk(SOURCES)) {
            return files
                    .filter(p -> p.toString().endsWith(".java"))
                    .map(FormationArchitectureTests::read)
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static Source read(Path file) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            Matcher packageMatcher = PACKAGE.matcher(content);
            String packageName = packageMatcher.find() ? packageMatcher.group(1) : "";
            List<String> imports = new ArrayList<>();
            Matcher importMatcher = IMPORT.matcher(content);
            while (importMatcher.find()) {
                imports.add(importMatcher.group(1));
            }
            String relativePath = SOURCES.relativize(file).toString().replace('\\', '/');
            return new Source(relativePath, packageName, content, imports);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}

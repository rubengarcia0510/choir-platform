package com.rubengarcia.choir.scene.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.rubengarcia.choir.scene.application.exception.StageAccessDeniedException;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;

/** Distinción 403 (existe, es ajeno) y 404 (no existe) en el adaptador de autorización de escena. */
@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class StageAuthorizationAdapterTests {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final long MISSING_ID = 999_999_999L;

    private final StageAuthorizationAdapter authorization;
    private final StagePersistenceFixture fixture;

    StageAuthorizationAdapterTests(StageAuthorizationAdapter authorization, JdbcTemplate jdbc) {
        this.authorization = authorization;
        this.fixture = new StagePersistenceFixture(jdbc);
    }

    @AfterEach
    void cleanUp() {
        fixture.cleanup();
    }

    @Test
    void ownerIsAuthorizedForChoirAndOccurrence() {
        StagePersistenceFixture.Context owner = fixture.create("owner");

        StepVerifier.create(authorization.requireOwnedChoir(owner.email(), owner.choirId())).verifyComplete();
        StepVerifier.create(authorization.requireOwnedOccurrence(
                owner.email(), owner.choirId(), owner.concertId(), owner.repertoireItemId())).verifyComplete();
    }

    @Test
    void existingChoirOfAnotherDirectorAnswersForbidden() {
        StagePersistenceFixture.Context owner = fixture.create("dueno");
        StagePersistenceFixture.Context intruder = fixture.create("intruso");

        StepVerifier.create(authorization.requireOwnedChoir(intruder.email(), owner.choirId()))
                .expectError(StageAccessDeniedException.class)
                .verify(TIMEOUT);
    }

    @Test
    void existingOccurrenceOfAnotherDirectorAnswersForbidden() {
        StagePersistenceFixture.Context owner = fixture.create("ocurrencia");
        StagePersistenceFixture.Context intruder = fixture.create("ocurrencia-intruso");

        StepVerifier.create(authorization.requireOwnedOccurrence(
                        intruder.email(), owner.choirId(), owner.concertId(), owner.repertoireItemId()))
                .expectError(StageAccessDeniedException.class)
                .verify(TIMEOUT);
    }

    @Test
    void missingChoirAnswersNotFound() {
        StagePersistenceFixture.Context owner = fixture.create("inexistente");

        StepVerifier.create(authorization.requireOwnedChoir(owner.email(), MISSING_ID))
                .expectErrorSatisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value())
                        .isEqualTo(HttpStatus.NOT_FOUND.value()))
                .verify(TIMEOUT);
    }

    @Test
    void missingWorkInOwnChoirAnswersNotFound() {
        StagePersistenceFixture.Context owner = fixture.create("obra-inexistente");

        StepVerifier.create(authorization.requireOwnedOccurrence(
                        owner.email(), owner.choirId(), owner.concertId(), MISSING_ID))
                .expectErrorSatisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value())
                        .isEqualTo(HttpStatus.NOT_FOUND.value()))
                .verify(TIMEOUT);
    }
}

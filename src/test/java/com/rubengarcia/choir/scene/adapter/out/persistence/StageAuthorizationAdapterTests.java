package com.rubengarcia.choir.scene.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class StageAuthorizationAdapterTests {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

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

        StepVerifier.create(authorization.requireOwnedChoir(owner.email(), owner.choirId()))
                .verifyComplete();
        StepVerifier.create(authorization.requireOwnedOccurrence(
                        owner.email(), owner.choirId(), owner.concertId(), owner.repertoireItemId()))
                .verifyComplete();
    }

    @Test
    void anotherDirectorIsRejectedForTheChoir() {
        StagePersistenceFixture.Context owner = fixture.create("dueno");
        StagePersistenceFixture.Context intruder = fixture.create("intruso");

        StepVerifier.create(authorization.requireOwnedChoir(intruder.email(), owner.choirId()))
                .expectErrorSatisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND))
                .verify(TIMEOUT);
    }

    @Test
    void anotherDirectorIsRejectedForTheOccurrence() {
        StagePersistenceFixture.Context owner = fixture.create("ocurrencia");
        StagePersistenceFixture.Context intruder = fixture.create("ocurrencia-intruso");

        StepVerifier.create(authorization.requireOwnedOccurrence(
                        intruder.email(), owner.choirId(), owner.concertId(), owner.repertoireItemId()))
                .expectErrorSatisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND))
                .verify(TIMEOUT);
    }
}

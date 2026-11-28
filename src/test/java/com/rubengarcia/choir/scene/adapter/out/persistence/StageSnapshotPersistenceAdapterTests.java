package com.rubengarcia.choir.scene.adapter.out.persistence;

import static com.rubengarcia.choir.scene.adapter.out.persistence.StagePersistenceFixture.assertSameDetails;
import static com.rubengarcia.choir.scene.adapter.out.persistence.StagePersistenceFixture.assertSameGeometry;
import static com.rubengarcia.choir.scene.adapter.out.persistence.StagePersistenceFixture.bd;
import static com.rubengarcia.choir.scene.adapter.out.persistence.StagePersistenceFixture.element;
import static com.rubengarcia.choir.scene.adapter.out.persistence.StagePersistenceFixture.fullTemplate;
import static com.rubengarcia.choir.scene.adapter.out.persistence.StagePersistenceFixture.p;
import static org.assertj.core.api.Assertions.assertThat;

import com.rubengarcia.choir.scene.domain.StageBoundary;
import com.rubengarcia.choir.scene.domain.StageElementType;
import com.rubengarcia.choir.scene.domain.StageSnapshot;
import com.rubengarcia.choir.scene.domain.StageTemplate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import reactor.test.StepVerifier;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class StageSnapshotPersistenceAdapterTests {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final StageSnapshotPersistenceAdapter snapshots;
    private final StageTemplatePersistenceAdapter templates;
    private final StagePersistenceFixture fixture;

    StageSnapshotPersistenceAdapterTests(
            StageSnapshotPersistenceAdapter snapshots,
            StageTemplatePersistenceAdapter templates,
            JdbcTemplate jdbc) {
        this.snapshots = snapshots;
        this.templates = templates;
        this.fixture = new StagePersistenceFixture(jdbc);
    }

    @AfterEach
    void cleanUp() {
        fixture.cleanup();
    }

    @Test
    void insertsACompleteSnapshotAndRecoversItWithoutReadingTheTemplate() {
        StagePersistenceFixture.Context ctx = fixture.create("snapshot");
        StageTemplate saved = templates.save(fullTemplate(ctx.choirId(), "Plantilla base")).block(TIMEOUT);
        StageSnapshot snapshot = StageSnapshot.of(saved, ctx.concertId(), ctx.repertoireItemId(),
                LocalDateTime.now());

        StageSnapshot stored = snapshots.insert(snapshot).block(TIMEOUT);
        StageSnapshot recovered = snapshots.findForInclusion(ctx.choirId(), ctx.concertId(),
                ctx.repertoireItemId()).block(TIMEOUT);

        assertThat(stored.id()).isNotNull();
        assertThat(recovered.id()).isEqualTo(stored.id());
        assertThat(recovered.sourceTemplateId()).isEqualTo(saved.id());
        assertThat(recovered.name()).isEqualTo("Plantilla base");
        assertSameDetails(saved.boundary(), saved.accesses(), saved.elements(),
                recovered.boundary(), recovered.accesses(), recovered.elements());
    }

    @Test
    void snapshotDoesNotChangeWhenTheTemplateIsModifiedLater() {
        StagePersistenceFixture.Context ctx = fixture.create("independiente");
        StageTemplate original = templates.save(fullTemplate(ctx.choirId(), "Original")).block(TIMEOUT);
        snapshots.insert(StageSnapshot.of(original, ctx.concertId(), ctx.repertoireItemId(), LocalDateTime.now()))
                .block(TIMEOUT);

        StageTemplate modified = new StageTemplate(original.id(), ctx.choirId(), "Modificada",
                bd("900"), bd("700"),
                new StageBoundary(List.of(p("0", "0"), p("900", "0"), p("900", "700"), p("0", "700"))),
                List.of(), List.of(element("Banco", StageElementType.BENCH, "100", "100", "200", "40", "0")),
                true);
        templates.save(modified).block(TIMEOUT);

        StageSnapshot recovered = snapshots.findForInclusion(ctx.choirId(), ctx.concertId(),
                ctx.repertoireItemId()).block(TIMEOUT);
        assertThat(recovered.name()).isEqualTo("Original");
        assertThat(recovered.widthCm()).isEqualByComparingTo("800");
        assertSameDetails(original.boundary(), original.accesses(), original.elements(),
                recovered.boundary(), recovered.accesses(), recovered.elements());
    }

    @Test
    void secondSnapshotForTheSameInclusionIsRejected() {
        StagePersistenceFixture.Context ctx = fixture.create("duplicado");
        StageTemplate template = templates.save(fullTemplate(ctx.choirId(), "Escenario")).block(TIMEOUT);
        snapshots.insert(StageSnapshot.of(template, ctx.concertId(), ctx.repertoireItemId(), LocalDateTime.now()))
                .block(TIMEOUT);

        StepVerifier.create(snapshots.insert(
                        StageSnapshot.of(template, ctx.concertId(), ctx.repertoireItemId(), LocalDateTime.now())))
                .expectError(DataIntegrityViolationException.class)
                .verify(TIMEOUT);
    }

    @Test
    void snapshotsOfAnotherChoirAreNeverReturned() {
        StagePersistenceFixture.Context mine = fixture.create("mio");
        StagePersistenceFixture.Context other = fixture.create("otro");
        StageTemplate template = templates.save(fullTemplate(mine.choirId(), "Mío")).block(TIMEOUT);
        snapshots.insert(StageSnapshot.of(template, mine.concertId(), mine.repertoireItemId(), LocalDateTime.now()))
                .block(TIMEOUT);

        StepVerifier.create(snapshots.findForInclusion(other.choirId(), mine.concertId(), mine.repertoireItemId()))
                .expectNextCount(0)
                .verifyComplete();
    }
}

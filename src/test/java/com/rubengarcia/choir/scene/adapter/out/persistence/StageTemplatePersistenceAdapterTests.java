package com.rubengarcia.choir.scene.adapter.out.persistence;

import static com.rubengarcia.choir.scene.adapter.out.persistence.StagePersistenceFixture.assertSameGeometry;
import static com.rubengarcia.choir.scene.adapter.out.persistence.StagePersistenceFixture.bd;
import static com.rubengarcia.choir.scene.adapter.out.persistence.StagePersistenceFixture.element;
import static com.rubengarcia.choir.scene.adapter.out.persistence.StagePersistenceFixture.fullTemplate;
import static com.rubengarcia.choir.scene.adapter.out.persistence.StagePersistenceFixture.p;
import static org.assertj.core.api.Assertions.assertThat;

import com.rubengarcia.choir.scene.domain.StageAccess;
import com.rubengarcia.choir.scene.domain.StageBoundary;
import com.rubengarcia.choir.scene.domain.StageElementType;
import com.rubengarcia.choir.scene.domain.StageTemplate;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class StageTemplatePersistenceAdapterTests {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final StageTemplatePersistenceAdapter adapter;
    private final JdbcTemplate jdbc;
    private final StagePersistenceFixture fixture;

    StageTemplatePersistenceAdapterTests(StageTemplatePersistenceAdapter adapter, JdbcTemplate jdbc) {
        this.adapter = adapter;
        this.jdbc = jdbc;
        this.fixture = new StagePersistenceFixture(jdbc);
    }

    @AfterEach
    void cleanUp() {
        fixture.cleanup();
    }

    @Test
    void createsAndRecoversACompleteTemplateWithEveryElementType() {
        StagePersistenceFixture.Context ctx = fixture.create("plantilla");
        StageTemplate draft = fullTemplate(ctx.choirId(), "Escenario completo");

        StageTemplate saved = adapter.save(draft).block(TIMEOUT);
        StageTemplate loaded = adapter.findById(ctx.choirId(), saved.id()).block(TIMEOUT);

        assertThat(saved.id()).isNotNull();
        assertSameGeometry(draft, loaded);
        assertThat(loaded.elements()).extracting(e -> e.type()).containsExactly(
                StageElementType.BENCH, StageElementType.PIANO, StageElementType.FURNITURE,
                StageElementType.OBSTACLE, StageElementType.STEP, StageElementType.PLATFORM);
    }

    @Test
    void preservesExactCoordinatesAndElevationsWithThreeDecimals() {
        StagePersistenceFixture.Context ctx = fixture.create("decimales");
        StageTemplate draft = new StageTemplate(null, ctx.choirId(), "Decimales", bd("800"), bd("600"),
                new StageBoundary(List.of(p("0", "0"), p("800", "0"), p("800", "600"), p("0", "600"))),
                List.of(), List.of(element("Plataforma", StageElementType.PLATFORM,
                        "10.125", "20.250", "100.500", "50.001", "40.125")),
                false);

        StageTemplate loaded = adapter.save(draft)
                .flatMap(saved -> adapter.findById(ctx.choirId(), saved.id()))
                .block(TIMEOUT);

        assertThat(loaded.elements().get(0).x()).isEqualByComparingTo("10.125");
        assertThat(loaded.elements().get(0).widthCm()).isEqualByComparingTo("100.500");
        assertThat(loaded.elements().get(0).depthCm()).isEqualByComparingTo("50.001");
        assertThat(loaded.elements().get(0).elevationCm()).isEqualByComparingTo("40.125");
    }

    @Test
    void updateReplacesDetailsInOrderAndPersistsArchivedState() {
        StagePersistenceFixture.Context ctx = fixture.create("actualizacion");
        StageTemplate saved = adapter.save(fullTemplate(ctx.choirId(), "Original")).block(TIMEOUT);
        StageTemplate replacement = new StageTemplate(saved.id(), ctx.choirId(), "Modificado",
                bd("900"), bd("700"),
                new StageBoundary(List.of(p("0", "0"), p("900", "0"), p("900", "700"), p("0", "700"))),
                List.of(new StageAccess("Acceso nuevo", p("0", "100"), p("0", "200"))),
                List.of(element("Banco", StageElementType.BENCH, "100", "100", "200", "40", "0")),
                true);

        StageTemplate loaded = adapter.save(replacement)
                .flatMap(updated -> adapter.findById(ctx.choirId(), saved.id()))
                .block(TIMEOUT);

        assertSameGeometry(replacement, loaded);
        assertThat(loaded.archived()).isTrue();
        assertThat(loaded.elements()).hasSize(1);
    }

    @Test
    void templatesOfAnotherChoirAreNeverReturned() {
        StagePersistenceFixture.Context mine = fixture.create("propio");
        StagePersistenceFixture.Context other = fixture.create("ajeno");
        StageTemplate ownTemplate = adapter.save(fullTemplate(mine.choirId(), "Mío")).block(TIMEOUT);
        adapter.save(fullTemplate(other.choirId(), "Ajeno")).block(TIMEOUT);

        List<StageTemplate> listed = adapter.findByChoir(mine.choirId()).collectList().block(TIMEOUT);

        assertThat(listed).extracting(StageTemplate::name).containsExactly("Mío");
        StepVerifier.create(adapter.findById(other.choirId(), ownTemplate.id()))
                .expectNextCount(0)
                .verifyComplete();
    }

    @Test
    void rejectsCoordinatesWithMoreThanThreeDecimalsInsteadOfRounding() {
        StagePersistenceFixture.Context ctx = fixture.create("precision");
        StageTemplate draft = new StageTemplate(null, ctx.choirId(), "Precisión", bd("800"), bd("600"),
                new StageBoundary(List.of(p("0", "0"), p("800", "0"), p("800", "600"), p("0", "600"))),
                List.of(), List.of(element("Banco", StageElementType.BENCH, "1.1234", "0", "200", "40", "0")),
                false);

        StepVerifier.create(adapter.save(draft))
                .expectError(IllegalArgumentException.class)
                .verify(TIMEOUT);
    }
}

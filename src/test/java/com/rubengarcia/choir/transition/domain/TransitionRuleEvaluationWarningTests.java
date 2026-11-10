package com.rubengarcia.choir.transition.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rubengarcia.choir.scene.domain.SceneRuleEvaluator.Status;
import com.rubengarcia.choir.transition.domain.TransitionRuleEvaluationSnapshot.Warning;
import com.rubengarcia.choir.transition.domain.TransitionRuleEvaluationSnapshot.Warning.PublicationImpact;
import org.junit.jupiter.api.Test;

class TransitionRuleEvaluationWarningTests {

    @Test
    void allowsInformationalDirectorVisibilityWarning() {
        assertThatCode(() -> new Warning(
                null,
                0,
                "DIRECTOR_VISIBILITY_NOT_EVALUABLE",
                "La visibilidad del director no puede evaluarse.",
                Status.NOT_EVALUABLE,
                PublicationImpact.INFORMATIONAL))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsInformationalClassificationForOtherWarningCodes() {
        assertThatThrownBy(() -> new Warning(
                null,
                0,
                "OTHER_WARNING",
                "Otra advertencia.",
                Status.NOT_EVALUABLE,
                PublicationImpact.INFORMATIONAL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInformationalClassificationForOtherStatuses() {
        assertThatThrownBy(() -> new Warning(
                null,
                0,
                "DIRECTOR_VISIBILITY_NOT_EVALUABLE",
                "La visibilidad del director no puede evaluarse.",
                Status.SATISFIED,
                PublicationImpact.INFORMATIONAL))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

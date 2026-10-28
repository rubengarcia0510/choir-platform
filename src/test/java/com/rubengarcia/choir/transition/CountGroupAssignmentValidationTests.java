package com.rubengarcia.choir.transition;

import static org.assertj.core.api.Assertions.assertThat;

import com.rubengarcia.choir.transition.application.model.CountGroupAssignment;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CountGroupAssignmentValidationTests {

    @Test
    void rejectsAssignmentWithoutGroupOrder() {
        CountGroupAssignment assignment =
                new CountGroupAssignment(101L, null, java.util.List.of(11L, 12L, 13L, 14L));

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();

            Set<String> invalidProperties = validator.validate(assignment)
                    .stream()
                    .map(error -> error.getPropertyPath().toString())
                    .collect(java.util.stream.Collectors.toSet());

            assertThat(invalidProperties).contains("groupOrder");
        }
    }

    @Test
    void acceptsAssignmentWithGroupOrderZero() {
        CountGroupAssignment assignment =
                new CountGroupAssignment(101L, 0, java.util.List.of(11L, 12L, 13L, 14L));

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();

            assertThat(validator.validate(assignment)).isEmpty();
        }
    }
}

package com.rubengarcia.choir.transition.application.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record CountGroupAssignment(
        @Positive long ruleRevisionId,
        @PositiveOrZero int groupOrder,
        @NotNull @Size(min = 1)
        List<@NotNull @Positive Long> singerIds) {

    public CountGroupAssignment {
        singerIds = singerIds == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(singerIds));
    }
}

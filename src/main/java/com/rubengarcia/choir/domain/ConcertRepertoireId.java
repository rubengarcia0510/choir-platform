package com.rubengarcia.choir.domain;

import java.io.Serializable;
import java.util.Objects;

public class ConcertRepertoireId implements Serializable {

    private final Long concertId;
    private final Long repertoireItemId;

    public ConcertRepertoireId(Long concertId, Long repertoireItemId) {
        this.concertId = Objects.requireNonNull(concertId, "concertId");
        this.repertoireItemId = Objects.requireNonNull(
                repertoireItemId, "repertoireItemId");
    }

    public Long getConcertId() {
        return concertId;
    }

    public Long getRepertoireItemId() {
        return repertoireItemId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ConcertRepertoireId that)) {
            return false;
        }
        return Objects.equals(concertId, that.concertId)
                && Objects.equals(repertoireItemId, that.repertoireItemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(concertId, repertoireItemId);
    }
}

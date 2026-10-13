package com.rubengarcia.choir.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ConcertRepertoireId implements Serializable {
@Column(name = "concert_id")
private Long concertId;

@Column(name = "repertoire_item_id")
private Long repertoireItemId;

protected ConcertRepertoireId() {}

public ConcertRepertoireId(Long concertId, Long repertoireItemId) {
    this.concertId = concertId;
    this.repertoireItemId = repertoireItemId;
}

public Long getConcertId() { return concertId; }
public Long getRepertoireItemId() { return repertoireItemId; }

@Override
public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof ConcertRepertoireId that)) return false;
    return Objects.equals(concertId, that.concertId)
        && Objects.equals(repertoireItemId, that.repertoireItemId);
}

@Override
public int hashCode() {
    return Objects.hash(concertId, repertoireItemId);
}

}

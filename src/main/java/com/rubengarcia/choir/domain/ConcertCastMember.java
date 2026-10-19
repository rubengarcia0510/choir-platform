package com.rubengarcia.choir.domain;

public record ConcertCastMember(
        Long singerId,
        String name,
        String voicePart,
        Long voiceSectionId,
        String voiceSectionName,
        Integer divisionNumber) {
}

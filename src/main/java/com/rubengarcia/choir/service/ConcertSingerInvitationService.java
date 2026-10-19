package com.rubengarcia.choir.service;

import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface ConcertSingerInvitationService {

    record CreatedInvitation(
            long invitationId, long concertId, long singerId,
            String token, LocalDateTime expiresAt) {}

    record RepertoireEntry(
            long repertoireItemId, String title, String composer,
            String arranger, int orderIndex) {}

    record CastMember(
            Long singerId, String name, String voicePart,
            String voiceSection, Integer divisionNumber,
            boolean invitedSinger) {}

    record VocalAssignment(
            long repertoireItemId, String workTitle, String singerName,
            String voicePart, String voiceSection, int divisionNumber,
            boolean invitedSinger) {}

    record ConcertView(
            long concertId, String title, LocalDate concertDate,
            String venue, String invitedSingerName,
            List<RepertoireEntry> repertoire,
            List<CastMember> cast,
            List<VocalAssignment> vocalAssignments) {}

    Mono<CreatedInvitation> create(
            String email, long choirId, long concertId,
            long singerId, LocalDateTime expiresAt);

    Mono<Void> revoke(
            String email, long choirId, long concertId, long invitationId);

    Mono<ConcertView> viewByToken(String token);
}

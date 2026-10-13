package com.rubengarcia.choir.domain;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class DomainPersistenceTests {

@Autowired
private EntityManager entityManager;

@Autowired
private EntityManagerFactory entityManagerFactory;

@Test
void allDomainEntitiesAreRegisteredWithJpa() {
    Set<String> entityNames = entityManagerFactory.getMetamodel()
            .getEntities()
            .stream()
            .map(entity -> entity.getJavaType().getSimpleName())
            .collect(Collectors.toSet());

    assertThat(entityNames).contains(
            "Director",
            "Choir",
            "Singer",
            "Concert",
            "ConcertCastSnapshot",
            "RepertoireItem",
            "ConcertRepertoire",
            "Formation",
            "FormationVersion",
            "FormationPosition",
            "Transition",
            "TransitionVersion",
            "Absence"
    );
}

@Test
void concertCastSnapshotKeepsHistoricalSingerDetails() {
    Director director = new Director();
    director.setDisplayName("Director de prueba");
    director.setEmail("director-domain-test@example.test");
    entityManager.persist(director);

    Choir choir = new Choir();
    choir.setDirector(director);
    choir.setName("Coro de prueba");
    entityManager.persist(choir);

    Singer singer = new Singer();
    singer.setChoir(choir);
    singer.setDisplayName("Nombre original");
    singer.setVoicePart("SOPRANO");
    entityManager.persist(singer);

    Concert concert = new Concert();
    concert.setChoir(choir);
    concert.setTitle("Concierto de prueba");
    entityManager.persist(concert);

    ConcertCastSnapshot snapshot = new ConcertCastSnapshot();
    snapshot.setConcert(concert);
    snapshot.setSourceSinger(singer);
    snapshot.setSingerNameSnapshot("Nombre original");
    snapshot.setVoicePartSnapshot("SOPRANO");
    entityManager.persist(snapshot);
    entityManager.flush();

    singer.setDisplayName("Nombre actualizado");
    singer.setVoicePart("ALTO");
    entityManager.flush();
    entityManager.clear();

    ConcertCastSnapshot storedSnapshot =
            entityManager.find(ConcertCastSnapshot.class, snapshot.getId());

    assertThat(storedSnapshot.getSingerNameSnapshot())
            .isEqualTo("Nombre original");
    assertThat(storedSnapshot.getVoicePartSnapshot())
            .isEqualTo("SOPRANO");
    assertThat(storedSnapshot.getSourceSinger().getDisplayName())
            .isEqualTo("Nombre actualizado");
}

@Test
void publishedFormationVersionCanCoexistWithANewDraftVersion() {
    Director director = new Director();
    director.setDisplayName("Director de versiones");
    director.setEmail("director-versions-test@example.test");
    entityManager.persist(director);

    Choir choir = new Choir();
    choir.setDirector(director);
    choir.setName("Coro de versiones");
    entityManager.persist(choir);

    Formation formation = new Formation();
    formation.setChoir(choir);
    formation.setName("Formación principal");
    entityManager.persist(formation);

    FormationVersion published = new FormationVersion();
    published.setFormation(formation);
    published.setVersionNumber(1);
    published.setStatus("PUBLISHED");
    entityManager.persist(published);

    FormationVersion draft = new FormationVersion();
    draft.setFormation(formation);
    draft.setVersionNumber(2);
    draft.setStatus("DRAFT");
    entityManager.persist(draft);
    entityManager.flush();

    Long publishedId = published.getId();
    Long draftId = draft.getId();
    entityManager.clear();

    FormationVersion storedPublished =
            entityManager.find(FormationVersion.class, publishedId);
    FormationVersion storedDraft =
            entityManager.find(FormationVersion.class, draftId);

    assertThat(storedPublished.getStatus()).isEqualTo("PUBLISHED");
    assertThat(storedPublished.getVersionNumber()).isEqualTo(1);
    assertThat(storedDraft.getStatus()).isEqualTo("DRAFT");
    assertThat(storedDraft.getVersionNumber()).isEqualTo(2);
}

}

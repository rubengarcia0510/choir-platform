package com.rubengarcia.choir.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.server.WebSession;
import org.springframework.web.server.session.InMemoryWebSessionStore;
import org.springframework.web.server.session.WebSessionManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
class DirectorSessionExpirationTests {

    @Autowired
    private WebSessionManager webSessionManager;

    @Test
    void sessionsUseConfiguredThirtyMinuteIdleTimeout() {
        WebSession session = webSessionManager.getSession(
                MockServerWebExchange.from(
                        MockServerHttpRequest.get("/").build()
                )
        ).block();

        assertNotNull(session);
        assertEquals(
                Duration.ofMinutes(30),
                session.getMaxIdleTime(),
                "La sesión debe tener un tiempo máximo de inactividad de 30 minutos"
        );
    }

    @Test
    void inactiveSessionExpiresAfterConfiguredIdleTimeout() {
        Instant start = Instant.parse("2026-01-01T12:00:00Z");
        Clock clock = Clock.fixed(start, ZoneOffset.UTC);

        InMemoryWebSessionStore store = new InMemoryWebSessionStore();
        store.setClock(clock);
        store.setDefaultMaxIdleTime(Duration.ofMinutes(30));

        WebSession session = store.createWebSession().block();
        assertNotNull(session);

        session.start();
        session.save().block();

        String sessionId = session.getId();

        assertNotNull(
                store.retrieveSession(sessionId).block(),
                "La sesión debe existir antes de expirar"
        );

        store.setClock(Clock.offset(clock, Duration.ofMinutes(31)));

        assertNull(
                store.retrieveSession(sessionId).block(),
                "La sesión inactiva debe dejar de estar disponible después de 30 minutos"
        );
    }
}

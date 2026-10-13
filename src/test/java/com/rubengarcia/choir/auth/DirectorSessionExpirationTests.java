package com.rubengarcia.choir.auth;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.server.WebSession;
import org.springframework.web.server.session.WebSessionManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
}

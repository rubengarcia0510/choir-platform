package com.rubengarcia.choir.auth;

import com.rubengarcia.choir.domain.Director;
import com.rubengarcia.choir.repository.DirectorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocalDevelopmentDirectorInitializerTests {

    private static final String NAME = "Director Local";
    private static final String EMAIL = "director@example.test";
    private static final String PASSWORD = "  Local-Password-2026!  ";

    @Mock
    private DirectorRepository directorRepository;

    @Mock
    private Environment environment;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private LocalDevelopmentDirectorInitializer initializer;

    @BeforeEach
    void configureEnvironment() {
        lenient().when(environment.getProperty("CHOIR_DEV_DIRECTOR_NAME"))
                .thenReturn(NAME);
        lenient().when(environment.getProperty("CHOIR_DEV_DIRECTOR_EMAIL"))
                .thenReturn("  DIRECTOR@EXAMPLE.TEST  ");
        lenient().when(environment.getProperty("CHOIR_DEV_DIRECTOR_PASSWORD"))
                .thenReturn(PASSWORD);

        initializer = new LocalDevelopmentDirectorInitializer(
                directorRepository,
                passwordEncoder,
                environment
        );
    }

    @Test
    void createsVerifiedDirectorWithBcryptPasswordAndNormalizedEmail() {
        when(directorRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Mono.empty());
        when(directorRepository.save(any(Director.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        initializer.run(new DefaultApplicationArguments(new String[0]));

        ArgumentCaptor<Director> captor = ArgumentCaptor.forClass(Director.class);
        verify(directorRepository).save(captor.capture());
        verify(directorRepository).findByEmailIgnoreCase(EMAIL);

        Director saved = captor.getValue();

        assertEquals(NAME, saved.getDisplayName());
        assertEquals(EMAIL, saved.getEmail());
        assertTrue(saved.isEmailVerified());
        assertNotEquals(PASSWORD, saved.getPasswordHash());
        assertTrue(saved.getPasswordHash().startsWith("$2"));
        assertTrue(passwordEncoder.matches(PASSWORD, saved.getPasswordHash()));
    }

    @Test
    void doesNotModifyExistingDirector() {
        Director existing = Director.create("Existing Director", EMAIL);
        existing.setPasswordHash("existing-hash");
        existing.setEmailVerified(false);

        when(directorRepository.findByEmailIgnoreCase(EMAIL))
                .thenReturn(Mono.just(existing));

        initializer.run(new DefaultApplicationArguments(new String[0]));

        verify(directorRepository, never()).save(any(Director.class));
        assertEquals("existing-hash", existing.getPasswordHash());
        assertFalse(existing.isEmailVerified());
    }

    @Test
    void failsWhenRequiredEnvironmentVariableIsMissing() {
        when(environment.getProperty("CHOIR_DEV_DIRECTOR_PASSWORD")).thenReturn(" ");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> initializer.run(new DefaultApplicationArguments(new String[0]))
        );

        assertTrue(exception.getMessage().contains("CHOIR_DEV_DIRECTOR_PASSWORD"));
        assertFalse(exception.getMessage().contains(PASSWORD));
        verifyNoInteractions(directorRepository);
    }

    @Test
    void rejectsInvalidEmailBeforeAccessingRepository() {
        when(environment.getProperty("CHOIR_DEV_DIRECTOR_EMAIL"))
                .thenReturn("not-an-email");

        assertThrows(
                IllegalStateException.class,
                () -> initializer.run(new DefaultApplicationArguments(new String[0]))
        );

        verifyNoInteractions(directorRepository);
    }

    @Test
    void rejectsPasswordShorterThanTwelveCharacters() {
        when(environment.getProperty("CHOIR_DEV_DIRECTOR_PASSWORD"))
                .thenReturn("short");

        assertThrows(
                IllegalStateException.class,
                () -> initializer.run(new DefaultApplicationArguments(new String[0]))
        );

        verifyNoInteractions(directorRepository);
    }

    @Test
    void rejectsPasswordLongerThanTwoHundredCharacters() {
        when(environment.getProperty("CHOIR_DEV_DIRECTOR_PASSWORD"))
                .thenReturn("a".repeat(201));

        assertThrows(
                IllegalStateException.class,
                () -> initializer.run(new DefaultApplicationArguments(new String[0]))
        );

        verifyNoInteractions(directorRepository);
    }

    @Test
    void rejectsBlankNameBeforeAccessingRepository() {
        when(environment.getProperty("CHOIR_DEV_DIRECTOR_NAME"))
                .thenReturn("   ");

        assertThrows(
                IllegalStateException.class,
                () -> initializer.run(new DefaultApplicationArguments(new String[0]))
        );

        verifyNoInteractions(directorRepository);
    }

    @Test
    void rejectsNameLongerThanOneHundredSixtyCharacters() {
        when(environment.getProperty("CHOIR_DEV_DIRECTOR_NAME"))
                .thenReturn("a".repeat(161));

        assertThrows(
                IllegalStateException.class,
                () -> initializer.run(new DefaultApplicationArguments(new String[0]))
        );

        verifyNoInteractions(directorRepository);
    }
}

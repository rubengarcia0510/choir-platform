package com.rubengarcia.choir.mail;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDateTime;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmtpVerificationEmailSenderTests {

    private static final String RECIPIENT = "director@example.test";
    private static final String VERIFICATION_URL =
            "https://app.example.test/verify?token=abc123";
    private static final LocalDateTime EXPIRES_AT =
            LocalDateTime.of(2026, 11, 20, 14, 5);

    private JavaMailSender mailSender;
    private MimeMessage message;
    private SmtpVerificationEmailSender sender;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        sender = new SmtpVerificationEmailSender(
                mailSender, "no-reply@example.test", "CHOIR");
    }

    @Test
    void sendsTheVerificationEmailThroughTheMailTransport() throws Exception {
        sender.sendVerificationEmail(
                RECIPIENT, "Ana Directora", VERIFICATION_URL, EXPIRES_AT);

        verify(mailSender).send(message);
    }

    @Test
    void buildsMessageWithRecipientSenderUrlAndExpiration() throws Exception {
        sender.sendVerificationEmail(
                RECIPIENT, "Ana Directora", VERIFICATION_URL, EXPIRES_AT);

        InternetAddress from = (InternetAddress) message.getFrom()[0];
        InternetAddress to =
                (InternetAddress) message.getRecipients(Message.RecipientType.TO)[0];
        String body = (String) message.getContent();

        assertEquals("no-reply@example.test", from.getAddress());
        assertEquals("CHOIR", from.getPersonal());
        assertEquals(RECIPIENT, to.getAddress());
        assertEquals(SmtpVerificationEmailSender.SUBJECT, message.getSubject());
        assertTrue(body.contains("Hola Ana Directora"));
        assertTrue(body.contains(VERIFICATION_URL));
        assertTrue(body.contains("20/11/2026 14:05 UTC"));
        assertTrue(message.getContentType().startsWith("text/plain"));
    }

    @Test
    void transportFailureIsReportedAsDeliveryExceptionWithoutLeakingData() {
        MailSendException transportFailure = new MailSendException("SMTP down");
        doThrow(transportFailure).when(mailSender).send(any(MimeMessage.class));

        VerificationEmailDeliveryException exception = assertThrows(
                VerificationEmailDeliveryException.class,
                () -> sender.sendVerificationEmail(
                        RECIPIENT, "Ana Directora", VERIFICATION_URL, EXPIRES_AT));

        assertSame(transportFailure, exception.getCause());
        assertTrue(exception instanceof IllegalStateException);
        assertFalse(exception.getMessage().contains(RECIPIENT));
        assertFalse(exception.getMessage().contains(VERIFICATION_URL));
    }
}

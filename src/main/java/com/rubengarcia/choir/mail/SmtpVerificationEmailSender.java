package com.rubengarcia.choir.mail;

import com.rubengarcia.choir.auth.VerificationEmailSender;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * Envía el correo de verificación de registro mediante SMTP.
 *
 * <p>El envío es síncrono y bloqueante: el servicio de registro lo invoca en
 * {@code boundedElastic}. Cualquier falla se informa con una excepción no
 * verificada para que la transacción de registro haga rollback. No se
 * registran en logs el destinatario, la URL ni el token.</p>
 */
public class SmtpVerificationEmailSender implements VerificationEmailSender {

    static final String SUBJECT = "Verificá tu correo en CHOIR";

    private static final DateTimeFormatter EXPIRATION_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ROOT);

    private final JavaMailSender mailSender;
    private final String from;
    private final String fromName;

    public SmtpVerificationEmailSender(
            JavaMailSender mailSender,
            String from,
            String fromName) {
        this.mailSender = Objects.requireNonNull(mailSender);
        this.from = Objects.requireNonNull(from);
        this.fromName = Objects.requireNonNull(fromName);
    }

    @Override
    public void sendVerificationEmail(
            String email,
            String displayName,
            String verificationUrl,
            LocalDateTime expiresAt) {
        Objects.requireNonNull(email);
        Objects.requireNonNull(displayName);
        Objects.requireNonNull(verificationUrl);
        Objects.requireNonNull(expiresAt);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from, fromName);
            helper.setTo(email);
            helper.setSubject(SUBJECT);
            helper.setText(
                    buildBody(displayName, verificationUrl, expiresAt), false);
            mailSender.send(message);
        } catch (MailException | MessagingException
                 | UnsupportedEncodingException exception) {
            throw new VerificationEmailDeliveryException(
                    "No se pudo enviar el correo de verificación", exception);
        }
    }

    static String buildBody(
            String displayName,
            String verificationUrl,
            LocalDateTime expiresAt) {
        return "Hola " + displayName + ":\n\n"
                + "Recibimos una solicitud para crear un espacio de dirección "
                + "en CHOIR con este correo.\n\n"
                + "Para verificar tu correo, abrí este enlace:\n"
                + verificationUrl + "\n\n"
                + "El enlace es de un solo uso y es válido hasta el "
                + EXPIRATION_FORMAT.format(expiresAt) + " UTC.\n\n"
                + "Si no hiciste esta solicitud, ignorá este mensaje.\n";
    }
}

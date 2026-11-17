package com.rubengarcia.choir.mail;

/**
 * Indica que no se pudo entregar el correo de verificación al transporte SMTP.
 *
 * <p>Extiende {@link IllegalStateException} para mantener el comportamiento
 * del registro: el error se propaga y la transacción hace rollback.</p>
 */
public class VerificationEmailDeliveryException extends IllegalStateException {

    public VerificationEmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}

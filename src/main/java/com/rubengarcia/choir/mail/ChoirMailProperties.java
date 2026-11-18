package com.rubengarcia.choir.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parámetros propios de CHOIR para el envío de correo. El servidor SMTP y sus
 * credenciales se configuran con las propiedades estándar {@code spring.mail.*}.
 */
@ConfigurationProperties(prefix = "choir.mail")
public class ChoirMailProperties {

    /** Activa el adaptador SMTP. Deshabilitado por defecto. */
    private boolean enabled = false;

    /** Dirección del remitente. Obligatoria cuando el correo está habilitado. */
    private String from;

    /** Nombre visible del remitente. */
    private String fromName = "CHOIR";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }
}

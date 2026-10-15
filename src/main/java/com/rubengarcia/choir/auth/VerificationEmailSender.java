package com.rubengarcia.choir.auth;

import java.time.LocalDateTime;

public interface VerificationEmailSender {

    void sendVerificationEmail(
            String email,
            String displayName,
            String verificationUrl,
            LocalDateTime expiresAt
    );
}

// backend/src/main/java/com/example/app/shared/email/EmailService.java
package com.example.app.shared.email;

public interface EmailService {
    void sendVerificationEmail(String toEmail, String verificationLink);
    void sendPasswordResetEmail(String toEmail, String resetLink);
}

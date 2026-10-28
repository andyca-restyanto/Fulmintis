// backend/src/main/java/com/example/app/modules/auth/exception/InvalidOrExpiredResetTokenException.java
package com.example.app.modules.auth.exception;

public class InvalidOrExpiredResetTokenException extends RuntimeException {
    public InvalidOrExpiredResetTokenException() {
        super("Link reset password tidak valid atau sudah kedaluwarsa. Silakan request ulang.");
    }
}

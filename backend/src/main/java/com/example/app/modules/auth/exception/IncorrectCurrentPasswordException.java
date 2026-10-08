// backend/src/main/java/com/example/app/modules/auth/exception/IncorrectCurrentPasswordException.java
package com.example.app.modules.auth.exception;

public class IncorrectCurrentPasswordException extends RuntimeException {
    public IncorrectCurrentPasswordException() {
        super("Password saat ini salah.");
    }
}

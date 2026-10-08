// backend/src/main/java/com/example/app/modules/auth/exception/EmailAlreadyExistsException.java
package com.example.app.modules.auth.exception;

public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException(String email) {
        super("Email '" + email + "' sudah terdaftar");
    }
}

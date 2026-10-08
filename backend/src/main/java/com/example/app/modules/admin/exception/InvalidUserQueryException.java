// filepath: /backend/src/main/java/com/example/app/modules/admin/exception/InvalidUserQueryException.java
package com.example.app.modules.admin.exception;

/** Kata kunci pencarian di menu User terlalu panjang -> 400. */
public class InvalidUserQueryException extends RuntimeException {
    public InvalidUserQueryException(int maxLength) {
        super("Kata kunci pencarian maksimal " + maxLength + " karakter.");
    }
}

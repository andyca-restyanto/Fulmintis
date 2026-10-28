// filepath: /backend/src/main/java/com/example/app/modules/testcase/exception/InvalidImportFileException.java
package com.example.app.modules.testcase.exception;

/**
 * File import tidak bisa diproses sama sekali (bukan .xlsx, rusak, terlalu
 * besar, kolom wajib tidak ada di header, terlalu banyak baris, kosong).
 * Dipetakan ke 400 { status, message } oleh GlobalExceptionHandler.
 */
public class InvalidImportFileException extends RuntimeException {
    public InvalidImportFileException(String message) {
        super(message);
    }
}

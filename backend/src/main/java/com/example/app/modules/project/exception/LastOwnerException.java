// filepath: /backend/src/main/java/com/example/app/modules/project/exception/LastOwnerException.java
package com.example.app.modules.project.exception;

/**
 * Dilempar saat sebuah aksi (turunkan role / hapus member) akan membuat
 * project TANPA OWNER sama sekali. Dipetakan ke 409.
 */
public class LastOwnerException extends RuntimeException {
    public LastOwnerException() {
        super("Project harus punya minimal 1 OWNER. Jadikan member lain OWNER terlebih dahulu.");
    }
}

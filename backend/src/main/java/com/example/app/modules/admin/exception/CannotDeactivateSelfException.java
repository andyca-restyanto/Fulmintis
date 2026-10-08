// filepath: /backend/src/main/java/com/example/app/modules/admin/exception/CannotDeactivateSelfException.java
package com.example.app.modules.admin.exception;

/** Admin mencoba menonaktifkan dirinya sendiri -> 400. */
public class CannotDeactivateSelfException extends RuntimeException {
    public CannotDeactivateSelfException() {
        super("Anda tidak dapat menonaktifkan akun Anda sendiri.");
    }
}

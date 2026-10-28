// filepath: /backend/src/main/java/com/example/app/modules/admin/validation/PasswordConfirmation.java
package com.example.app.modules.admin.validation;

/**
 * DTO yang punya pasangan password + konfirmasi. Nama method sengaja BUKAN
 * getter (tanpa awalan get/is) supaya tidak ikut sebagai properti JSON.
 */
public interface PasswordConfirmation {
    String enteredPassword();

    String enteredConfirmation();
}

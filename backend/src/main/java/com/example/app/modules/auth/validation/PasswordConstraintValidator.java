// backend/src/main/java/com/example/app/modules/auth/validation/PasswordConstraintValidator.java
package com.example.app.modules.auth.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

public class PasswordConstraintValidator implements ConstraintValidator<ValidPassword, String> {

    // Minimal 9 karakter, 1 huruf besar, 1 huruf kecil, 1 angka, 1 karakter spesial
    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@#$%^&+=!()\\-_.,?*]).{9,}$");

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) return false;
        return PASSWORD_PATTERN.matcher(password).matches();
    }
}

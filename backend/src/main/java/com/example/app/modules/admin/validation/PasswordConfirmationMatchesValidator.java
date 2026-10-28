// filepath: /backend/src/main/java/com/example/app/modules/admin/validation/PasswordConfirmationMatchesValidator.java
package com.example.app.modules.admin.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordConfirmationMatchesValidator
        implements ConstraintValidator<PasswordConfirmationMatches, PasswordConfirmation> {

    @Override
    public boolean isValid(PasswordConfirmation dto, ConstraintValidatorContext context) {
        if (dto.enteredPassword() == null || dto.enteredConfirmation() == null) {
            return true; // biar @NotBlank yang menangani kosong/null
        }

        boolean isValid = dto.enteredPassword().equals(dto.enteredConfirmation());

        if (!isValid) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("confirmPassword")
                    .addConstraintViolation();
        }
        return isValid;
    }
}

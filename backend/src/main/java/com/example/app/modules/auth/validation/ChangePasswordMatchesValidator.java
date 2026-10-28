// backend/src/main/java/com/example/app/modules/auth/validation/ChangePasswordMatchesValidator.java
package com.example.app.modules.auth.validation;

import com.example.app.modules.auth.dto.ChangePasswordRequestDTO;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ChangePasswordMatchesValidator implements ConstraintValidator<ChangePasswordMatches, ChangePasswordRequestDTO> {

    @Override
    public boolean isValid(ChangePasswordRequestDTO dto, ConstraintValidatorContext context) {
        if (dto.getNewPassword() == null || dto.getConfirmNewPassword() == null) {
            return true; // biar @NotBlank yang menangani kosong/null
        }

        boolean isValid = dto.getNewPassword().equals(dto.getConfirmNewPassword());

        if (!isValid) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("confirmNewPassword")
                    .addConstraintViolation();
        }
        return isValid;
    }
}

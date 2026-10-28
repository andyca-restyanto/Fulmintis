// backend/src/main/java/com/example/app/modules/auth/validation/NewPasswordMatchesValidator.java
package com.example.app.modules.auth.validation;

import com.example.app.modules.auth.dto.ResetPasswordRequestDTO;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NewPasswordMatchesValidator implements ConstraintValidator<NewPasswordMatches, ResetPasswordRequestDTO> {

    @Override
    public boolean isValid(ResetPasswordRequestDTO dto, ConstraintValidatorContext context) {
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

// backend/src/main/java/com/example/app/modules/auth/validation/ValidPassword.java
package com.example.app.modules.auth.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = PasswordConstraintValidator.class)
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {
    String message() default "Password minimal 9 karakter dan harus mengandung huruf besar, huruf kecil, angka, dan karakter spesial";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

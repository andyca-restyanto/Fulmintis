// backend/src/main/java/com/example/app/modules/auth/validation/ChangePasswordMatches.java
package com.example.app.modules.auth.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = ChangePasswordMatchesValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ChangePasswordMatches {
    String message() default "Password baru dan konfirmasi password tidak sama";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

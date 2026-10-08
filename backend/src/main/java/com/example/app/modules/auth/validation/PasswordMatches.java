// backend/src/main/java/com/example/app/modules/auth/validation/PasswordMatches.java
package com.example.app.modules.auth.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = PasswordMatchesValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PasswordMatches {
    String message() default "Password dan konfirmasi password tidak sama";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

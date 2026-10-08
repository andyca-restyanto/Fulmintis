// backend/src/main/java/com/example/app/modules/auth/validation/NewPasswordMatches.java
package com.example.app.modules.auth.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = NewPasswordMatchesValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface NewPasswordMatches {
    String message() default "Password baru dan konfirmasi password tidak sama";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

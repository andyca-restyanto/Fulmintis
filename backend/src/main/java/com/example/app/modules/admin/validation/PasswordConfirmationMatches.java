// filepath: /backend/src/main/java/com/example/app/modules/admin/validation/PasswordConfirmationMatches.java
package com.example.app.modules.admin.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = PasswordConfirmationMatchesValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PasswordConfirmationMatches {
    String message() default "Password dan konfirmasi password tidak sama";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

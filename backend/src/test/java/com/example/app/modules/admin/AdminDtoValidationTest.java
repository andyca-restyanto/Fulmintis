// filepath: /backend/src/test/java/com/example/app/modules/admin/AdminDtoValidationTest.java
package com.example.app.modules.admin;

import com.example.app.modules.admin.dto.AdminAcceptInvitationRequestDTO;
import com.example.app.modules.admin.dto.AdminInviteRequestDTO;
import com.example.app.modules.admin.dto.AdminRegisterRequestDTO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminDtoValidationTest {

    private static Validator validator;

    @BeforeAll
    static void init() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private static Set<String> invalidFields(Object dto) {
        return validator.validate(dto).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private AdminRegisterRequestDTO validRegister() {
        AdminRegisterRequestDTO r = new AdminRegisterRequestDTO();
        r.setName("Budi");
        r.setEmail("budi@example.com");
        r.setPassword("Str0ng!Pass");
        r.setConfirmPassword("Str0ng!Pass");
        return r;
    }

    @Test
    void validRegisterHasNoViolationsAndBootstrapCodeIsOptional() {
        assertTrue(invalidFields(validRegister()).isEmpty());
    }

    @Test
    void registerRejectsBlankNameBadEmailWeakPasswordAndMismatch() {
        AdminRegisterRequestDTO r = validRegister();
        r.setName(" ");
        r.setEmail("bukan-email");
        r.setPassword("lemah");
        r.setConfirmPassword("beda");

        Set<String> fields = invalidFields(r);

        assertTrue(fields.containsAll(Set.of("name", "email", "password", "confirmPassword")), fields.toString());
    }

    @Test
    void passwordMismatchIsReportedOnConfirmPasswordField() {
        AdminRegisterRequestDTO r = validRegister();
        r.setConfirmPassword("Str0ng!Pass-beda");

        assertEquals(Set.of("confirmPassword"), invalidFields(r));
    }

    @Test
    void acceptInvitationRequiresTokenStrongPasswordAndMatchingConfirmation() {
        AdminAcceptInvitationRequestDTO r = new AdminAcceptInvitationRequestDTO();
        r.setToken("");
        r.setNewPassword("lemah");
        r.setConfirmPassword("");
        assertTrue(invalidFields(r).containsAll(Set.of("token", "newPassword", "confirmPassword")));

        r.setToken("tok");
        r.setNewPassword("Str0ng!Pass");
        r.setConfirmPassword("Str0ng!Pass-beda");
        assertEquals(Set.of("confirmPassword"), invalidFields(r));

        r.setConfirmPassword("Str0ng!Pass");
        assertTrue(invalidFields(r).isEmpty());
    }

    @Test
    void inviteRequiresNameAndValidEmail() {
        AdminInviteRequestDTO r = new AdminInviteRequestDTO();
        assertEquals(Set.of("name", "email"), invalidFields(r));

        r.setName("Sari");
        r.setEmail("sari@example.com");
        assertTrue(invalidFields(r).isEmpty());
    }
}

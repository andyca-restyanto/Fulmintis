// filepath: /backend/src/test/java/com/example/app/modules/admin/AdminTestSupport.java
package com.example.app.modules.admin;

import com.example.app.modules.auth.entity.User;
import com.example.app.modules.auth.repository.UserRepository;
import com.example.app.modules.usertype.UserTypeCode;
import com.example.app.modules.usertype.entity.UserType;
import com.example.app.modules.usertype.repository.UserTypeRepository;
import com.example.app.shared.security.JwtService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Pembantu test fitur admin: repository tiruan yang "menyimpan" user, JwtService & encoder sungguhan. */
public final class AdminTestSupport {

    public static final PasswordEncoder ENCODER = new BCryptPasswordEncoder(4);

    private AdminTestSupport() {
    }

    public static JwtService jwtService() {
        JwtService jwt = new JwtService();
        ReflectionTestUtils.setField(jwt, "jwtSecret",
                "0123456789012345678901234567890123456789012345678901234567890123");
        ReflectionTestUtils.setField(jwt, "jwtExpirationMinutes", 60L);
        return jwt;
    }

    /** UserRepository tiruan: save() mengembalikan entity yang sama (+ id bila belum ada). */
    public static UserRepository userRepository() {
        UserRepository repo = mock(UserRepository.class);
        when(repo.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            if (u.getId() == null) {
                u.setId(UUID.randomUUID());
            }
            return u;
        });
        return repo;
    }

    public static UserTypeRepository userTypeRepositoryWithAllTypes() {
        UserTypeRepository repo = mock(UserTypeRepository.class);
        for (String code : new String[]{UserTypeCode.FREE, UserTypeCode.VIP_MONTHLY, UserTypeCode.VIP_YEARLY, UserTypeCode.ADMIN}) {
            when(repo.findByCode(code)).thenReturn(Optional.of(UserType.builder().code(code).label(code).build()));
        }
        return repo;
    }

    public static User user(String email, String userType, boolean verified, String rawPassword) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .name("Nama " + email)
                .password(ENCODER.encode(rawPassword))
                .verified(verified)
                .userType(userType)
                .build();
    }
}

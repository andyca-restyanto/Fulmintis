// backend/src/main/java/com/example/app/modules/auth/repository/UserRepository.java
package com.example.app.modules.auth.repository;

import com.example.app.modules.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    boolean existsByEmail(String email);
    Optional<User> findByEmail(String email);
    Optional<User> findByVerificationToken(String verificationToken);
    Optional<User> findByResetPasswordToken(String resetPasswordToken);

    // Dipakai ProjectCollaboratorServiceImpl (search & add member): HANYA
    // user yang sudah verified dan HANYA cocok persis dengan email -- tidak
    // ada lagi pencarian substring/nama yang bisa dipakai memanen daftar user.
    Optional<User> findByEmailAndVerifiedTrue(String email);
}

// backend/src/main/java/com/example/app/modules/auth/repository/UserRepository.java
package com.example.app.modules.auth.repository;

import com.example.app.modules.auth.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Sama seperti {@link #findByEmailAndVerifiedTrue} tapi mengecualikan satu tipe user.
     * Dipakai pencarian/penambahan team member agar akun ADMIN tidak bisa ditemukan
     * maupun dimasukkan ke project user.
     */
    Optional<User> findByEmailAndVerifiedTrueAndUserTypeNot(String email, String userType);

    long countByUserType(String userType);

    Optional<User> findByAdminInvitationToken(String adminInvitationToken);

    /** Daftar akun satu tipe (dipakai menu Admin); urutan & ukuran halaman ditentukan Pageable. */
    Page<User> findAllByUserType(String userType, Pageable pageable);

    /** Jumlah akun satu tipe yang aktif dan sudah terverifikasi (batas aman "admin aktif terakhir"). */
    long countByUserTypeAndActiveTrueAndVerifiedTrue(String userType);

    /**
     * Kunci advisory PostgreSQL level-transaksi: melepas otomatis saat commit/rollback.
     * Menyerialkan pendaftaran admin pertama (cek-lalu-simpan) antar request/instance.
     * Harus dipanggil di dalam transaksi. Mengembalikan 1 (nilai tidak dipakai).
     */
    @Query(value = "select 1 from (select pg_advisory_xact_lock(:key)) as lock_row", nativeQuery = true)
    Integer lockAdvisory(@Param("key") long key);
}

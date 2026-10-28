// backend/src/main/java/com/example/app/modules/usertype/repository/UserTypeRepository.java
package com.example.app.modules.usertype.repository;

import com.example.app.modules.usertype.entity.UserType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserTypeRepository extends JpaRepository<UserType, Long> {
    List<UserType> findAllByOrderBySortOrderAsc();

    /** Semua tipe KECUALI {@code code} -- dipakai untuk menyembunyikan ADMIN dari dropdown publik. */
    List<UserType> findAllByCodeNotOrderBySortOrderAsc(String code);
    Optional<UserType> findByCode(String code);
    boolean existsByCode(String code);

    /** Bulk fetch -- 1 query untuk banyak code sekaligus (in-memory join). */
    List<UserType> findByCodeIn(Collection<String> codes);
}

// filepath: /backend/src/main/java/com/example/app/modules/admin/service/UserSpecifications.java
package com.example.app.modules.admin.service;

import com.example.app.modules.auth.entity.User;
import com.example.app.modules.usertype.UserTypeCode;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

/**
 * Syarat-syarat filter daftar user (menu User admin). Specification dipakai (bukan JPQL dengan
 * parameter opsional) karena parameter null di PostgreSQL + Hibernate sering gagal dikenali tipenya:
 * di sini sebuah syarat hanya ditambahkan bila filternya benar-benar terisi.
 */
public final class UserSpecifications {

    /** Karakter escape untuk LIKE. */
    static final char LIKE_ESCAPE = '\\';

    private UserSpecifications() {
    }

    /** Akun ADMIN tidak pernah ikut di daftar user. */
    public static Specification<User> notAdmin() {
        return (root, query, cb) -> cb.notEqual(root.get("userType"), UserTypeCode.ADMIN);
    }

    public static Specification<User> tierIs(String tier) {
        return (root, query, cb) -> cb.equal(root.get("userType"), tier);
    }

    /** Email ATAU nama mengandung kata kunci (tanpa peduli huruf besar/kecil). Nama null aman. */
    public static Specification<User> matchesQuery(String rawQuery) {
        String pattern = likePattern(rawQuery);
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("email")), pattern, LIKE_ESCAPE),
                cb.like(cb.lower(cb.coalesce(root.<String>get("name"), "")), pattern, LIKE_ESCAPE)
        );
    }

    /** "%kata%" huruf kecil, dengan \ % _ pada kata kunci di-escape agar diperlakukan sebagai huruf biasa. */
    static String likePattern(String rawQuery) {
        return "%" + escapeLike(rawQuery.toLowerCase(Locale.ROOT)) + "%";
    }

    static String escapeLike(String value) {
        StringBuilder out = new StringBuilder(value.length() + 4);
        for (char c : value.toCharArray()) {
            if (c == LIKE_ESCAPE || c == '%' || c == '_') {
                out.append(LIKE_ESCAPE);
            }
            out.append(c);
        }
        return out.toString();
    }
}

// filepath: /backend/src/main/java/com/example/app/shared/security/Roles.java
package com.example.app.shared.security;

import com.example.app.modules.usertype.UserTypeCode;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/**
 * Peran aplikasi. Sumber kebenaran adalah kolom users.user_type di DB (dibaca
 * JwtAuthenticationFilter di setiap request), BUKAN claim di token -- claim
 * "role" di JWT hanya untuk UX frontend (memilih dashboard).
 */
public final class Roles {

    public static final String USER = "USER";
    public static final String ADMIN = "ADMIN";

    private Roles() {
    }

    /** ADMIN untuk tipe ADMIN; semua tipe lain (FREE, VIP_*) adalah USER. */
    public static String fromUserType(String userType) {
        return UserTypeCode.ADMIN.equals(userType) ? ADMIN : USER;
    }

    /** Authority Spring Security ({@code ROLE_ADMIN} / {@code ROLE_USER}) untuk hasRole(...). */
    public static List<GrantedAuthority> authoritiesFor(String userType) {
        return List.of(new SimpleGrantedAuthority("ROLE_" + fromUserType(userType)));
    }
}

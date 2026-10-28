// filepath: /backend/src/test/java/com/example/app/shared/security/RolesAndJwtRoleTest.java
package com.example.app.shared.security;

import com.example.app.modules.admin.AdminTestSupport;
import com.example.app.modules.usertype.UserTypeCode;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RolesAndJwtRoleTest {

    @Test
    void onlyAdminTypeMapsToAdminRole() {
        assertEquals("ADMIN", Roles.fromUserType(UserTypeCode.ADMIN));
        for (String type : new String[]{UserTypeCode.FREE, UserTypeCode.VIP_MONTHLY, UserTypeCode.VIP_YEARLY, "TIDAK_DIKENAL", null}) {
            assertEquals("USER", Roles.fromUserType(type), "type=" + type);
        }
    }

    @Test
    void authoritiesUseRolePrefixForHasRole() {
        List<GrantedAuthority> admin = Roles.authoritiesFor(UserTypeCode.ADMIN);
        List<GrantedAuthority> user = Roles.authoritiesFor(UserTypeCode.VIP_YEARLY);

        assertEquals(List.of("ROLE_ADMIN"), admin.stream().map(GrantedAuthority::getAuthority).toList());
        assertEquals(List.of("ROLE_USER"), user.stream().map(GrantedAuthority::getAuthority).toList());
    }

    @Test
    void tokenCarriesRoleAndTwoArgumentOverloadDefaultsToUser() {
        JwtService jwt = AdminTestSupport.jwtService();

        assertEquals("ADMIN", jwt.parse(jwt.generateToken("a@b.com", "$2a$hash", Roles.ADMIN)).orElseThrow().role());
        assertEquals("USER", jwt.parse(jwt.generateToken("a@b.com", "$2a$hash")).orElseThrow().role());
    }
}

// filepath: /backend/src/test/java/com/example/app/modules/admin/service/UserSpecificationsTest.java
package com.example.app.modules.admin.service;

import com.example.app.modules.auth.entity.User;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.jpa.domain.Specification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Pola LIKE (escape \ % _) dan syarat-syarat yang dirangkai UserSpecifications. */
class UserSpecificationsTest {

    // ---------------------------------------------------------------- pola LIKE

    @Test
    void patternIsLowercasedAndWrappedInPercent() {
        assertEquals("%budi%", UserSpecifications.likePattern("BuDi"));
        assertEquals("%budi santoso%", UserSpecifications.likePattern("Budi Santoso"));
    }

    @Test
    void wildcardCharactersInTheKeywordAreEscapedSoTheyMatchLiterally() {
        assertEquals("%\\%%", UserSpecifications.likePattern("%"));
        assertEquals("%\\_%", UserSpecifications.likePattern("_"));
        assertEquals("%\\\\%", UserSpecifications.likePattern("\\"));
        assertEquals("%100\\% a\\_b\\\\c%", UserSpecifications.likePattern("100% a_b\\c"));
    }

    @Test
    void escapeLeavesOrdinaryCharactersAlone() {
        assertEquals("budi@contoh.com", UserSpecifications.escapeLike("budi@contoh.com"));
        assertEquals("", UserSpecifications.escapeLike(""));
    }

    // ---------------------------------------------------------------- syarat yang dirangkai

    @SuppressWarnings("unchecked")
    private CriteriaBuilder evaluate(Specification<User> spec) {
        CriteriaBuilder cb = Mockito.mock(CriteriaBuilder.class, Mockito.RETURNS_MOCKS);
        Root<User> root = Mockito.mock(Root.class, Mockito.RETURNS_MOCKS);
        CriteriaQuery<?> query = Mockito.mock(CriteriaQuery.class, Mockito.RETURNS_MOCKS);
        spec.toPredicate(root, query, cb);
        return cb;
    }

    @Test
    void notAdminExcludesTheAdminType() {
        CriteriaBuilder cb = evaluate(UserSpecifications.notAdmin());
        verify(cb).notEqual(any(Expression.class), eq("ADMIN"));
    }

    @Test
    void tierIsMatchesExactlyThatTier() {
        CriteriaBuilder cb = evaluate(UserSpecifications.tierIs("VIP_YEARLY"));
        verify(cb).equal(any(Expression.class), eq("VIP_YEARLY"));
        verify(cb, never()).notEqual(any(Expression.class), eq("ADMIN"));
    }

    @Test
    void queryMatchesEmailOrNameWithTheEscapeCharacter() {
        CriteriaBuilder cb = evaluate(UserSpecifications.matchesQuery("Budi_"));
        // dua kali: satu untuk email, satu untuk nama; keduanya memakai pola yang sama + karakter escape
        verify(cb, Mockito.times(2)).like(any(Expression.class), eq("%budi\\_%"), eq('\\'));
        verify(cb).or(any(jakarta.persistence.criteria.Predicate.class), any(jakarta.persistence.criteria.Predicate.class));
    }

    @Test
    void nameIsCoalescedSoUsersWithoutANameDoNotBreakTheSearch() {
        CriteriaBuilder cb = evaluate(UserSpecifications.matchesQuery("x"));
        verify(cb).coalesce(any(Expression.class), eq(""));
    }
}

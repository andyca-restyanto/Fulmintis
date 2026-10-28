// filepath: /backend/src/main/java/com/example/app/modules/emailallowlist/repository/AllowedEmailRuleRepository.java
package com.example.app.modules.emailallowlist.repository;

import com.example.app.modules.emailallowlist.entity.AllowedEmailRule;
import com.example.app.modules.emailallowlist.entity.AllowedEmailRuleType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AllowedEmailRuleRepository extends JpaRepository<AllowedEmailRule, Long> {

    List<AllowedEmailRule> findAllByOrderByIdAsc();

    boolean existsByRuleTypeAndValue(AllowedEmailRuleType ruleType, String value);
}

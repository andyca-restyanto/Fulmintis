// filepath: /backend/src/main/java/com/example/app/modules/automation/repository/AutomationSetupRepository.java
package com.example.app.modules.automation.repository;

import com.example.app.modules.automation.entity.AutomationSetup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AutomationSetupRepository extends JpaRepository<AutomationSetup, UUID> {

    Optional<AutomationSetup> findByProjectId(UUID projectId);
}

// backend/src/main/java/com/example/app/modules/projectteam/repository/ProjectTeamRepository.java
package com.example.app.modules.projectteam.repository;

import com.example.app.modules.projectteam.entity.ProjectTeam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectTeamRepository extends JpaRepository<ProjectTeam, Long> {
    Optional<ProjectTeam> findByDescription(String description);
    boolean existsByDescription(String description);
}

// backend/src/main/java/com/example/app/modules/project/repository/ProjectRepository.java
package com.example.app.modules.project.repository;

import com.example.app.modules.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
}

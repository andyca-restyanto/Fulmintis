// backend/src/main/java/com/example/app/modules/projectteam/seed/ProjectTeamSeeder.java
package com.example.app.modules.projectteam.seed;

import com.example.app.modules.projectteam.ProjectTeamCode;
import com.example.app.modules.projectteam.entity.ProjectTeam;
import com.example.app.modules.projectteam.repository.ProjectTeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** Isi data awal tabel project_team saat aplikasi start. Idempotent. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectTeamSeeder implements CommandLineRunner {

    private final ProjectTeamRepository projectTeamRepository;

    @Override
    public void run(String... args) {
        seed(ProjectTeamCode.OWNER);
        seed(ProjectTeamCode.COLLABORATOR);
    }

    private void seed(String description) {
        if (projectTeamRepository.existsByDescription(description)) {
            return;
        }

        projectTeamRepository.save(ProjectTeam.builder().description(description).build());
        log.info("Seed project_team: description={}", description);
    }
}

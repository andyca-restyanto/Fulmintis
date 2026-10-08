// backend/src/main/java/com/example/app/modules/projectteam/entity/ProjectTeam.java
package com.example.app.modules.projectteam.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Tabel dedicated untuk role kolaborasi project (OWNER, COLLABORATOR), ada
 * di database TERPISAH "master_data" (bukan "frontline" tempat tabel
 * project/project_collaboration berada) -- lihat MasterDataSourceConfig.java.
 * Sengaja cuma 2 kolom (id, description) sesuai requirement -- beda dari
 * user_type yang punya code+label terpisah.
 */
@Entity
@Table(name = "project_team")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String description;
}

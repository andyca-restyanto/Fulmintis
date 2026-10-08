// filepath: /backend/src/main/java/com/example/app/modules/automation/dto/AutomationFileDTO.java
package com.example.app.modules.automation.dto;

/** Satu berkas hasil generate. Match dgn AutomationFile (FE). path selalu relatif, pemisah "/". */
public record AutomationFileDTO(String path, String content) {
}

// filepath: /backend/src/main/java/com/example/app/modules/automation/ai/ParsedOutput.java
package com.example.app.modules.automation.ai;

import com.example.app.modules.automation.dto.AutomationFileDTO;

import java.util.List;

/**
 * @param files        berkas yang dipakai
 * @param notes        catatan dari AI (sudah ditambah keterangan berkas yang dilewati, kalau ada)
 * @param skippedPaths path yang dilewati (jenis tidak didukung / ganda); hanya utk log internal
 */
public record ParsedOutput(List<AutomationFileDTO> files, String notes, List<String> skippedPaths) {

    public ParsedOutput(List<AutomationFileDTO> files, String notes) {
        this(files, notes, List.of());
    }
}

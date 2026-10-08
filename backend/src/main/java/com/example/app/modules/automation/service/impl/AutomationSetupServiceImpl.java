// filepath: /backend/src/main/java/com/example/app/modules/automation/service/impl/AutomationSetupServiceImpl.java
package com.example.app.modules.automation.service.impl;

import com.example.app.modules.automation.AutomationCompatibility;
import com.example.app.modules.automation.AutomationFramework;
import com.example.app.modules.automation.AutomationLanguage;
import com.example.app.modules.automation.AutomationPattern;
import com.example.app.modules.automation.dto.AutomationOptionsResponseDTO;
import com.example.app.modules.automation.dto.AutomationSetupRequestDTO;
import com.example.app.modules.automation.dto.AutomationSetupResponseDTO;
import com.example.app.modules.automation.entity.AutomationSetup;
import com.example.app.modules.automation.exception.AutomationApiException;
import com.example.app.modules.automation.repository.AutomationSetupRepository;
import com.example.app.modules.automation.service.AutomationSetupService;
import com.example.app.modules.project.entity.ProjectCollaboration;
import com.example.app.modules.project.service.ProjectAccessService;
import com.example.app.shared.activitylog.ActivityLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AutomationSetupServiceImpl implements AutomationSetupService {

    private static final String OWNER_ONLY_MESSAGE = "Hanya OWNER project yang bisa mengatur struktur automation.";

    private final ProjectAccessService projectAccessService;
    private final AutomationSetupRepository setupRepository;
    private final ActivityLogService activityLogService;

    @Override
    @Transactional(readOnly = true)
    public AutomationOptionsResponseDTO getOptions(String userEmail, UUID projectId) {
        projectAccessService.requireMember(userEmail, projectId);

        return AutomationOptionsResponseDTO.builder()
                .frameworks(Arrays.asList(AutomationFramework.values()))
                .languages(Arrays.asList(AutomationLanguage.values()))
                .compatibility(AutomationCompatibility.matrix())
                .patterns(Arrays.asList(AutomationPattern.values()))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AutomationSetupResponseDTO getSetup(String userEmail, UUID projectId) {
        projectAccessService.requireMember(userEmail, projectId);

        return setupRepository.findByProjectId(projectId)
                .map(this::toResponse)
                .orElseGet(() -> AutomationSetupResponseDTO.builder().configured(false).build());
    }

    @Override
    @Transactional
    public AutomationSetupResponseDTO saveSetup(String userEmail, UUID projectId, AutomationSetupRequestDTO request) {
        ProjectCollaboration myCollaboration = projectAccessService.requireOwner(userEmail, projectId, OWNER_ONLY_MESSAGE);

        // Backend adalah penentu: kombinasi tidak valid ditolak walau FE sudah menonaktifkannya.
        if (!AutomationCompatibility.isSupported(request.getFramework(), request.getLanguage())) {
            throw AutomationApiException.unsupportedCombination(
                    AutomationCompatibility.unsupportedMessage(request.getFramework(), request.getLanguage()));
        }

        AutomationSetup setup = setupRepository.findByProjectId(projectId)
                .orElseGet(() -> AutomationSetup.builder()
                        .project(myCollaboration.getProject())
                        .createdBy(userEmail)
                        .build());

        setup.setFramework(request.getFramework());
        setup.setLanguage(request.getLanguage());
        setup.setPattern(request.getPattern() == null ? AutomationPattern.PAGE_OBJECT_MODEL : request.getPattern());
        setup.setStructureNotes(blankToNull(request.getStructureNotes()));
        setup.setUpdatedBy(userEmail);

        AutomationSetup saved;
        try {
            // flush sekarang: pelanggaran unique (dua OWNER menyimpan pertama kali bersamaan) harus
            // terdeteksi di sini dan dijawab jelas, bukan meledak saat commit.
            saved = setupRepository.saveAndFlush(setup);
        } catch (DataIntegrityViolationException e) {
            throw new AutomationApiException(HttpStatus.CONFLICT, "SETUP_CONFLICT",
                    "Setup sedang disimpan oleh pengguna lain. Muat ulang lalu coba lagi.");
        }

        activityLogService.log(userEmail, "SAVE_AUTOMATION_SETUP");
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteSetup(String userEmail, UUID projectId) {
        projectAccessService.requireOwner(userEmail, projectId, OWNER_ONLY_MESSAGE);

        setupRepository.findByProjectId(projectId).ifPresent(existing -> {
            setupRepository.delete(existing);
            activityLogService.log(userEmail, "DELETE_AUTOMATION_SETUP");
        });
    }

    private AutomationSetupResponseDTO toResponse(AutomationSetup setup) {
        return AutomationSetupResponseDTO.builder()
                .configured(true)
                .framework(setup.getFramework())
                .language(setup.getLanguage())
                .pattern(setup.getPattern())
                .structureNotes(setup.getStructureNotes())
                .updatedAt(setup.getUpdatedAt())
                .updatedBy(setup.getUpdatedBy())
                .build();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}

// backend/src/main/java/com/example/app/shared/exception/GlobalExceptionHandler.java
package com.example.app.shared.exception;

import com.example.app.modules.project.exception.CollaborationNotFoundException;
import com.example.app.modules.project.exception.LastOwnerException;
import com.example.app.modules.testrun.exception.TestResultConflictException;
import com.example.app.modules.admin.exception.AdminRegistrationClosedException;
import com.example.app.modules.admin.exception.InvalidAdminBootstrapCodeException;
import com.example.app.modules.admin.exception.InvalidAdminInvitationException;
import com.example.app.modules.auth.exception.AccountNotVerifiedException;
import com.example.app.modules.auth.exception.EmailAlreadyExistsException;
import com.example.app.modules.auth.exception.IncorrectCurrentPasswordException;
import com.example.app.modules.auth.exception.InvalidCredentialsException;
import com.example.app.modules.auth.exception.InvalidOrExpiredResetTokenException;
import com.example.app.modules.project.exception.CollaboratorAlreadyExistsException;
import com.example.app.modules.project.exception.CollaboratorNotFoundException;
import com.example.app.modules.project.exception.InvalidProjectTeamException;
import com.example.app.modules.project.exception.ProjectAccessForbiddenException;
import com.example.app.modules.project.exception.ProjectNotFoundException;
import com.example.app.modules.testrepository.exception.TestFolderNotFoundException;
import com.example.app.modules.testcase.exception.TestCaseAlreadyArchivedException;
import com.example.app.modules.testcase.exception.TestCaseArchivedException;
import com.example.app.modules.automation.exception.AutomationApiException;
import com.example.app.shared.ai.AiApiException;
import com.example.app.modules.testcase.dto.TestCaseImportResultDTO;
import com.example.app.modules.testcase.exception.InvalidImportFileException;
import com.example.app.modules.testcase.exception.TestCaseImportValidationException;
import com.example.app.modules.testcase.exception.TestCaseNotFoundException;
import com.example.app.modules.testrun.exception.EvidenceUploadException;
import com.example.app.modules.testrun.exception.TestResultNotFoundException;
import com.example.app.modules.testrun.exception.TestRunNotFoundException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("message", "Validasi gagal");
        body.put("errors", errors);

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(AdminRegistrationClosedException.class)
    public ResponseEntity<Map<String, Object>> handleAdminRegistrationClosed(AdminRegistrationClosedException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(InvalidAdminBootstrapCodeException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidAdminBootstrapCode(InvalidAdminBootstrapCodeException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(InvalidAdminInvitationException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidAdminInvitation(InvalidAdminInvitationException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleEmailExists(EmailAlreadyExistsException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidCredentials(InvalidCredentialsException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(AccountNotVerifiedException.class)
    public ResponseEntity<Map<String, Object>> handleAccountNotVerified(AccountNotVerifiedException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(InvalidOrExpiredResetTokenException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidResetToken(InvalidOrExpiredResetTokenException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(IncorrectCurrentPasswordException.class)
    public ResponseEntity<Map<String, Object>> handleIncorrectCurrentPassword(IncorrectCurrentPasswordException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(CollaboratorAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleCollaboratorAlreadyExists(CollaboratorAlreadyExistsException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(CollaboratorNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleCollaboratorNotFound(CollaboratorNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(InvalidProjectTeamException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidProjectTeam(InvalidProjectTeamException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ProjectNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleProjectNotFound(ProjectNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ProjectAccessForbiddenException.class)
    public ResponseEntity<Map<String, Object>> handleProjectAccessForbidden(ProjectAccessForbiddenException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(TestFolderNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleTestFolderNotFound(TestFolderNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // ---- Modul testcase ----

    @ExceptionHandler(TestCaseNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleTestCaseNotFound(TestCaseNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(TestCaseAlreadyArchivedException.class)
    public ResponseEntity<Map<String, Object>> handleTestCaseAlreadyArchived(TestCaseAlreadyArchivedException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(TestCaseArchivedException.class)
    public ResponseEntity<Map<String, Object>> handleTestCaseArchived(TestCaseArchivedException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    // ---- Modul project (manajemen member) ----

    @ExceptionHandler(CollaborationNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleCollaborationNotFound(CollaborationNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(LastOwnerException.class)
    public ResponseEntity<Map<String, Object>> handleLastOwner(LastOwnerException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    // ---- Modul testrun ----

    @ExceptionHandler(TestRunNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleTestRunNotFound(TestRunNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(TestResultNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleTestResultNotFound(TestResultNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(TestResultConflictException.class)
    public ResponseEntity<Map<String, Object>> handleTestResultConflict(TestResultConflictException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    // Dua request menyimpan test result yang sama nyaris bersamaan (@Version).
    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(
            org.springframework.orm.ObjectOptimisticLockingFailureException ex
    ) {
        return buildResponse(HttpStatus.CONFLICT, "Data ini baru saja diubah pengguna lain. Muat ulang lalu coba lagi.");
    }

    // ---- Fitur AI umum (mis. generate test case): errorCode mesin + (opsional) daftar kesalahan per item ----
    @ExceptionHandler(AiApiException.class)
    public ResponseEntity<Map<String, Object>> handleAiApi(AiApiException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", ex.getStatus().value());
        body.put("message", ex.getMessage());
        body.put("errorCode", ex.getErrorCode());
        if (ex.getErrors() != null) {
            body.put("errors", ex.getErrors());
        }
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    // ---- Menu Automation (generate kode automation dgn AI) ----

    // Membawa errorCode mesin supaya FE bisa membedakan kasus (mis. batas harian vs AI tidak tersedia).
    @ExceptionHandler(AutomationApiException.class)
    public ResponseEntity<Map<String, Object>> handleAutomationApi(AutomationApiException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", ex.getStatus().value());
        body.put("message", ex.getMessage());
        body.put("errorCode", ex.getErrorCode());
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    // ---- Import test case dari Excel ----

    // File tidak bisa diproses sama sekali (bukan .xlsx, rusak, kolom wajib tidak ada, dst).
    @ExceptionHandler(InvalidImportFileException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidImportFile(InvalidImportFileException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // Simpan ditolak karena ada baris bermasalah: body = hasil lengkap (daftar error per baris).
    @ExceptionHandler(TestCaseImportValidationException.class)
    public ResponseEntity<TestCaseImportResultDTO> handleImportValidation(TestCaseImportValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getResult());
    }

    @ExceptionHandler(EvidenceUploadException.class)
    public ResponseEntity<Map<String, Object>> handleEvidenceUpload(EvidenceUploadException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // Dilempar Spring SEBELUM masuk controller kalau file melebihi
    // spring.servlet.multipart.max-file-size / max-request-size, jadi
    // validasi 50MB di TestRunServiceImpl tidak pernah sempat jalan utk
    // kasus ini. Tanpa handler ini FE dapat 500 generik.
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Ukuran file melebihi batas upload (50MB).");
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("message", message);

        return ResponseEntity.status(status).body(body);
    }
}

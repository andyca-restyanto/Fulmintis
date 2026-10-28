// backend/src/main/java/com/example/app/modules/testrun/exception/EvidenceUploadException.java
package com.example.app.modules.testrun.exception;

/**
 * Dilempar (400) kalau file evidence yang di-upload TIDAK VALID -- kosong,
 * bukan image/video, atau melebihi batas ukuran maksimum (requirement #5:
 * "evidence (can upload image/video)").
 */
public class EvidenceUploadException extends RuntimeException {
    public EvidenceUploadException(String message) {
        super(message);
    }
}

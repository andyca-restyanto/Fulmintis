// filepath: /backend/src/main/java/com/example/app/modules/testcase/service/importer/ImportFileGuard.java
package com.example.app.modules.testcase.service.importer;

import com.example.app.modules.testcase.exception.InvalidImportFileException;

import java.util.Locale;

/**
 * Pemeriksaan file SEBELUM dibuka dengan POI: ekstensi, ukuran, dan isi (byte
 * awal) -- bukan hanya nama/ekstensi yang dikirim klien.
 */
public final class ImportFileGuard {

    public static final long MAX_FILE_BYTES = 5L * 1024 * 1024; // 5 MB

    private ImportFileGuard() {
    }

    /**
     * @param header byte awal file (minimal 4 byte kalau ada)
     * @throws InvalidImportFileException kalau file ditolak
     */
    public static void check(String originalFilename, long sizeBytes, byte[] header) {
        if (sizeBytes <= 0) {
            throw new InvalidImportFileException("File Excel tidak boleh kosong.");
        }
        if (sizeBytes > MAX_FILE_BYTES) {
            throw new InvalidImportFileException("Ukuran file maksimal 5 MB.");
        }
        if (originalFilename == null || !originalFilename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new InvalidImportFileException("Format file harus .xlsx (Excel). File .xls atau .csv tidak didukung.");
        }
        // .xlsx adalah arsip ZIP: diawali "PK\x03\x04". File .xls lama (OLE2), .csv, atau
        // file apa pun yang cuma di-rename jadi .xlsx tidak lolos di sini.
        boolean looksLikeZip = header != null && header.length >= 4
                && header[0] == 'P' && header[1] == 'K' && header[2] == 0x03 && header[3] == 0x04;
        if (!looksLikeZip) {
            throw new InvalidImportFileException("Isi file bukan Excel .xlsx yang valid.");
        }
    }
}

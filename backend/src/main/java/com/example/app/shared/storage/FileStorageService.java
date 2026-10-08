// filepath: /backend/src/main/java/com/example/app/shared/storage/FileStorageService.java
package com.example.app.shared.storage;

import org.springframework.core.io.Resource;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * Abstraksi penyimpanan file. Implementasi sekarang: {@link LocalFileStorageService}
 * (disk lokal). Ganti implementasinya kalau nanti pindah ke S3/CDN -- caller
 * tidak perlu berubah.
 */
public interface FileStorageService {

    /**
     * Simpan file ke {@code subDirectory} dan balas NAMA FILE yang tersimpan
     * (UUID + ekstensi). Ekstensi ditentukan CALLER (hasil deteksi isi file),
     * BUKAN diambil dari nama file kiriman klien.
     *
     * @param fileExtension ekstensi tanpa titik, hanya [a-z0-9] (mis. "png")
     */
    String store(MultipartFile file, String subDirectory, String fileExtension);

    /** @throws IllegalStateException kalau file tidak ada / tidak terbaca di storage. */
    Resource loadAsResource(String subDirectory, String storedFileName);

    /** Best-effort: tidak melempar exception kalau file sudah tidak ada / gagal dihapus. */
    void delete(String subDirectory, String storedFileName);

    /**
     * Hapus file SETELAH transaksi aktif berhasil commit. Kalau transaksi
     * rollback, file TIDAK dihapus (baris DB kembali menunjuk ke file itu,
     * jadi file harus tetap ada). Tanpa transaksi aktif: hapus langsung.
     */
    default void deleteAfterCommit(String subDirectory, String storedFileName) {
        if (storedFileName == null || storedFileName.isBlank()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delete(subDirectory, storedFileName);
                }
            });
        } else {
            delete(subDirectory, storedFileName);
        }
    }

    /**
     * Hapus file HANYA kalau transaksi aktif berakhir rollback -- dipakai
     * untuk file BARU yang sudah ditulis ke disk sebelum commit, supaya tidak
     * jadi file yatim kalau transaksinya gagal. Tanpa transaksi aktif: no-op.
     */
    default void deleteIfRolledBack(String subDirectory, String storedFileName) {
        if (storedFileName == null || storedFileName.isBlank()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    delete(subDirectory, storedFileName);
                }
            }
        });
    }
}

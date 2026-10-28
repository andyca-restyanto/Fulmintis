// backend/src/main/java/com/example/app/shared/storage/LocalFileStorageService.java
package com.example.app.shared.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Implementasi {@link FileStorageService} yang nyimpen file ke DISK LOKAL
 * server (BUKAN cloud storage/S3) -- paling simpel utk kebutuhan sekarang,
 * cukup buat local dev & single-server deployment. Kalau nanti deploy multi-
 * instance/butuh CDN, tinggal ganti implementasi ini (interface-nya sudah
 * generic, caller/TestRunServiceImpl tidak perlu berubah).
 * <p>
 * Lokasi folder root bisa di-override lewat property
 * {@code app.storage.base-dir} (default: "uploads", relatif ke working
 * directory app di-start) -- lihat application.properties.
 */
@Service
public class LocalFileStorageService implements FileStorageService {

    private final Path baseDir;

    public LocalFileStorageService(@Value("${app.storage.base-dir:uploads}") String baseDirProperty) {
        this.baseDir = Path.of(baseDirProperty).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.baseDir);
        } catch (IOException e) {
            throw new IllegalStateException("Tidak bisa membuat direktori upload: " + this.baseDir, e);
        }
    }

    @Override
    public String store(MultipartFile file, String subDirectory, String fileExtension) {
        // Ekstensi datang dari caller (hasil deteksi isi file), tapi tetap
        // divalidasi ketat di sini supaya storage tidak pernah menulis nama
        // file dengan karakter path/aneh apa pun sumbernya.
        if (fileExtension == null || !fileExtension.matches("[a-z0-9]{1,10}")) {
            throw new IllegalArgumentException("Ekstensi file tidak valid.");
        }
        try {
            Path targetDir = baseDir.resolve(subDirectory).normalize();
            Files.createDirectories(targetDir);

            String storedFileName = UUID.randomUUID() + "." + fileExtension;
            Path targetPath = targetDir.resolve(storedFileName).normalize();

            if (!targetPath.startsWith(targetDir)) {
                throw new IllegalStateException("Path file upload tidak valid.");
            }

            file.transferTo(targetPath);
            return storedFileName;
        } catch (IOException e) {
            throw new IllegalStateException("Gagal menyimpan file upload.", e);
        }
    }

    @Override
    public Resource loadAsResource(String subDirectory, String storedFileName) {
        Path filePath = baseDir.resolve(subDirectory).resolve(storedFileName).normalize();
        Resource resource = new FileSystemResource(filePath);

        if (!resource.exists() || !resource.isReadable()) {
            throw new IllegalStateException("File tidak ditemukan di storage: " + storedFileName);
        }

        return resource;
    }

    @Override
    public void delete(String subDirectory, String storedFileName) {
        if (storedFileName == null || storedFileName.isBlank()) {
            return;
        }

        Path filePath = baseDir.resolve(subDirectory).resolve(storedFileName).normalize();
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException ignored) {
            // Best-effort -- lihat javadoc FileStorageService.delete().
        }
    }
}

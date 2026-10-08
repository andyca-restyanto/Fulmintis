// filepath: /backend/src/main/java/com/example/app/modules/automation/service/AutomationZipBuilder.java
package com.example.app.modules.automation.service;

import com.example.app.modules.automation.ai.GeneratedPaths;
import com.example.app.modules.automation.dto.AutomationFileDTO;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Membuat zip hasil generate di server. Nama entri divalidasi ULANG (pertahanan berlapis dgn parser). */
public final class AutomationZipBuilder {

    public static final String README_NAME = "README-AI-GENERATED.txt";

    private AutomationZipBuilder() {
    }

    public static byte[] build(List<AutomationFileDTO> files, String frameworkName, String languageName) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {

            addEntry(zip, README_NAME, readme(frameworkName, languageName));
            for (AutomationFileDTO file : files) {
                String problem = GeneratedPaths.problemWith(file.path());
                if (problem != null || README_NAME.equalsIgnoreCase(file.path())) {
                    throw new IllegalStateException("Path berkas tidak aman untuk zip: " + problem);
                }
                addEntry(zip, file.path(), file.content());
            }
            zip.finish();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Gagal membuat zip hasil generate.", e);
        }
    }

    private static void addEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String readme(String frameworkName, String languageName) {
        return "KODE HASIL AI -- TINJAU SEBELUM DIJALANKAN\n"
                + "=========================================\n\n"
                + "Berkas dalam arsip ini dibuat oleh AI (" + frameworkName + " / " + languageName + ") dari test case "
                + "di TCMS. Hasil bersifat DRAFT:\n"
                + " - tidak dijamin langsung bisa dikompilasi atau dijalankan;\n"
                + " - locator dan URL hanyalah dugaan (lihat komentar TODO) dan perlu disesuaikan dgn aplikasi Anda;\n"
                + " - tinjau isinya sebelum menjalankan, terutama kalau test case berisi teks dari pihak lain.\n"
                + "Jangan menaruh kredensial asli di kode; gunakan environment variable.\n\n"
                + "AI-GENERATED CODE -- REVIEW BEFORE RUNNING. This is a draft and is not guaranteed to compile or run.\n";
    }
}

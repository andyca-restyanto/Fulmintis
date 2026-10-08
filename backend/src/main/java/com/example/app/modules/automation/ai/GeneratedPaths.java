// filepath: /backend/src/main/java/com/example/app/modules/automation/ai/GeneratedPaths.java
package com.example.app.modules.automation.ai;

import java.util.Locale;
import java.util.Set;

/**
 * Aturan path berkas hasil generate -- dipakai parser (menolak output berbahaya) DAN pembuat zip
 * (pertahanan berlapis). Path wajib relatif, memakai "/", tanpa "..", dan hanya berekstensi
 * kode/konfigurasi teks; berkas yang bisa dieksekusi langsung (.sh, .bat, .exe, ...) ditolak.
 */
public final class GeneratedPaths {

    public static final int MAX_PATH_LENGTH = 200;
    public static final int MAX_DEPTH = 8;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "java", "js", "ts", "mjs", "cjs", "py",
            "json", "xml", "yml", "yaml", "md", "txt", "properties", "gradle", "toml", "ini", "cfg");

    private GeneratedPaths() {
    }

    /** Hasil pemeriksaan sebuah path dari keluaran AI. */
    public enum Verdict {
        /** Aman dan jenisnya didukung: dipakai. */
        OK,
        /** Aman tetapi jenis berkasnya tidak didukung (dotfile, tanpa ekstensi, ekstensi tak dikenal): dilewati, bukan menggagalkan hasil. */
        SKIPPABLE,
        /** Berbahaya (path traversal/absolut/karakter terlarang) atau berkas yang bisa dieksekusi: seluruh keluaran ditolak. */
        UNSAFE
    }

    /**
     * Berkas yang bisa dieksekusi langsung. Keberadaannya di keluaran AI bisa menandakan prompt injection dari isi test case, jadi ini
     * TIDAK dilewati diam-diam: seluruh keluaran ditolak. Jenis lain yang tidak didukung hanya dilewati.
     */
    private static final Set<String> EXECUTABLE_EXTENSIONS = Set.of(
            "sh", "bash", "zsh", "csh", "ksh", "fish", "bat", "cmd", "ps1", "psm1", "vbs", "vbe", "wsf",
            "exe", "dll", "com", "msi", "scr", "pif", "lnk", "jar", "so", "dylib", "bin", "app", "apk", "class", "pyc");

    private record Inspection(String problem, boolean benign) {
    }

    private static Inspection inspect(String path) {
        if (path == null || path.isBlank()) {
            return new Inspection("path kosong", false);
        }
        if (path.length() > MAX_PATH_LENGTH) {
            return new Inspection("path terlalu panjang", false);
        }
        if (path.indexOf('\\') >= 0 || path.indexOf('\0') >= 0 || path.indexOf(':') >= 0) {
            return new Inspection("path mengandung karakter terlarang (\\, NUL, :)", false);
        }
        if (path.startsWith("/")) {
            return new Inspection("path absolut", false);
        }
        String[] segments = path.split("/", -1);
        if (segments.length > MAX_DEPTH) {
            return new Inspection("path terlalu dalam", false);
        }
        for (String segment : segments) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                return new Inspection("path mengandung segmen kosong, '.' atau '..'", false);
            }
        }
        String fileName = segments[segments.length - 1];
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) {
            // .gitignore, .env, Dockerfile, Makefile, dst: tidak berbahaya, hanya bukan jenis yang kita sertakan.
            return new Inspection("berkas tanpa ekstensi yang dikenal", true);
        }
        String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            return new Inspection("ekstensi tidak diizinkan: ." + extension, !EXECUTABLE_EXTENSIONS.contains(extension));
        }
        return new Inspection(null, false);
    }

    /** @return null kalau path aman, atau alasan penolakan (utk log internal). */
    public static String problemWith(String path) {
        return inspect(path).problem();
    }

    /** Dipakai parser keluaran AI: path yang jenisnya tidak didukung dilewati, yang berbahaya menolak seluruh keluaran. */
    public static Verdict classify(String path) {
        Inspection inspection = inspect(path);
        if (inspection.problem() == null) {
            return Verdict.OK;
        }
        return inspection.benign() ? Verdict.SKIPPABLE : Verdict.UNSAFE;
    }

    public static boolean isSafe(String path) {
        return problemWith(path) == null;
    }
}

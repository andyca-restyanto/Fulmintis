// backend/src/main/java/com/example/app/modules/project/exception/CollaboratorNotFoundException.java
package com.example.app.modules.project.exception;

/**
 * Dilempar saat email yang dikirim di AddProjectCollaboratorRequestDTO
 * BELUM terdaftar sebagai user di aplikasi ini (beda dengan
 * ProjectNotFoundException yang soal project/membership si PEMANGGIL).
 */
public class CollaboratorNotFoundException extends RuntimeException {
    public CollaboratorNotFoundException() {
        super("User dengan email tersebut belum terdaftar atau belum terverifikasi.");
    }
}

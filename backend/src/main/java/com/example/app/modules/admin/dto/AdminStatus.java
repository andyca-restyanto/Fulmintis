// filepath: /backend/src/main/java/com/example/app/modules/admin/dto/AdminStatus.java
package com.example.app.modules.admin.dto;

/**
 * Status admin di menu Admin. Diturunkan dari kolom yang sudah ada (tanpa tabel baru):
 * <ul>
 *   <li>{@code INACTIVE} -- {@code active=false} (dinonaktifkan), apa pun status lainnya;</li>
 *   <li>{@code ACTIVE} -- aktif dan sudah terverifikasi;</li>
 *   <li>{@code PENDING} -- belum terverifikasi: menunggu undangan diterima, atau (tanpa
 *       {@code invitationExpiresAt}) admin pertama yang belum klik link verifikasi email.</li>
 * </ul>
 * Match: AdminStatus (FE, union literal string).
 */
public enum AdminStatus {
    ACTIVE,
    PENDING,
    INACTIVE
}

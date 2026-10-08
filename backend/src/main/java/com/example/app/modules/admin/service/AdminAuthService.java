// filepath: /backend/src/main/java/com/example/app/modules/admin/service/AdminAuthService.java
package com.example.app.modules.admin.service;

import com.example.app.modules.admin.dto.AdminAcceptInvitationRequestDTO;
import com.example.app.modules.admin.dto.AdminInvitationValidationResponseDTO;
import com.example.app.modules.admin.dto.AdminProfileResponseDTO;
import com.example.app.modules.admin.dto.AdminRegisterRequestDTO;
import com.example.app.modules.admin.dto.AdminRegisterResponseDTO;
import com.example.app.modules.admin.dto.AdminRegistrationStatusResponseDTO;
import com.example.app.modules.auth.dto.LoginRequestDTO;
import com.example.app.modules.auth.dto.LoginResponseDTO;

public interface AdminAuthService {

    /** Apakah pendaftaran admin pertama masih terbuka (belum ada admin sama sekali). */
    AdminRegistrationStatusResponseDTO getRegistrationStatus();

    /** Daftar ADMIN PERTAMA. Setelah ada satu admin, selalu ditolak (403). */
    AdminRegisterResponseDTO register(AdminRegisterRequestDTO request);

    /** Login khusus admin. Non-admin / salah password -> 401 generik yang sama. */
    LoginResponseDTO login(LoginRequestDTO request);

    AdminInvitationValidationResponseDTO validateInvitation(String token);

    void acceptInvitation(AdminAcceptInvitationRequestDTO request);

    AdminProfileResponseDTO getProfile(String adminEmail);
}

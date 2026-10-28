// filepath: /backend/src/main/java/com/example/app/modules/emailallowlist/repository/RegistrationAllowlistSettingRepository.java
package com.example.app.modules.emailallowlist.repository;

import com.example.app.modules.emailallowlist.entity.RegistrationAllowlistSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationAllowlistSettingRepository extends JpaRepository<RegistrationAllowlistSetting, Integer> {
}

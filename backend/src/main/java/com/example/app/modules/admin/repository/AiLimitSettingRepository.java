// filepath: /backend/src/main/java/com/example/app/modules/admin/repository/AiLimitSettingRepository.java
package com.example.app.modules.admin.repository;

import com.example.app.modules.admin.entity.AiLimitSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiLimitSettingRepository extends JpaRepository<AiLimitSetting, String> {
}

// backend/src/main/java/com/example/app/shared/activitylog/ActivityLogRepository.java
package com.example.app.shared.activitylog;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {
}

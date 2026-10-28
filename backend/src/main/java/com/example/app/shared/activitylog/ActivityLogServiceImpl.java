// backend/src/main/java/com/example/app/shared/activitylog/ActivityLogServiceImpl.java
package com.example.app.shared.activitylog;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ActivityLogServiceImpl implements ActivityLogService {

    private final ActivityLogRepository activityLogRepository;

    @Override
    @Transactional
    public void log(String userEmail, String activity) {
        ActivityLog entry = ActivityLog.builder()
                .userEmail(userEmail)
                .activity(activity)
                .createdBy(userEmail)
                .updatedBy(userEmail)
                .build();

        activityLogRepository.save(entry);
    }
}

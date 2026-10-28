// backend/src/main/java/com/example/app/shared/activitylog/ActivityLogService.java
package com.example.app.shared.activitylog;

public interface ActivityLogService {
    /**
     * Catat 1 baris activity log. userEmail dipakai juga sebagai createdBy,
     * karena aksi ini dilakukan oleh user tersebut sendiri.
     *
     * @param userEmail email user yang melakukan aksi
     * @param activity  deskripsi aksi, contoh: "REGISTER", "LOGIN", "VIEW_DASHBOARD"
     */
    void log(String userEmail, String activity);
}

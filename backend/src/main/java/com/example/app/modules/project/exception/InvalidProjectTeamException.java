// backend/src/main/java/com/example/app/modules/project/exception/InvalidProjectTeamException.java
package com.example.app.modules.project.exception;

public class InvalidProjectTeamException extends RuntimeException {
    public InvalidProjectTeamException(String projectTeam) {
        super("Role '" + projectTeam + "' tidak valid. Gunakan OWNER atau COLLABORATOR.");
    }
}

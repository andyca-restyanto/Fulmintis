// backend/src/main/java/com/example/app/modules/project/exception/CollaboratorAlreadyExistsException.java
package com.example.app.modules.project.exception;

public class CollaboratorAlreadyExistsException extends RuntimeException {
    public CollaboratorAlreadyExistsException() {
        super("User ini sudah menjadi member project ini.");
    }
}

// backend/src/main/java/com/example/app/modules/auth/service/EmailVerificationResult.java
package com.example.app.modules.auth.service;

public enum EmailVerificationResult {
    SUCCESS,
    ALREADY_VERIFIED,
    EXPIRED,
    INVALID
}

// filepath: /backend/src/main/java/com/example/app/shared/config/ClockConfig.java
package com.example.app.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Satu sumber waktu yang bisa diganti saat test (batas hari kuota, circuit breaker). */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}

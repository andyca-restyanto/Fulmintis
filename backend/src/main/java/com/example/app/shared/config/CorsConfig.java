// backend/src/main/java/com/example/app/shared/config/CorsConfig.java
package com.example.app.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {

    // Daftar origin frontend yang diizinkan, dipisah koma.
    // Diset lewat application.properties / application-local.properties,
    // supaya beda environment (dev/staging/prod) bisa beda origin tanpa ubah kode.
    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Berlaku untuk SEMUA endpoint /api/** -> otomatis meng-cover modul baru
        // (product, order, dll) tanpa perlu config CORS ulang tiap modul.
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}

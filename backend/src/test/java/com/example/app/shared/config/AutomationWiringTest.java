// filepath: /backend/src/test/java/com/example/app/shared/config/AutomationWiringTest.java
package com.example.app.shared.config;

import com.example.app.modules.automation.repository.AutomationGenerationRepository;
import com.example.app.modules.automation.repository.AutomationSetupRepository;
import com.example.app.modules.automation.service.AutomationGenerationService;
import com.example.app.modules.automation.service.AutomationSetupService;
import com.example.app.modules.project.service.ProjectAccessService;
import com.example.app.shared.activitylog.ActivityLogServiceImpl;
import com.example.app.shared.ai.AiGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Merakit konteks Spring yang MENIRU start aplikasi untuk modul automation -- memakai PrimaryDataSourceConfig dan
 * MasterDataSourceConfig yang ASLI, tetapi tanpa database (metadata JDBC dimatikan, koneksi tidak pernah dibuka).
 * <p>
 * Ini yang tidak bisa dilakukan unit test biasa (yang membuat objek dengan {@code new} dan repository palsu): memastikan
 * SETIAP bean modul ini benar-benar bisa dirakit Spring, mis. repository terdaftar di datasource yang tepat. Kesalahan
 * itulah yang membuat aplikasi gagal start dengan "required a bean of type '...AutomationSetupRepository' that could not
 * be found".
 */
class AutomationWiringTest {

    @Configuration
    @EnableConfigurationProperties
    // Kelas test (mis. @Configuration bersarang di test lain) berada di classpath yang sama saat test berjalan, tetapi TIDAK ada
    // di aplikasi sungguhan -- dikecualikan supaya konteks ini hanya berisi kelas utama.
    @ComponentScan(basePackages = {"com.example.app.modules.automation", "com.example.app.shared.ai"},
            excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*\\.[A-Za-z]*Test(Support)?(\\$.*)?"))
    @Import({PrimaryDataSourceConfig.class, MasterDataSourceConfig.class, ClockConfig.class,
            ProjectAccessService.class, ActivityLogServiceImpl.class})
    static class MiniApplication {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        /** Di aplikasi asli disediakan auto-configuration Spring Boot; di sini dirakit manual TANPA koneksi database. */
        @Bean
        EntityManagerFactoryBuilder entityManagerFactoryBuilder() {
            Map<String, Object> hibernate = new HashMap<>();
            hibernate.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
            hibernate.put("hibernate.boot.allow_jdbc_metadata_access", "false");
            hibernate.put("hibernate.hbm2ddl.auto", "none");
            return new EntityManagerFactoryBuilder(new HibernateJpaVendorAdapter(), hibernate, null);
        }

        @Bean
        JpaProperties jpaProperties() {
            return new JpaProperties();
        }
    }

    private static AnnotationConfigApplicationContext startContext() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        Map<String, Object> properties = new HashMap<>();
        properties.put("spring.datasource.url", "jdbc:postgresql://127.0.0.1:1/tidak_dipakai");
        properties.put("spring.datasource.username", "x");
        properties.put("spring.datasource.password", "x");
        properties.put("app.datasource.masterdata.url", "jdbc:postgresql://127.0.0.1:1/tidak_dipakai");
        properties.put("app.datasource.masterdata.username", "x");
        properties.put("app.datasource.masterdata.password", "x");
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
        context.register(MiniApplication.class);
        context.refresh();
        return context;
    }

    @Test
    void theAutomationModuleWiresUpTheWayItDoesAtApplicationStart() {
        try (AnnotationConfigApplicationContext context = startContext()) {
            // Repository terdaftar (inilah yang hilang pada error "required a bean of type ... AutomationSetupRepository")
            assertNotNull(context.getBean(AutomationSetupRepository.class));
            assertNotNull(context.getBean(AutomationGenerationRepository.class));
            // Service yang menerima repository itu lewat constructor
            assertNotNull(context.getBean(AutomationSetupService.class));
            assertNotNull(context.getBean(AutomationGenerationService.class));
            // Lapisan AI aktif sebagai "tidak dikonfigurasi" (provider kosong) -- aplikasi tetap start
            assertTrue(!context.getBean(AiGateway.class).isEnabled());
        }
    }
}

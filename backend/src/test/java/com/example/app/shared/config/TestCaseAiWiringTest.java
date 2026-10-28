// filepath: /backend/src/test/java/com/example/app/shared/config/TestCaseAiWiringTest.java
package com.example.app.shared.config;

import com.example.app.modules.testcase.ai.TestCaseFakeResponder;
import com.example.app.modules.testcase.controller.TestCaseAiController;
import com.example.app.modules.testcase.repository.TestCaseAiGenerationRepository;
import com.example.app.modules.testcase.service.TestCaseAiJobDispatcher;
import com.example.app.modules.testcase.service.TestCaseAiService;
import com.example.app.modules.testcase.service.TestCaseAiStartupRecovery;
import com.example.app.modules.project.service.ProjectAccessService;
import com.example.app.shared.activitylog.ActivityLogServiceImpl;
import com.example.app.shared.ai.AiGateway;
import com.example.app.shared.ai.AiUsageCounter;
import com.example.app.shared.ai.FakeAiResponder;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Merakit konteks Spring yang meniru start aplikasi untuk fitur generate test case: konfigurasi datasource ASLI (tanpa database),
 * modul testcase + automation + lapisan AI. Memastikan repository dan entity baru terdaftar di datasource yang tepat dan SEMUA
 * bean (service, worker, dispatcher, controller, responder palsu, penghitung anggaran) bisa dirakit -- hal yang tidak bisa dibuktikan
 * unit test yang memakai {@code new} dan repository palsu.
 */
class TestCaseAiWiringTest {

    @Configuration
    @EnableConfigurationProperties
    @ComponentScan(basePackages = {"com.example.app.modules.testcase", "com.example.app.modules.automation", "com.example.app.shared.ai"},
            excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*\\.[A-Za-z]*Test(Support)?(\\$.*)?"))
    @Import({PrimaryDataSourceConfig.class, MasterDataSourceConfig.class, ClockConfig.class,
            ProjectAccessService.class, ActivityLogServiceImpl.class})
    static class MiniApplication {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

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

    private static AnnotationConfigApplicationContext startContext(String provider) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        Map<String, Object> properties = new HashMap<>();
        properties.put("spring.datasource.url", "jdbc:postgresql://127.0.0.1:1/tidak_dipakai");
        properties.put("spring.datasource.username", "x");
        properties.put("spring.datasource.password", "x");
        properties.put("app.datasource.masterdata.url", "jdbc:postgresql://127.0.0.1:1/tidak_dipakai");
        properties.put("app.datasource.masterdata.username", "x");
        properties.put("app.datasource.masterdata.password", "x");
        properties.put("app.ai.provider", provider);
        properties.put("app.ai.fake.delay-millis", "0");
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
        context.register(MiniApplication.class);
        context.refresh();
        return context;
    }

    @Test
    void theTestCaseAiFeatureWiresUpTheWayItDoesAtApplicationStart() {
        try (AnnotationConfigApplicationContext context = startContext("")) {
            // Repository terdaftar di datasource primary (kalau paketnya terlewat: "required a bean of type ... that could not be found")
            assertNotNull(context.getBean(TestCaseAiGenerationRepository.class));
            // Seluruh rantai bean fitur ini
            assertNotNull(context.getBean(TestCaseAiService.class));
            assertNotNull(context.getBean(TestCaseAiJobDispatcher.class));
            assertNotNull(context.getBean(TestCaseAiController.class));
            assertNotNull(context.getBean(TestCaseAiStartupRecovery.class));
            // Provider kosong -> aplikasi tetap start, fitur nonaktif
            assertTrue(!context.getBean(AiGateway.class).isEnabled());
            // Anggaran global menjumlahkan KEDUA fitur (Automation + generate test case)
            assertEquals(2, context.getBeansOfType(AiUsageCounter.class).size());
        }
    }

    @Test
    void withTheFakeProviderTheTestCaseResponderIsRegisteredAndTheGatewayIsEnabled() {
        try (AnnotationConfigApplicationContext context = startContext("fake")) {
            assertTrue(context.getBean(AiGateway.class).isEnabled());
            assertEquals(1, context.getBeansOfType(FakeAiResponder.class).size());
            assertNotNull(context.getBean(TestCaseFakeResponder.class));
        }
    }
}

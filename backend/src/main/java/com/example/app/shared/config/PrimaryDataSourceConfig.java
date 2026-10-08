// backend/src/main/java/com/example/app/shared/config/PrimaryDataSourceConfig.java
package com.example.app.shared.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * Datasource UTAMA -> database "frontline" (tabel users, activity_log, dll).
 * Ini datasource DEFAULT aplikasi (@Primary), dipakai kalau tidak ada
 * qualifier eksplisit.
 *
 * Kalau nanti nambah modul baru yang tabelnya juga di database "frontline"
 * (bukan master_data), tambahkan package repository-nya ke basePackages di
 * bawah.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = {
                "com.example.app.modules.auth.repository",
                "com.example.app.modules.project.repository",
                "com.example.app.modules.testrepository.repository",
                "com.example.app.modules.testcase.repository",
                "com.example.app.modules.testrun.repository",
                "com.example.app.modules.automation.repository",
                "com.example.app.shared.activitylog"
        },
        entityManagerFactoryRef = "primaryEntityManagerFactory",
        transactionManagerRef = "primaryTransactionManager"
)
public class PrimaryDataSourceConfig {

    /**
     * Paket entity (@Entity) yang dikelola datasource ini. Dipisah jadi konstanta supaya
     * JpaRegistrationTest bisa memastikan SETIAP entity di aplikasi terdaftar di tepat satu datasource.
     * Menambah modul baru dgn tabel di database "frontline": tambahkan paket entity-nya di SINI
     * dan paket repository-nya di basePackages @EnableJpaRepositories di atas.
     */
    public static final String[] ENTITY_PACKAGES = {
            "com.example.app.modules.auth.entity",
            "com.example.app.modules.project.entity",
            "com.example.app.modules.testrepository.entity",
            "com.example.app.modules.testcase.entity",
            "com.example.app.modules.testrun.entity",
            "com.example.app.modules.automation.entity",
            "com.example.app.shared.activitylog"
    };

    @Primary
    @Bean(name = "primaryDataSourceProperties")
    @org.springframework.boot.context.properties.ConfigurationProperties("spring.datasource")
    public DataSourceProperties primaryDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean(name = "primaryDataSource")
    public DataSource primaryDataSource(
            @Qualifier("primaryDataSourceProperties") DataSourceProperties properties
    ) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Primary
    @Bean(name = "primaryEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean primaryEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("primaryDataSource") DataSource dataSource
    ) {
        return builder
                .dataSource(dataSource)
                .packages(ENTITY_PACKAGES)
                .persistenceUnit("primary")
                .build();
    }

    @Primary
    @Bean(name = "primaryTransactionManager")
    public PlatformTransactionManager primaryTransactionManager(
            @Qualifier("primaryEntityManagerFactory") EntityManagerFactory entityManagerFactory
    ) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}

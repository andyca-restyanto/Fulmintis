// backend/src/main/java/com/example/app/shared/config/MasterDataSourceConfig.java
package com.example.app.shared.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * Datasource KEDUA -> database "master_data" (TERPISAH dari "frontline").
 * Database ini HARUS sudah dibuat manual dulu di PostgreSQL sebelum aplikasi
 * di-start -- lihat catatan di README.
 *
 * Berisi tabel-tabel dedicated (BUKAN 1 tabel generic multi-kategori):
 * saat ini "user_type" (com.example.app.modules.usertype) dan "project_team"
 * (com.example.app.modules.projectteam). Kalau nanti ada master data lain,
 * buat entity/repository dedicated baru lagi di module masing-masing, lalu
 * tambahkan package-nya ke basePackages/.packages() di bawah -- tetap
 * connect ke database "master_data" yang sama.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = {
                "com.example.app.modules.usertype.repository",
                "com.example.app.modules.projectteam.repository"
        },
        entityManagerFactoryRef = "masterDataEntityManagerFactory",
        transactionManagerRef = "masterDataTransactionManager"
)
public class MasterDataSourceConfig {

    /** Paket entity yang dikelola datasource "master_data" (lihat catatan di PrimaryDataSourceConfig.ENTITY_PACKAGES). */
    public static final String[] ENTITY_PACKAGES = {
            "com.example.app.modules.usertype.entity",
            "com.example.app.modules.projectteam.entity"
    };

    @Bean(name = "masterDataSourceProperties")
    @ConfigurationProperties("app.datasource.masterdata")
    public DataSourceProperties masterDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "masterDataSource")
    public DataSource masterDataSource(
            @Qualifier("masterDataSourceProperties") DataSourceProperties properties
    ) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean(name = "masterDataEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean masterDataEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("masterDataSource") DataSource dataSource
    ) {
        return builder
                .dataSource(dataSource)
                .packages(ENTITY_PACKAGES)
                .persistenceUnit("masterdata")
                .build();
    }

    @Bean(name = "masterDataTransactionManager")
    public PlatformTransactionManager masterDataTransactionManager(
            @Qualifier("masterDataEntityManagerFactory") EntityManagerFactory entityManagerFactory
    ) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}

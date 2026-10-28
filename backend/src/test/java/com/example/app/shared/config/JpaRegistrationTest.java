// filepath: /backend/src/test/java/com/example/app/shared/config/JpaRegistrationTest.java
package com.example.app.shared.config;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pengaman pendaftaran JPA untuk DUA datasource (frontline = primary, master_data).
 * <p>
 * Latar belakang: repository dan entity didaftarkan PER PAKET secara eksplisit di PrimaryDataSourceConfig /
 * MasterDataSourceConfig. Modul baru yang paketnya lupa didaftarkan lolos kompilasi dan semua unit test
 * (yang memakai repository palsu), tetapi aplikasi GAGAL START ("required a bean of type '...Repository'
 * that could not be found", lalu "Not a managed type"). Test ini membuat kesalahan itu gagal di build.
 * <p>
 * Test juga membangun metadata Hibernate dan SEMUA repository Spring Data tanpa koneksi database, sehingga
 * pemetaan entity (kolom indeks, constraint, enum) dan semua query turunan/JPQL ikut tervalidasi.
 */
class JpaRegistrationTest {

    private static final String BASE_PACKAGE = "com.example.app";

    // ---------- Penemuan kelas ----------

    private static List<Class<?>> scanEntities() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        List<Class<?>> result = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(BASE_PACKAGE)) {
            result.add(Class.forName(definition.getBeanClassName()));
        }
        return result;
    }

    private static List<Class<?>> scanRepositories() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
                return definition.getMetadata().isInterface() && definition.getMetadata().isIndependent();
            }
        };
        scanner.addIncludeFilter(new AssignableTypeFilter(Repository.class));
        List<Class<?>> result = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(BASE_PACKAGE)) {
            Class<?> type = Class.forName(definition.getBeanClassName());
            if (!type.isAnnotationPresent(NoRepositoryBean.class)) {
                result.add(type);
            }
        }
        return result;
    }

    private static boolean isIn(Class<?> type, String[] packages) {
        String name = type.getPackageName();
        return Arrays.stream(packages).anyMatch(pkg -> name.equals(pkg) || name.startsWith(pkg + "."));
    }

    private static String[] repositoryPackages(Class<?> config) {
        return config.getAnnotation(EnableJpaRepositories.class).basePackages();
    }

    private static final String[] PRIMARY_REPOSITORY_PACKAGES = repositoryPackages(PrimaryDataSourceConfig.class);
    private static final String[] MASTER_REPOSITORY_PACKAGES = repositoryPackages(MasterDataSourceConfig.class);

    // ---------- Pendaftaran paket ----------

    @Test
    void everyRepositoryIsRegisteredWithExactlyOneDatasource() throws Exception {
        List<String> problems = new ArrayList<>();
        for (Class<?> repository : scanRepositories()) {
            boolean primary = isIn(repository, PRIMARY_REPOSITORY_PACKAGES);
            boolean master = isIn(repository, MASTER_REPOSITORY_PACKAGES);
            if (!primary && !master) {
                problems.add(repository.getName() + " TIDAK terdaftar di @EnableJpaRepositories datasource mana pun -- "
                        + "tambahkan paket '" + repository.getPackageName() + "' ke basePackages PrimaryDataSourceConfig "
                        + "(atau MasterDataSourceConfig)");
            }
            if (primary && master) {
                problems.add(repository.getName() + " terdaftar di KEDUA datasource");
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void everyEntityIsRegisteredWithExactlyOneDatasource() throws Exception {
        List<String> problems = new ArrayList<>();
        for (Class<?> entity : scanEntities()) {
            boolean primary = isIn(entity, PrimaryDataSourceConfig.ENTITY_PACKAGES);
            boolean master = isIn(entity, MasterDataSourceConfig.ENTITY_PACKAGES);
            if (!primary && !master) {
                problems.add(entity.getName() + " TIDAK terdaftar di ENTITY_PACKAGES datasource mana pun -- "
                        + "tambahkan paket '" + entity.getPackageName() + "' ke PrimaryDataSourceConfig.ENTITY_PACKAGES "
                        + "(atau MasterDataSourceConfig)");
            }
            if (primary && master) {
                problems.add(entity.getName() + " terdaftar di KEDUA datasource");
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void aRepositoryAndItsEntityLiveInTheSameDatasource() throws Exception {
        List<String> problems = new ArrayList<>();
        for (Class<?> repository : scanRepositories()) {
            Class<?>[] typeArguments = GenericTypeResolver.resolveTypeArguments(repository, Repository.class);
            if (typeArguments == null || typeArguments.length == 0) {
                continue;
            }
            Class<?> entity = typeArguments[0];
            boolean repositoryOnPrimary = isIn(repository, PRIMARY_REPOSITORY_PACKAGES);
            boolean entityOnPrimary = isIn(entity, PrimaryDataSourceConfig.ENTITY_PACKAGES);
            if (repositoryOnPrimary != entityOnPrimary) {
                problems.add(repository.getSimpleName() + " (datasource " + (repositoryOnPrimary ? "primary" : "master_data")
                        + ") mengelola entity " + entity.getSimpleName() + " yang ada di datasource "
                        + (entityOnPrimary ? "primary" : "master_data"));
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    // ---------- Pemetaan Hibernate & query Spring Data (tanpa database) ----------

    /** ConnectionProvider yang tidak pernah dipakai: membuktikan tidak ada koneksi yang dibuka saat membangun metadata. */
    public static final class NoConnectionProvider implements ConnectionProvider {
        @Override
        public Connection getConnection() {
            throw new IllegalStateException("Test ini tidak boleh membuka koneksi database.");
        }

        @Override
        public void closeConnection(Connection connection) {
        }

        @Override
        public boolean supportsAggressiveRelease() {
            return false;
        }

        @Override
        public boolean isUnwrappableAs(Class<?> unwrapType) {
            return false;
        }

        @Override
        public <T> T unwrap(Class<T> unwrapType) {
            throw new UnsupportedOperationException();
        }
    }

    private static SessionFactory buildSessionFactory(List<Class<?>> entities, Path ddlScript) {
        Map<String, Object> settings = new HashMap<>();
        // Skrip DDL ditulis ke berkas SAAT SessionFactory dibangun -- tanpa koneksi database (lihat NoConnectionProvider).
        settings.put("jakarta.persistence.schema-generation.scripts.action", "create");
        settings.put("jakarta.persistence.schema-generation.scripts.create-target", ddlScript.toString());
        settings.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        settings.put("hibernate.boot.allow_jdbc_metadata_access", "false");
        settings.put("hibernate.connection.provider_class", NoConnectionProvider.class.getName());
        settings.put("hibernate.hbm2ddl.auto", "none");
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder().applySettings(settings).build();
        MetadataSources sources = new MetadataSources(registry);
        entities.forEach(sources::addAnnotatedClass);
        return sources.buildMetadata().buildSessionFactory();
    }

    private void validateDatasource(String name, String[] entityPackages, String[] repositoryPackages) throws Exception {
        List<Class<?>> entities = scanEntities().stream().filter(entity -> isIn(entity, entityPackages)).toList();
        List<Class<?>> repositories = scanRepositories().stream().filter(repo -> isIn(repo, repositoryPackages)).toList();
        assertTrue(!entities.isEmpty(), name + ": tidak ada entity");

        Path ddlScript = Files.createTempFile("jpa-ddl-", ".sql");
        try (SessionFactory sessionFactory = buildSessionFactory(entities, ddlScript);
             EntityManager entityManager = sessionFactory.createEntityManager()) {
            assertDdlIsSelfConsistent(name, Files.readString(ddlScript));
            JpaRepositoryFactory factory = new JpaRepositoryFactory(entityManager);
            for (Class<?> repository : repositories) {
                // Membuat repository memvalidasi SEMUA method: nama query turunan (properti harus ada), JPQL @Query, parameter.
                try {
                    factory.getRepository(repository);
                } catch (RuntimeException e) {
                    throw new AssertionError(name + ": repository " + repository.getSimpleName() + " tidak valid: " + rootMessage(e), e);
                }
            }
        }
    }

    // ---------- Konsistensi DDL ----------
    // Hibernate TIDAK memvalidasi nama kolom pada @Index / @UniqueConstraint: kolom yang salah ketik lolos membangun
    // metadata dan baru gagal di PostgreSQL saat tabel dibuat. Karena DDL-nya bisa dihasilkan tanpa database,
    // setiap kolom yang dirujuk indeks / unique / foreign key diperiksa terhadap kolom tabelnya.

    private static final Pattern CREATE_TABLE = Pattern.compile("(?is)^create table (?:if not exists )?(\\w+)\\s*\\((.*)\\)$");
    private static final Pattern CREATE_INDEX = Pattern.compile("(?is)^create (?:unique )?index \\w+ on (\\w+)\\s*\\(([^)]*)\\)");
    private static final Pattern ALTER_CONSTRAINT = Pattern.compile(
            "(?is)^alter table (?:if exists )?(\\w+) add constraint \\w+ (unique|foreign key)\\s*\\(([^)]*)\\)(?:\\s+references\\s+(\\w+))?");

    static void assertDdlIsSelfConsistent(String datasource, String ddl) {
        Map<String, Set<String>> columnsByTable = new HashMap<>();
        List<String> problems = new ArrayList<>();
        List<String[]> references = new ArrayList<>(); // {tabel, jenis, kolom-csv, tabel-rujukan}

        for (String raw : ddl.split(";")) {
            String statement = raw.trim().replaceAll("\\s+", " ");
            if (statement.isEmpty()) {
                continue;
            }
            Matcher table = CREATE_TABLE.matcher(statement);
            if (table.find()) {
                Set<String> columns = new HashSet<>();
                for (String definition : splitTopLevel(table.group(2))) {
                    String trimmed = definition.trim();
                    String lower = trimmed.toLowerCase();
                    if (lower.startsWith("primary key") || lower.startsWith("unique") || lower.startsWith("foreign key")
                            || lower.startsWith("constraint") || lower.startsWith("check")) {
                        Matcher inline = Pattern.compile("(?i)^(?:constraint\\s+\\w+\\s+)?(unique|primary key|foreign key)\\s*\\(([^)]*)\\)(?:\\s+references\\s+(\\w+))?").matcher(trimmed);
                        if (inline.find()) {
                            references.add(new String[]{table.group(1), inline.group(1), inline.group(2), inline.group(3)});
                        }
                    } else {
                        columns.add(trimmed.split(" ")[0].replace("\"", "").toLowerCase());
                    }
                }
                columnsByTable.put(table.group(1).toLowerCase(), columns);
                continue;
            }
            Matcher index = CREATE_INDEX.matcher(statement);
            if (index.find()) {
                references.add(new String[]{index.group(1), "index", index.group(2), null});
                continue;
            }
            Matcher alter = ALTER_CONSTRAINT.matcher(statement);
            if (alter.find()) {
                references.add(new String[]{alter.group(1), alter.group(2), alter.group(3), alter.group(4)});
            }
        }

        for (String[] ref : references) {
            Set<String> tableColumns = columnsByTable.get(ref[0].toLowerCase());
            if (tableColumns == null) {
                problems.add(ref[1] + " pada tabel '" + ref[0] + "' yang tidak ada di DDL");
                continue;
            }
            for (String column : ref[2].split(",")) {
                String name = column.trim().replaceAll("(?i)\\s+(asc|desc)$", "").replace("\"", "").toLowerCase();
                if (!tableColumns.contains(name)) {
                    problems.add(ref[1] + " pada tabel '" + ref[0] + "' merujuk kolom '" + name + "' yang TIDAK ADA (kolom yang ada: " + new TreeSet<>(tableColumns) + ")");
                }
            }
            if (ref[3] != null && !columnsByTable.containsKey(ref[3].toLowerCase())) {
                problems.add("foreign key dari '" + ref[0] + "' merujuk tabel '" + ref[3] + "' yang tidak dikelola datasource ini");
            }
        }
        assertTrue(!columnsByTable.isEmpty(), datasource + ": DDL kosong (skrip tidak terbentuk)");
        assertTrue(problems.isEmpty(), datasource + ": DDL tidak konsisten:\n" + String.join("\n", problems));
    }

    /** Pisah berdasarkan koma di tingkat teratas (koma di dalam tanda kurung, mis. check (x in ('A','B')), tidak memisah). */
    private static List<String> splitTopLevel(String body) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (char c : body.toCharArray()) {
            if (c == '(') depth++;
            if (c == ')') depth--;
            if (c == ',' && depth == 0) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            parts.add(current.toString());
        }
        return parts;
    }

    // Menjaga pemeriksa DDL itu sendiri dari "lulus karena tidak memeriksa apa-apa".
    @Test
    void theDdlCheckerRejectsBrokenIndexUniqueAndForeignKeyColumns() {
        String good = "create table t (id uuid not null, a varchar(5), b integer, check (a in ('x','y')), primary key (id));"
                + "create index i on t (a, b);"
                + "alter table if exists t add constraint u unique (a);";
        assertDdlIsSelfConsistent("uji", good); // tidak melempar

        // Bentuk yang dipakai Hibernate utk @UniqueConstraint: constraint DI DALAM create table.
        assertDdlIsSelfConsistent("uji", "create table u (id uuid not null, p uuid not null, primary key (id), constraint uq_u unique (p));");

        for (String broken : List.of(
                "create table u (id uuid not null, p uuid not null, primary key (id), constraint uq_u unique (project_idX));",
                "create table u (id uuid not null, primary key (idX));",
                good + "create index j on t (a, zzz);",
                good + "alter table if exists t add constraint u2 unique (nope);",
                good + "alter table if exists t add constraint f foreign key (a) references tidak_ada;",
                good + "create index k on tidak_ada (a);")) {
            boolean failed = false;
            try {
                assertDdlIsSelfConsistent("uji", broken);
            } catch (AssertionError expected) {
                failed = true;
            }
            assertTrue(failed, "pemeriksa DDL harus menolak: " + broken);
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage();
    }

    @Test
    void primaryDatasourceMappingsAndAllRepositoryQueriesAreValid() throws Exception {
        validateDatasource("primary (frontline)", PrimaryDataSourceConfig.ENTITY_PACKAGES, PRIMARY_REPOSITORY_PACKAGES);
    }

    @Test
    void masterDatasourceMappingsAndAllRepositoryQueriesAreValid() throws Exception {
        validateDatasource("master_data", MasterDataSourceConfig.ENTITY_PACKAGES, MASTER_REPOSITORY_PACKAGES);
    }

    @Test
    void theScannerActuallyFindsTheModulesWeExpect() throws Exception {
        // Menjaga test ini sendiri dari "lulus karena memindai kosong".
        var repositoryNames = new TreeSet<String>();
        scanRepositories().forEach(r -> repositoryNames.add(r.getSimpleName()));
        assertTrue(repositoryNames.contains("TestCaseRepository"), repositoryNames.toString());
        assertTrue(repositoryNames.contains("UserTypeRepository"), repositoryNames.toString());
        assertTrue(repositoryNames.size() >= 10, "jumlah repository terdeteksi: " + repositoryNames.size());
        assertEquals(true, scanEntities().size() >= 10);
    }

    @Test
    void automationTablesAreGeneratedWithTheUniqueSetupConstraint() throws Exception {
        List<Class<?>> entities = scanEntities().stream().filter(e -> isIn(e, PrimaryDataSourceConfig.ENTITY_PACKAGES)).toList();
        Path ddl = Files.createTempFile("jpa-ddl-", ".sql");
        try (SessionFactory ignored = buildSessionFactory(entities, ddl)) {
            String script = Files.readString(ddl).replaceAll("\\s+", " ").toLowerCase();
            assertTrue(script.contains("create table automation_setup"), script);
            assertTrue(script.contains("create table automation_generation"), script);
            // maksimal 1 setup per project: unique di database, bukan hanya di kode
            assertTrue(script.contains("uq_automation_setup_project unique (project_id)"), script);
        }
    }
}

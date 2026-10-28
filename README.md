# Fullstack Monorepo — Auth, Dashboard, Multi-Database Master Data

Paket ini berisi: Register, Verifikasi Email, Login (JWT), Activity Log,
Dashboard (protected), dan Master Data User Type di **database PostgreSQL
terpisah** dengan pola in-memory join. Dibuat oleh Luhut (backend) dan Pigay
(frontend), sesuai Feature-Based Modular Architecture.

## Arsitektur Database (PENTING)

Aplikasi ini pakai **2 database PostgreSQL terpisah**, dikoneksikan lewat
**2 datasource berbeda** dalam 1 aplikasi Spring Boot:

| Database | Isi | Config datasource |
|---|---|---|
| `frontline` | `users`, `activity_log` | `PrimaryDataSourceConfig.java` (`@Primary`) |
| `master_data` | `user_type` (Free, VIP Monthly, VIP Yearly) | `MasterDataSourceConfig.java` |

Karena PostgreSQL **tidak bisa JOIN/FK lintas database**, kolom `users.user_type`
cuma menyimpan STRING code (misal `"FREE"`) — bukan foreign key. Tabel di
database `master_data` bernama **`user_type`** (dedicated, bukan tabel
generic). Setiap kali butuh LABEL-nya (misal "Free", "VIP Monthly"), aplikasi
melakukan:
1. Fetch Data A dari `frontline` (misal `User`)
2. Fetch Data B dari `master_data` (tabel `user_type`, by code) — 1 query utk banyak code sekaligus (hindari N+1)
3. **In-memory join** di kode Java pakai `Stream API` + `HashMap` (lihat `DashboardServiceImpl.java` dan `UserTypeLookupServiceImpl.java`)

## Struktur

```
backend/
  pom.xml
  .gitignore
  .env.example
  src/main/java/com/example/app/
    AppApplication.java
    modules/auth/
      controller/AuthController.java     (register, verify-email, login)
      service/AuthService.java + impl/
      service/EmailVerificationResult.java
      repository/UserRepository.java
      entity/User.java                   (+ verified, verificationToken, ..., user_type [string code])
      dto/RegisterRequestDTO.java, RegisterResponseDTO.java
      dto/LoginRequestDTO.java, LoginResponseDTO.java
      dto/ForgotPasswordRequestDTO.java, ResetPasswordRequestDTO.java
      exception/ (EmailAlreadyExists, InvalidCredentials, AccountNotVerified, InvalidOrExpiredResetToken)
      validation/ (ValidPassword, PasswordMatches, NewPasswordMatches, dst)
    modules/dashboard/
      controller/DashboardController.java   (GET /api/dashboard/summary, PROTECTED)
      service/DashboardService.java + impl/   (CONTOH in-memory join 2 database)
      dto/DashboardSummaryDTO.java           (+ userType, userTypeLabel)
    modules/usertype/
      entity/UserType.java                 (tabel dedicated "user_type"; DB TERPISAH "master_data")
      repository/UserTypeRepository.java     (+ findByCodeIn utk bulk join)
      service/UserTypeLookupService.java + Impl.java  (utility in-memory join, Stream/HashMap)
      UserTypeCode.java                    (konstanta: FREE, VIP_MONTHLY, VIP_YEARLY)
      seed/UserTypeSeeder.java             (auto-insert Free/VIP Monthly/VIP Yearly saat startup)
      controller/UserTypeController.java     (GET /api/user-types)
      dto/UserTypeResponseDTO.java
    modules/project/
      entity/Project.java                  (tabel "project"; database "frontline")
      entity/ProjectCollaboration.java     (tabel "project_collaboration"; FK ASLI ke Project & User, sama-sama di "frontline")
      repository/ProjectRepository.java, ProjectCollaborationRepository.java
      service/ProjectService.java + impl/   (create project + auto-assign OWNER)
      controller/ProjectController.java     (POST /api/projects, PROTECTED)
      dto/CreateProjectRequestDTO.java, ProjectResponseDTO.java
    modules/projectteam/
      entity/ProjectTeam.java              (tabel dedicated "project_team"; DB TERPISAH "master_data")
      repository/ProjectTeamRepository.java
      ProjectTeamCode.java                 (konstanta: OWNER, COLLABORATOR)
      seed/ProjectTeamSeeder.java           (auto-insert OWNER/COLLABORATOR saat startup)
    shared/
      config/SecurityBeansConfig.java   (PasswordEncoder bean)
      config/CorsConfig.java            (CORS rule, berlaku utk semua /api/**)
      config/SecurityConfig.java        (SecurityFilterChain + JWT filter)
      config/PrimaryDataSourceConfig.java     (datasource "frontline", @Primary, scan modules.auth + modules.project)
      config/MasterDataSourceConfig.java      (datasource "master_data", scan modules.usertype + modules.projectteam)
      security/JwtService.java, JwtAuthenticationFilter.java, JwtAuthenticationEntryPoint.java
      email/EmailService.java + Impl.java
      activitylog/ActivityLog.java + Repository + Service(Impl)
      exception/GlobalExceptionHandler.java
  src/main/resources/
    application.properties     (datasource ganda, CORS, SMTP, JWT, dll)
    application-local.properties   (kredensial KEDUA datasource lokal, JANGAN commit)
    db/migration/                       (referensi skema database "frontline")
      V1__create_users_table.sql
      V2__add_verification_columns_to_users.sql
      V3__create_activity_log_table.sql
      V4__add_user_type_column_to_users.sql
      V5__add_reset_password_columns_to_users.sql
      V6__create_project_and_collaboration_tables.sql
    db/master-data-migration/           (referensi skema database "master_data" -- TERPISAH)
      V1__create_user_type_table.sql
      V2__create_project_team_table.sql

frontend/
  vite.config.ts / tsconfig*.json   (alias "@" -> src, Tailwind v4)
  src/style.css                     (@import "tailwindcss";)
  src/main.ts / src/App.vue
  src/router/index.ts               (route guard "requiresAuth" utk dashboard)
  src/modules/auth/    (register, verify notice, login form, forgot/reset password, dst)
  src/modules/dashboard/
    views/DashboardView.vue           (nav, user dropdown, grid project / empty state)
    services/dashboard.service.ts     (GET /dashboard/summary, protected)
    types/dashboard.types.ts          (+ userType, userTypeLabel)
    routes.ts / index.ts
  src/modules/project/
    components/CreateProjectModal.vue   (form Project Name + Description)
    components/ProjectCard.vue          (kartu project + badge role Owner/Collaborator)
    services/project.service.ts         (POST /projects -- BELUM ada GET/list)
    types/project.types.ts
  src/shared/   (apiClient, tokenStorage, icons)
  .env / .env.production
```

## ⚠️ Setup WAJIB sebelum jalankan aplikasi (2 database!)

### 1. Buat KEDUA database + grant akses
Database `frontline` (kalau belum ada) dan database `master_data` (BARU,
WAJIB dibuat manual — JDBC tidak bisa auto-create database):
```sql
-- psql -U postgres
CREATE DATABASE frontline;   -- kalau belum ada
CREATE DATABASE master_data; -- WAJIB, database baru

\c frontline
GRANT ALL PRIVILEGES ON DATABASE frontline TO "your_db_username";
GRANT USAGE, CREATE ON SCHEMA public TO "your_db_username";
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON TABLES TO "your_db_username";
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON SEQUENCES TO "your_db_username";

\c master_data
GRANT ALL PRIVILEGES ON DATABASE master_data TO "your_db_username";
GRANT USAGE, CREATE ON SCHEMA public TO "your_db_username";
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON TABLES TO "your_db_username";
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON SEQUENCES TO "your_db_username";
```
Tanpa GRANT di level schema (bukan cuma level database), akan muncul error
`permission denied for table ...` — sudah pernah terjadi sebelumnya untuk
`frontline`, dan akan terulang untuk `master_data` kalau langkah ini dilewati.

### 2. Isi kredensial di `application-local.properties`
```properties
spring.datasource.username=your_db_username
spring.datasource.password=your_db_password
app.datasource.masterdata.username=your_db_username
app.datasource.masterdata.password=your_db_password
spring.mail.username=your_email@gmail.com
spring.mail.password=your_16_digit_app_password
app.security.jwt-secret=<base64 random string minimal 32 byte>
```
Boleh pakai role PostgreSQL yang sama untuk kedua database (asal sudah
di-GRANT ke keduanya seperti langkah 1), tidak wajib beda role.

### 3. Data user lama (kalau sudah pernah register sebelum update ini)
Kolom `users.user_type` (NOT NULL) akan kosong untuk user lama:
```sql
TRUNCATE TABLE users CASCADE;
-- atau: UPDATE users SET user_type = 'FREE' WHERE user_type IS NULL;
```

### 4. Jalankan
```bash
mvn clean install
mvn spring-boot:run
```
Tabel di kedua database (termasuk `project`, `project_collaboration` di
`frontline`, dan `project_team` di `master_data`) + seed data 3 user type
(`FREE`, `VIP_MONTHLY`, `VIP_YEARLY`) dan 2 project team (`OWNER`,
`COLLABORATOR`) otomatis dibuat oleh Hibernate + seeder saat startup. Tidak
perlu `CREATE DATABASE` baru lagi -- tabel baru cukup nambah ke database yang
sudah ada.

## Endpoint

### `POST /api/auth/register`
Response 201: `{ id, email, createdAt, message }` — `user_type` otomatis `FREE`.

### `GET /api/auth/verify-email?token=xxx`
Redirect 302 ke `{frontend}/auth/signin?verified=true|false&reason=...`

### `POST /api/auth/login`
Response 200: `{ accessToken, tokenType, expiresIn, id, email }`

### `GET /api/dashboard/summary` — PROTECTED
Response 200 (hasil in-memory join 2 database):
```json
{
  "email": "user@example.com",
  "message": "Selamat datang di dashboard!",
  "userType": "FREE",
  "userTypeLabel": "Free"
}
```

### `GET /api/user-types` — PROTECTED
```json
[
  { "id": 1, "code": "FREE", "label": "Free" },
  { "id": 2, "code": "VIP_MONTHLY", "label": "VIP Monthly" },
  { "id": 3, "code": "VIP_YEARLY", "label": "VIP Yearly" }
]
```

### `POST /api/auth/forgot-password`
Request: `{ email }`. Response SELALU sama persis (generik) baik email
terdaftar atau tidak (cegah user enumeration):
```json
{ "message": "Kalau email tersebut terdaftar, kami sudah mengirim link reset password ke email itu." }
```
Kalau email terdaftar, sistem generate token (expire 60 menit) dan kirim
email berisi link **langsung ke frontend**: `{frontend}/auth/reset-password?token=xxx`
(beda dari verifikasi email yang linknya ke backend — di sini user perlu isi form).

### `GET /api/auth/reset-password/validate?token=xxx`
Dipanggil frontend saat halaman reset password dibuka, SEBELUM user isi
form. `200 { "valid": true }` kalau token valid, `400` kalau invalid/expired.

### `POST /api/auth/reset-password`
Request: `{ token, newPassword, confirmNewPassword }`. Set password baru,
token otomatis di-invalidate (sekali pakai). Response:
```json
{ "message": "Password berhasil diubah. Silakan login dengan password baru kamu." }
```

### `POST /api/auth/resend-verification`
Publik. Body `{ "email": "..." }`. Respons SELALU 200 dengan pesan generik (tidak membocorkan
apakah email terdaftar). Cooldown 60 detik per email, rate limit per IP.

### Manajemen team member — PROTECTED, OWNER-only
- `GET /api/projects/{projectId}/collaborators/search?email=exact@mail.com` — cocok PERSIS
  dengan email, hanya user verified, maks 1 hasil (`keyword` masih diterima sebagai alias).
- `PATCH /api/projects/{projectId}/collaborators/{collaborationId}` — body `{ "projectTeam": "OWNER|COLLABORATOR" }`.
- `DELETE /api/projects/{projectId}/collaborators/{collaborationId}` — 204.
- 409 kalau aksi membuat project tanpa OWNER. `collaborationId` bertipe angka (Long).

### Menu Automation (generate kode automation dengan AI) — PROTECTED
Semua di bawah `/api/projects/{projectId}/automation`; non-member 404. **OWNER** mengatur setup, **semua member** boleh generate.

| Endpoint | Keterangan |
|---|---|
| `GET /options` | Daftar framework, bahasa, pola, dan **matriks kompatibilitas** (satu sumber kebenaran; FE tidak menulis ulang aturannya) |
| `GET /setup` | Setup project; belum diatur -> `{ "configured": false }` |
| `PUT /setup` | OWNER. Upsert; **maksimal 1 setup per project** (unique di database). Body `{ framework, language, pattern?, structureNotes? }` |
| `DELETE /setup` | OWNER. 204, idempoten |
| `POST /generations` | Body `{ "testCaseIds": [...] }`. **202** `{ id, status: "QUEUED" }`; proses di latar belakang |
| `GET /generations` | Riwayat (terbaru dulu, maks 20), tanpa isi berkas |
| `GET /generations/{id}` | Detail + `files` (hanya bila `SUCCEEDED`). Klien **polling** sampai `SUCCEEDED`/`FAILED` |
| `GET /generations/{id}/download` | Zip hasil (+ `README-AI-GENERATED.txt`); selain `SUCCEEDED` -> 409 |
| `GET /usage` | `{ aiEnabled, tier, maxTestCasesPerGeneration, dailyLimit, usedToday }` |

- **Kombinasi valid**: Playwright dan Selenium dengan Java/JavaScript/TypeScript/Python; Cypress hanya JavaScript/TypeScript
  (`Cypress + Java/Python` -> 400 `UNSUPPORTED_COMBINATION`, pesan "Cypress tidak mendukung Java.").
- **Status job**: `QUEUED` -> `RUNNING` -> `SUCCEEDED` | `FAILED`. Satu job aktif per user.
- **Error** (body `{ timestamp, status, message, errorCode }`; FE membedakan kasus lewat `errorCode`):
  `AUTOMATION_SETUP_REQUIRED` (409), `TOO_MANY_TEST_CASES` / `INVALID_TEST_CASE_SELECTION` (400),
  `GENERATION_IN_PROGRESS` / `GENERATION_NOT_READY` / `SETUP_CONFLICT` (409), `AI_PLAN_LIMIT_REACHED` (429, batas harian user,
  pesan spesifik), `AI_UNAVAILABLE` (503 saat diajukan, atau `errorCode` pada job `FAILED`).
- **`AI_UNAVAILABLE` sengaja umum**: "Layanan AI sedang tidak tersedia. Silakan coba lagi nanti." Dipakai untuk SEMUA kegagalan sisi
  provider (kuota/saldo habis, rate limit, tidak tersedia, timeout, ditolak), AI belum dikonfigurasi, dan batas global sistem --
  tanpa menyebut kuota/billing/provider. Bedanya hanya di log server.
- Job `FAILED` lain: `AI_INVALID_OUTPUT` (keluaran AI tidak bisa dipakai), `GENERATION_FAILED`, `GENERATION_INTERRUPTED`
  (server dimulai ulang saat job berjalan), `SERVER_BUSY` (antrean penuh). Job gagal TIDAK menghabiskan jatah harian user.
- `aiEnabled=false` (provider belum dikonfigurasi): tombol Generate harus dinonaktifkan; backend tetap menolak dengan `AI_UNAVAILABLE`.

```json
// GET /generations/{id} (SUCCEEDED)
{ "id": "uuid", "status": "SUCCEEDED", "framework": "PLAYWRIGHT", "language": "TYPESCRIPT", "pattern": "PAGE_OBJECT_MODEL",
  "testCaseCount": 2, "requestedBy": "a@b.com", "createdAt": "...", "finishedAt": "...",
  "files": [ { "path": "tests/login.spec.ts", "content": "..." } ], "notes": null, "errorCode": null, "errorMessage": null }
```

**Konfigurasi AI** (`app.ai.*`, lihat `application-prod.properties`): `provider` kosong = nonaktif; `fake` = klien palsu
(hanya dev/uji, **aplikasi gagal start bila dipakai di profil prod**); blok `free.*` dan `vip.*` (`model`, `max-test-cases`,
`daily-limit`, `max-output-tokens`, `timeout-seconds`); `global-daily-limit`; API key hanya dari environment. Tier mengikuti tipe user:
`FREE` -> free; `VIP_MONTHLY`/`VIP_YEARLY` -> vip.

**Memasang provider AI sungguhan** (satu-satunya bagian yang bergantung provider): buat SATU kelas `@Component` yang
mengimplementasikan `shared/ai/AiClient` (`providerId()` = nilai `app.ai.provider`, `generate(AiRequest)` memanggil API provider dengan
`RestClient`, menerjemahkan SEMUA kegagalan menjadi `AiProviderException` dengan `Kind` yang sesuai -- terutama `QUOTA_EXHAUSTED`
untuk kuota/saldo habis), lalu isi variabel environment `AI_*`. Tidak ada kelas lain yang perlu diubah.
Uji dulu dengan `app.ai.provider=fake` dan `app.ai.fake.mode=QUOTA_EXHAUSTED|RATE_LIMITED|UNAVAILABLE|TIMEOUT|REJECTED|INVALID_OUTPUT|WRAPPED_OUTPUT`.

### Import test case dari Excel — PROTECTED, OWNER maupun COLLABORATOR
- `GET /api/projects/{projectId}/test-cases/import/template` — unduh `test-case-import-template.xlsx`
  (header, baris contoh, dropdown Priority/Test type/Scenario type, sheet Petunjuk).
- `POST /api/projects/{projectId}/test-cases/import?folderId={uuid}&dryRun=true|false` —
  `multipart/form-data`, field `file` (.xlsx, maks 5 MB, maks 500 baris). Semua baris masuk ke folder `folderId`.
  `dryRun` default **true** (hanya memeriksa, tidak menyimpan).

Kolom template (urutan bebas, nama header dikenali tanpa membedakan huruf besar/kecil, spasi, tanda hubung):
`Title`*, `Priority`*, `Test type`*, `Scenario type`, `Description`, `Objective`, `Pre-condition`,
`Test step`, `Expected results` (\* wajib). ID dibuat otomatis (UUID).

```json
{
  "folderId": "uuid", "folderName": "folder 1", "dryRun": true,
  "totalRows": 12, "validRows": 11, "importedCount": 0,
  "columnMapping": [ { "excelColumn": "Judul", "field": "title" } ],
  "unmappedColumns": ["Owner"],
  "errors":   [ { "row": 5, "column": "Priority", "message": "Nilai 'Urgent' tidak dikenal. Gunakan Highest, High, Medium, atau Low." } ],
  "warnings": [ { "row": 7, "column": "Test step", "message": "Jumlah step (3) tidak sama dengan expected results (2); baris kosong ditambahkan agar sejajar." } ],
  "preview":  [ { "row": 2, "title": "Login berhasil", "priority": "HIGH", "type": "MANUAL", "scenarioType": "POSITIVE", "stepCount": 3 } ]
}
```
- `row` = nomor baris di Excel (header = baris 1). `dryRun=true` tidak menyimpan apa pun (`importedCount` 0) dan
  baris bermasalah muncul di `errors` dengan respons 200.
- `dryRun=false` tanpa error: **semua** baris disimpan; `importedCount` = jumlah test case dibuat.
  Ada error: **400** dengan body yang sama, tidak ada yang disimpan (semua atau tidak sama sekali).
- File tidak bisa diproses (bukan .xlsx, rusak, kolom wajib tidak ada, > 500 baris, > 5 MB) -> **400** `{ status, message }`.
- Baris contoh dari template (Title diawali `[CONTOH]`) dilewati dengan peringatan.
- Test step/expected results: satu langkah per baris di dalam sel; disimpan bernomor `1. ...\n2. ...` (format yang sama dengan UI).
- Dibatasi rate limit (default 10 / menit / IP, `app.rate-limit.import-per-minute`).

### `GET /api/projects/{projectId}/dashboard` — PROTECTED, semua member
Semua angka dashboard project dalam satu panggilan (OWNER maupun COLLABORATOR; non-member 404).
Hanya menghitung test case `ACTIVE`; arsip dikirim terpisah.
```json
{
  "totalTestCases": 42, "archivedTestCases": 5,
  "scenario": { "positive": 28, "negative": 14 },
  "priority": { "highest": 6, "high": 14, "medium": 15, "low": 7 },
  "health":   { "passed": 20, "failed": 5, "blocked": 2, "pending": 9 },
  "latestRun": { "id": "uuid", "title": "...", "status": "RUNNING", "testCaseCount": 30,
                 "passedCount": 20, "failedCount": 3, "blockedCount": 1,
                 "progressPercentage": 80, "createdAt": "2026-10-01T09:30:00" },
  "activeRuns": [ { "...sama dengan latestRun..." } ],
  "executionTimeline": [ { "date": "2026-09-03", "passed": 4, "failed": 1, "blocked": 0 } ]
}
```
- `health` dihitung **sama persis dengan Report** (`GET /report/overview`): setiap hasil eksekusi dari semua
  test run di project, per status. Keempat angkanya identik dengan `totalPassed` / `totalFailed` /
  `totalBlocked` / `totalPending` di Report; jumlahnya = `totalExecutions` (bukan `totalTestCases`).
  `pending` = NEW + PENDING (di dashboard dilabeli "Not Run"). Test case yang belum masuk test run tidak
  dihitung; test case yang sama di 2 run dihitung 2x. Hanya `totalTestCases`, `scenario`, dan `priority`
  yang membatasi diri ke test case `ACTIVE`.
- `latestRun` = `null` kalau belum ada run. `activeRuns` = run yang belum FINISHED **dan** belum 100%
  dieksekusi (maks 5, terbaru dulu).
- `executionTimeline` selalu 30 elemen (terlama -> hari ini), hari kosong bernilai 0.

### Batas laju (rate limit)
Login/register/forgot/resend/reset-password dan search collaborator dibatasi per IP per menit
-> `429` + header `Retry-After`. Detail & konfigurasi: lihat `CHANGES.md` bagian 11.

### Deploy produksi
Aktifkan `SPRING_PROFILES_ACTIVE=prod` dan isi environment variable yang dibutuhkan
`application-prod.properties` (DB_URL, DB_USERNAME, DB_PASSWORD, MASTER_DB_*, JWT_SECRET,
CORS_ALLOWED_ORIGINS, BACKEND_BASE_URL, FRONTEND_BASE_URL, MAIL_*). `MAIL_FROM` sebaiknya
berformat `Fulmintis <no-reply@domainanda.com>`; nama produk di email diatur `BRAND_NAME`
(default `Fulmintis`). Jalankan sekali
`db/master-data-migration/V1,V2` di database `master_data`. Coba dulu di DB kosong: profil ini
memakai `ddl-auto=validate`, jadi ketidakcocokan skema akan menggagalkan start (disengaja).

### `POST /api/projects` — PROTECTED
Request: `{ projectName, description? }`. User pembuat otomatis jadi `OWNER`
lewat entry baru di `project_collaboration`. Response 201:
```json
{
  "id": "uuid",
  "projectName": "Website Redesign",
  "description": "Redesign landing page",
  "createdAt": "2026-09-21T10:00:00",
  "ownerEmail": "user@example.com",
  "projectTeam": "OWNER"
}
```
⚠️ **Belum ada `GET /api/projects` (list)** — frontend cuma nampilin project
yang dibuat selama sesi berjalan (state lokal), hilang kalau halaman
di-refresh. Tambahkan endpoint list (join `project_collaboration` filter
`user_id`) kalau butuh persist beneran.

## Validasi Login
1. Hanya user terdaftar & verified yang bisa login (pesan error generik utk cegah user enumeration).
2. Tidak bisa bypass dashboard dengan paste URL — 2 lapis: route guard frontend
   (`router/index.ts`) + `SecurityConfig`/`JwtAuthenticationFilter` backend
   yang menolak request tanpa JWT valid dengan `401`.

## Aturan Password
Minimal 9 karakter: 1 huruf besar, 1 huruf kecil, 1 angka, 1 karakter spesial
(`@#$%^&+=!()-_.,?*`). Identik di backend (regex Java) & frontend (composable).

## Setup Frontend
```bash
npm install vue-router axios
npm install -D tailwindcss @tailwindcss/vite
npm run dev
```
Pastikan `vite.config.ts` punya plugin `tailwindcss()` + alias `@` -> `src`,
`src/style.css` cukup `@import "tailwindcss";`, `src/main.ts` panggil
`app.use(router)`, `src/App.vue` render `<router-view />`.

## Catatan Integrasi
- Link verifikasi email mengarah ke backend (`app.backend.base-url`), bukan
  langsung ke frontend (hindari CORS lewat AJAX).
- Token JWT disimpan di `localStorage`, otomatis ke-attach via interceptor `apiClient.ts`.
- Kalau nanti butuh fitur "list semua user + tipe-nya" (misal admin panel),
  reuse `UserTypeLookupService.getLabelsByCodes()` dengan banyak code
  sekaligus (bulk), JANGAN panggil satu-satu dalam loop (N+1 query lintas database).
- Kalau nanti ada master data lain (bukan cuma user type), buat tabel
  dedicated baru lagi (bukan tabel generic) di module masing-masing, tetap
  connect ke database "master_data" yang sama lewat `MasterDataSourceConfig`.
- Sesuaikan package `com.example.app` dan path project sesuai struktur repo
  Anda yang sebenarnya sebelum di-copy ke monorepo.

## Menambah modul baru yang punya tabel (checklist)
Aplikasi memakai **dua datasource** dan mendaftarkan repository/entity **per paket** (bukan pemindaian otomatis). Modul baru yang lupa didaftarkan
lolos kompilasi tetapi aplikasi gagal start (`required a bean of type '...Repository' that could not be found`, lalu `Not a managed type`).
1. Tabel di database `frontline` -> `shared/config/PrimaryDataSourceConfig`: tambahkan paket repository ke `basePackages` `@EnableJpaRepositories`
   dan paket entity ke `ENTITY_PACKAGES`. Tabel di `master_data` -> `MasterDataSourceConfig` (cara yang sama).
2. Jalankan `JpaRegistrationTest`: ia gagal dan menyebut paket mana yang terlewat; ia juga memvalidasi nama query, JPQL, dan kolom indeks/unique.
3. Buat migrasi Flyway (profil prod) dan pastikan entity cocok dengannya.

### Generate test case dengan AI (menu Test Repository) — PROTECTED, OWNER maupun COLLABORATOR
Dua langkah: **generate** menghasilkan DRAFT (belum tersimpan), user mereview/mengedit, lalu **commit** menyimpannya (semua atau tidak sama sekali).
Draft bersifat PRIBADI (user lain dibalas 404). Semua di bawah `/api/projects/{projectId}/test-cases/ai-generation`.

| Endpoint | Keterangan |
|---|---|
| `GET /usage` | `{ aiEnabled, tier, maxDraftsPerGeneration, maxRequirementChars, dailyLimit, usedToday, sandbox }` |
| `POST /` | Body `{ folderId, requirement, count?, includeNegative? }` -> **202** `{ id, status: "QUEUED" }`; proses di latar belakang |
| `GET /{id}` | Detail + `drafts` (hanya bila `SUCCEEDED` dan belum disimpan). Klien **polling** sampai `SUCCEEDED`/`FAILED` |
| `GET /pending` | Draft terakhir yang belum disimpan (utk melanjutkan review setelah reload), atau **204** |
| `POST /{id}/commit` | Body `{ folderId, testCases: [...] }` -> **201** `{ savedCount, folderId, folderName }` |

- **Batas (kuota TERPISAH dari Automation):** FREE 3 generate/hari & maks 3 draft per generate; VIP 15 generate/hari & maks 15 draft. Teks requirement maks 6.000 karakter.
  Konfigurasi `app.ai.{free|vip}.testcase-daily-limit` dan `testcase-max-drafts` adalah dua parameter yang BERBEDA. Generate gagal karena sisi AI tidak
  menghabiskan jatah; "Generate ulang" memakan jatah; commit tidak. `global-daily-limit` menghitung total LINTAS fitur (Automation + test case).
- **Hasil bisa kurang dari `count`** (mis. keluaran AI terpotong batas token): hanya draft yang LENGKAP diselamatkan; `requestedCount` vs `draftCount` + `truncated`.
  AI yang mengembalikan lebih dari `count` dipangkas. Draft tanpa judul/tanpa langkah dibuang, judul kembar dalam satu batch dibuang, nilai enum yang tidak dikenal
  dinormalkan (priority MEDIUM, type MANUAL, scenario kosong) -- user masih bisa mengubahnya di layar review.
- **Commit** memvalidasi ulang tiap item dgn aturan membuat test case manual; salah satu tidak valid = **400** `INVALID_DRAFTS` dgn `errors: [{ index, field, message }]`
  dan tidak ada yang tersimpan. Hanya boleh SEKALI per generate (klik ganda/dua tab: yang kedua **409** `GENERATION_ALREADY_COMMITTED`, tidak menggandakan test case).
  `testStep`/`expectedResult` dikirim sbg teks bernomor (`"1. ...\n2. ..."`, format yang sama dgn UI).
- **`duplicateOfExisting`** pada draft: judul sama (tanpa membedakan huruf besar/kecil) dgn test case aktif di folder tujuan. Hanya peringatan, tidak memblokir.
- **Privasi:** teks requirement TIDAK disimpan di database maupun log (hanya panjangnya); ia diteruskan ke pemroses lewat memori. Draft yang tidak pernah disimpan
  dikosongkan setelah `app.ai.testcase.draft-retention-days` (7); setelah commit draft langsung dikosongkan. `sandbox=true` berarti akun free tier penyedia: data BISA dipakai
  penyedia utk memperbaiki produknya -> UI harus memperingatkan "jangan masukkan data asli".
- **Error** (body `{ timestamp, status, message, errorCode }`, plus `errors` utk `INVALID_DRAFTS`): `INVALID_REQUEST`, `REQUIREMENT_TOO_LONG`, `TOO_MANY_DRAFTS` (400);
  `GENERATION_NOT_FOUND` (404); `GENERATION_IN_PROGRESS`, `GENERATION_NOT_READY`, `GENERATION_ALREADY_COMMITTED`, `DRAFT_EXPIRED` (409); `AI_PLAN_LIMIT_REACHED` (429, pesan spesifik);
  `AI_UNAVAILABLE` (503 saat diajukan, atau `errorCode` pada job `FAILED` -- pesan UMUM yang sama persis dgn Automation). Job `FAILED` lain: `AI_INVALID_OUTPUT`, `GENERATION_FAILED`,
  `GENERATION_INTERRUPTED`, `SERVER_BUSY`.

```json
// GET /{id} (SUCCEEDED)
{ "id": "uuid", "status": "SUCCEEDED", "folderId": "uuid", "folderName": "folder 1", "requestedCount": 5, "draftCount": 5,
  "truncated": false, "committed": false, "createdAt": "2026-10-05T10:00:00", "finishedAt": "2026-10-05T10:00:07",
  "drafts": [ { "tempId": "d1", "title": "...", "priority": "HIGH", "type": "MANUAL", "scenarioType": "POSITIVE",
                "description": "...", "objective": "...", "precondition": "...",
                "steps": [ { "action": "...", "expected": "..." } ], "duplicateOfExisting": false } ],
  "errorCode": null, "errorMessage": null }
```

#### Gemini (Google): setup development vs production
Provider sungguhan = `app.ai.provider=gemini` (satu provider untuk SELURUH fitur AI: Automation ikut memakainya). Untuk uji tanpa key: `app.ai.provider=fake`.

| | Development (lokal) | Production |
|---|---|---|
| Akun Google | project AI Studio **tanpa billing** (free tier) | project Google Cloud **terpisah dengan billing aktif** |
| Key | environment variable lokal (`GEMINI_API_KEY`) | environment variable server; batasi ke Generative Language API; pasang budget alert |
| `app.ai.billing-tier` | `free` (bawaan) -> `sandbox=true` | **`paid`** (wajib; tanpa itu aplikasi GAGAL START) |
| Model FREE / VIP | kelas Flash-Lite / kelas Flash (keduanya ada di free tier, jadi jalur VIP bisa diuji di dev) | idem (berbayar) |
| `global-daily-limit` | kecil, di bawah batas harian project di halaman rate limit AI Studio | sesuai anggaran bulanan |
| Data | **hanya requirement rekaan** (Google dapat memakai data free tier) | data asli boleh (tier berbayar) |

Contoh `application-local.properties` (file itu TIDAK ikut zip; JANGAN menaruh key di dalamnya):
```properties
app.ai.provider=gemini
app.ai.api-key=${GEMINI_API_KEY}
app.ai.billing-tier=free
app.ai.free.model=<ID model kelas Flash-Lite>
app.ai.vip.model=<ID model kelas Flash>
app.ai.global-daily-limit=<di bawah batas harian project>
```
**Key format `AQ.`** (format baru Google, selain `AIza` lama) diterima: adapter tidak pernah memvalidasi bentuk key. Ada laporan sebagian key AQ ditolak REST dgn 401
`UNAUTHENTICATED`; kalau terjadi, buat key dari Google Cloud Console utk Generative Language API. **Uji key dulu** (jalankan sendiri; key JANGAN ditempel ke chat/commit/zip):
```bash
export GEMINI_API_KEY='...'   # key baru dari AI Studio
curl -s "https://generativelanguage.googleapis.com/v1beta/models" -H "x-goog-api-key: $GEMINI_API_KEY"          # daftar ID model yang tersedia
curl -s -X POST "https://generativelanguage.googleapis.com/v1beta/models/<MODEL_ID>:generateContent" \
  -H "x-goog-api-key: $GEMINI_API_KEY" -H "Content-Type: application/json" \
  -d '{"contents":[{"parts":[{"text":"Balas hanya: OK"}]}]}'
```
Dua parameter Gemini BELUM terverifikasi terhadap API sungguhan, jadi bawaannya MATI: `app.ai.gemini.response-schema` (`off` | `response-schema` | `response-json-schema`; dokumentasi
resmi menandai `responseSchema` deprecated) dan `app.ai.gemini.thinking-budget` (token thinking ditagih sbg output). Aktifkan hanya setelah diuji dgn key nyata.
Tanpa keduanya, keluaran tetap divalidasi penuh oleh parser (mode JSON `responseMimeType` selalu dikirim utk fitur ini).


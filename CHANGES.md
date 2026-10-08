# Changelog — Gabuters TCMS (hasil kerja Luhut & Pigay)

Ringkasan seluruh penambahan/perubahan pada project ini. Semua kode sudah
diterapkan langsung ke source project (bukan cuma potongan kode) sehingga
project ini seharusnya bisa langsung `mvn spring-boot:run` (backend) dan
`npm run dev` (frontend) setelah `npm install` seperti biasa.

## 1. Edit & Delete (Archive) Test Case

**Backend (Luhut)** — `backend/.../modules/testcase/`
- Entity `TestCase` tambah kolom `status` (ACTIVE/ARCHIVED), `archivedAt`, `archivedBy`.
- Endpoint baru: `GET /{testCaseId}`, `PUT /{testCaseId}` (edit), `DELETE /{testCaseId}`
  (soft-delete → archive), `GET /archived` (list archived, OWNER-only).
- Delete test case **hanya OWNER**; test case archived **hanya bisa dilihat OWNER**
  (COLLABORATOR dibalas 404, anti-enumeration).

**Frontend (Pigay)** — `frontend/src/modules/testcase/`
- `EditTestCaseModal.vue`, `DeleteTestCaseModal.vue`, `ArchivedTestCasesModal.vue`.
- `TestCasePanel.vue` diperbarui: tombol Edit (semua role) & Delete (OWNER saja),
  tombol "Archived" (OWNER saja) untuk buka daftar test case yang diarsipkan.

## 2. Test Run — create run, mapping test case, eksekusi hasil

**Backend (Luhut)** — `backend/.../modules/testrun/` (modul baru)
- Table baru `test_run` & `test_result` (mapping test case ↔ test run + hasil eksekusi).
- Endpoint: create test run (OWNER-only), list, detail, tambah test case ke run
  yang sudah ada (OWNER & COLLABORATOR), update hasil eksekusi (status/comment),
  upload & download evidence (image/video, multipart), delete test run (OWNER-only).
- Search test case saat mapping mendukung cari **by title atau by UUID id**
  (`TestCaseSpecifications.titleOrIdContains`, dipakai ulang dari endpoint
  test-case yang sudah ada).
- Status `test_run` otomatis: PENDING → RUNNING → **FINISHED hanya kalau SEMUA
  test result berstatus PASSED**; kalau ada yang FAILED/BLOCKED/PENDING, tetap RUNNING.
- File evidence disimpan lokal di disk (`shared/storage/FileStorageService`),
  path/nama file yang tersimpan di DB, bukan bytes-nya langsung.

**Frontend (Pigay)** — `frontend/src/modules/testrun/` (modul baru)
- `TestRunsView.vue` — halaman menu Test Runs (list + empty state, sesuai mockup).
- `CreateTestRunModal.vue` — wizard 2 langkah (Details → Select Cases).
- `TestCaseSelector.vue` — search + filter folder + select-all, dipakai ulang di
  create modal & `ManageTestRunCasesModal.vue` (tambah test case ke run yang sudah ada).
- `DeleteTestRunModal.vue` — konfirmasi hapus (OWNER-only).
- `TestRunExecuteView.vue` — halaman tujuan tombol "Execute": ubah status/comment
  per test case, upload & lihat evidence (di-fetch via JWT lalu dibuka sbg blob URL,
  karena endpoint evidence protected dan tidak bisa dipasang langsung sbg `<img src>`).
- Route `project-test-runs` & `project-test-run-execute` sudah di-wire di
  `modules/project/routes.ts` (menggantikan placeholder lama).

## Yang perlu ditambahkan MANUAL (di-exclude dari zip sesuai aturan project)

`backend/src/main/resources/application.properties` milikmu perlu baris berikut
supaya upload evidence (terutama video) berfungsi:

```properties
# ---- File storage (evidence upload test run) ----
app.storage.base-dir=uploads

# ---- Multipart (upload evidence bisa berupa video, default Spring cuma 1MB) ----
spring.servlet.multipart.max-file-size=50MB
spring.servlet.multipart.max-request-size=50MB
```

Tidak ada perubahan yang perlu ditambahkan manual di sisi frontend (`vite.config.ts`,
`tsconfig*.json`, `main.ts`, `App.vue`, `style.css` tidak disentuh sama sekali oleh
perubahan ini, cuma di-exclude dari zip sesuai aturan).

---

## 3. Review & perbaikan (Luhut & Pigay)

**Backend (Luhut)**
- **[Compile error]** `TestRunServiceImpl` — header method `updateTestResult(...)`
  (`@Override`, `@Transactional`, `public ... updateTestResult(`) hilang sehingga
  file tidak bisa dikompilasi. Sudah dipulihkan.
- **[500 -> status benar]** `GlobalExceptionHandler` tidak menangani exception modul
  testcase & testrun, sehingga semuanya jatuh ke HTTP 500 padahal dokumentasi kode
  menyebut 404/400/409. Handler ditambahkan:
  `TestCaseNotFoundException` (404), `TestCaseAlreadyArchivedException` (409),
  `TestCaseArchivedException` (409), `TestRunNotFoundException` (404),
  `TestResultNotFoundException` (404), `EvidenceUploadException` (400),
  `MaxUploadSizeExceededException` (400, file > batas multipart).
- `TestRunServiceImpl.loadEvidence` — file evidence yang hilang dari disk sekarang
  dibalas 404 (sebelumnya 500 karena `IllegalStateException` dari storage).
- Migrasi (dokumentasi): dua file `V10__*` bentrok. Diurutkan ulang:
  `V10` add_name_column_to_users, `V11` add_archive_columns_to_test_case,
  `V12` create_test_run_and_test_result_tables.
- `TestResultRepository` — komentar yatim dirapikan.

**Frontend (Pigay)**
- **[Match DTO 100%]** `LoginResponse` & `DashboardSummary` ditambah `name: string | null`
  (ada di `LoginResponseDTO` / `DashboardSummaryDTO` backend tapi belum ada di tipe TS).
- `DashboardView` & `ProjectLayoutView` — nama tampilan sekarang memakai nama custom
  user; fallback ke turunan dari email kalau belum diisi.
- `TestRunExecuteView` — refetch setelah simpan status/comment/upload evidence sekarang
  senyap (`silent`), tidak lagi meng-unmount seluruh daftar (scroll tidak loncat).
  Dropdown status juga dikembalikan ke nilai semula kalau penyimpanan gagal.

**Catatan keputusan desain (TIDAK diubah)**
- `TestCaseArchivedException` saat ini tidak pernah dilempar: OWNER memang sengaja boleh
  mengedit test case archived (lihat komentar di `TestCaseServiceImpl.updateTestCase`).
  Kalau ingin archived jadi read-only, lempar exception itu di method tersebut
  (handler 409-nya sudah siap).

---

## 4. Manage Cases = checklist (uncheck = lepas mapping, check = tambah)

Test case yang sudah ada di test run sekarang TETAP tampil di daftar pilihan dalam
keadaan tercentang. Uncheck untuk melepas, check test case lain untuk menambah,
lalu "Save changes" (1x simpan).

**Backend (Luhut)** — `modules/testrun/`
- Endpoint baru `PUT /api/projects/{projectId}/test-runs/{testRunId}/test-cases`
  (OWNER & COLLABORATOR). Body `SyncTestRunCasesRequestDTO { testCaseIds }` = daftar
  AKHIR test case di run. Yang baru ditambah, yang hilang dari daftar dilepas.
- Atomik (1 transaksi): kalau ada id baru yang tidak valid/archived -> 404 dan TIDAK ADA
  perubahan tersimpan. File evidence baru dihapus dari disk SETELAH commit.
- `testCaseIds` boleh kosong (= kosongkan run, status balik PENDING) tapi tidak boleh
  null, supaya field yang lupa dikirim tidak menghapus semua mapping.
- Validasi "ACTIVE" hanya untuk id BARU: test case yang sudah di run lalu di-archive
  tetap dipertahankan selama id-nya masih dikirim.
- Status run dihitung ulang; activity log `SYNC_TEST_CASES_IN_TEST_RUN` (hanya kalau
  ada perubahan).
- Endpoint lama (`POST .../test-cases`, `DELETE .../test-cases/{id}`) TIDAK diubah.

**Frontend (Pigay)** — `modules/testrun/`
- `SyncTestRunCasesRequest` (match 100% dgn DTO) + `testRunService.syncTestCasesInRun()`.
- `TestCaseSelector.vue` tidak lagi menyembunyikan test case yang sudah ada di run
  (prop `excludeTestCaseIds` dihapus). `modelValue` = seluruh id tercentang, jadi
  search/filter folder tidak mempengaruhi centang yang sedang tidak tampil.
- `ManageTestRunCasesModal.vue` ditulis ulang: satu checklist, ringkasan
  "+N ditambah / −M dilepas", tombol Save changes (nonaktif kalau tidak ada perubahan).
  Kalau ada test case yang akan dilepas yang sudah punya hasil eksekusi/evidence,
  muncul konfirmasi merah dulu (data hilang permanen). Modal menutup setelah berhasil.

---

## 5. UI Manage Test Cases disesuaikan dengan mockup (Pigay)

Backend (Luhut) TIDAK berubah -- modal tetap memakai `PUT .../test-runs/{id}/test-cases`.

- `ManageTestRunCasesModal.vue`: judul "Manage Test Cases" (tanpa subjudul/garis pemisah),
  lebar `max-w-xl`, footer hanya tombol **Cancel** & **Save Changes** rata kanan.
  Save Changes tanpa perubahan = langsung menutup modal (tanpa API call).
  Konfirmasi merah untuk test case yang punya hasil/evidence tetap muncul, hanya kalau perlu.
- `TestCaseSelector.vue` (dipakai juga oleh Create Test Run): toggle "Select All" berubah
  jadi "Deselect All" saat semua tercentang, checkbox bulat gelap dengan ikon centang,
  baris tercentang berlatar abu-abu, ID `TC-001` & nama folder pakai font mono,
  tinggi daftar tetap (`h-60`) supaya area kosong tetap terlihat seperti mockup.

---

## 6. Halaman Execute disesuaikan dengan mockup + upload evidence

**Backend (Luhut)** — `modules/testrun/`
- `TestResultResponseDTO` ditambah `testCaseDescription`, `testCasePrecondition`,
  `testCaseTestStep`, `testCaseExpectedResult` (di-denormalisasi seperti `testCaseTitle`,
  jadi FE tidak perlu GET test case per baris dan COLLABORATOR tidak kena 404 kalau
  test case-nya belakangan di-archive) dan `stepResults` (`List<TestStepResultStatus>`,
  tidak pernah null).
- Enum baru `TestStepResultStatus` (NEW | PASSED | FAILED) + kolom baru
  `test_result.step_results` (TEXT, CSV nama enum, urutan = urutan step). Ditambahkan
  otomatis oleh Hibernate (ddl-auto=update); dokumentasi di `V13__add_step_results_to_test_result.sql`.
- `UpdateTestResultRequestDTO` ditambah `stepResults` (opsional, maks. 200):
  `null` = hasil per-step TIDAK diubah, `[]` = hapus semua tanda.

**Frontend (Pigay)** — `modules/testrun/views/TestRunExecuteView.vue` (ditulis ulang)
- Header: Back + "X/Y Completed (Z%)" + progress bar. "Completed" = PASSED/FAILED/BLOCKED.
- Kiri: daftar "TEST CASES" dengan titik warna status. Kanan: judul, deskripsi,
  PRE-CONDITIONS, TEST STEPS (#n, Expected, tombol ✓ / ✗ per step; klik lagi = batal),
  COMMENT / ACTUAL RESULT.
- Status test case diturunkan otomatis dari tanda step: ada FAILED -> FAILED,
  semua PASSED -> PASSED, sebagian PASSED -> PENDING, tidak ada -> NEW. Pilihan
  **Status** manual (di kanan judul) tetap ada supaya BLOCKED/PENDING bisa diset,
  dan untuk test case tanpa step.
- **Upload Evidence** (image/video, maks. 50MB, divalidasi di FE sebelum upload) +
  tombol **View** dan **Replace Evidence** kalau sudah ada.
- Simpan lewat antrean berurutan + update optimistis: klik cepat beruntun / pindah
  test case tidak saling menimpa. Comment tersimpan saat blur / pindah test case / Back.
- `types/testRun.types.ts` mengikuti DTO (100% match). `parseSteps` diekspor dari
  `modules/testcase/index.ts`. Ikon baru: `CircleCheckIcon`, `XCircleIcon`.

**Koreksi ukuran modal Manage Test Cases**
- Skala screenshot ternyata ~1,43x (dikalibrasi dari sidebar `w-64`), bukan 2x.
  Modal disesuaikan: `max-w-3xl`, `p-6`, tinggi daftar `21rem`; `TestCaseSelector`
  mendapat prop `listHeightClass` (default `h-60`, Create Test Run tidak berubah tinggi).

---

## 7. [SUPERSEDED — lihat "7. Modul baru `report`" & bagian 9 di bawah]
## Menu Report — tab Overview (Backend, Luhut)

> Entri ini menjelaskan endpoint `GET /reports/overview` (jamak) dgn bentuk
> `ReportOverviewResponseDTO` versi lama (`runningTestRuns`, `totalTestCases`,
> `passedTestCases`, dst, dihitung pakai `PassRateCalculator`). Endpoint dan
> DTO itu SUDAH DIGANTI TOTAL oleh pekerjaan berikutnya di bawah -- endpoint
> yang benar-benar ada di kode sekarang adalah `GET /report/overview`
> (tunggal), lihat entri "7. Modul baru `report`". `PassRateCalculator`
> sendiri sudah dihapus krn jadi dead code. Baris di bawah ini dibiarkan
> apa adanya sbg histori, JANGAN dijadikan referensi API.

Modul baru `backend/.../modules/report/` (controller / service / dto).

**API** — `GET /api/projects/{projectId}/reports/overview`
- PROTECTED (JWT). Boleh OWNER & COLLABORATOR; 404 kalau project tidak ada / bukan member.
- Response `ReportOverviewResponseDTO`:

| Field | Arti |
|---|---|
| `totalTestRuns` | Total test run di project |
| `runningTestRuns` | Test run berstatus RUNNING |
| `pendingTestRuns`, `finishedTestRuns` | Sisa breakdown (pending + running + finished = total) |
| `totalTestCases` | Semua test case yang ke-mapping ke test run mana pun (penyebut pass rate) |
| `passedTestCases`, `failedTestCases`, `blockedTestCases` | Jumlah per status dari SEMUA test run |
| `passRate` | passed / totalTestCases x 100, 1 desimal (0.0 kalau belum ada test case) |

Contoh: `{"totalTestRuns":3,"runningTestRuns":1,"pendingTestRuns":1,"finishedTestRuns":1,
"totalTestCases":12,"passedTestCases":8,"failedTestCases":2,"blockedTestCases":1,"passRate":66.7}`

**Keputusan definisi (bisa diubah di 1 tempat)**
- "Test case" = hasil eksekusi (baris `test_result`): test case yang sama di 2 run dihitung 2x.
- "Already running" = status RUNNING (bukan PENDING/FINISHED).
- Penyebut pass rate = SEMUA test case ter-mapping (termasuk NEW/PENDING), supaya run yang
  baru 1 dari 10 test case-nya PASSED tidak tampil 100%. Rumus ada di `PassRateCalculator`.

**Implementasi**: `TestRunRepository.countByProjectId / countByProjectIdAndStatus` dan
`TestResultRepository.countGroupedByStatus` (1 query GROUP BY) -- total 5 query ringan,
tanpa fetch baris. Tidak ada tabel/kolom baru, tidak ada perubahan config datasource/security.

**Flow "Add New Test Run"** (pilih test case -> Create) memakai endpoint yang SUDAH ada:
`GET .../test-cases?keyword=` (daftar & search) dan `POST .../test-runs` (create, OWNER-only).

**Tab Run Details**: belum dibuat (fase berikutnya); endpoint-nya ditambahkan di `ReportController`.

---

## 7. Modul baru `report` — API tab Overview (Luhut, backend only)

Requirement: menu Report dgn 2 tab (Overview & Run Details); tab Overview
menampilkan total test run, total test run yang sedang running, jumlah
test case passed/failed/blocked dari SEMUA test run, dan pass rate. Tab
Run Details next phase (BELUM dibuat). Flow "Add New Test Run" dari menu
Report memakai endpoint Test Run yang SUDAH ADA (`POST /test-runs`) --
tidak ada endpoint create-test-run baru.

**Endpoint baru:** `GET /api/projects/{projectId}/report/overview` (OWNER
maupun COLLABORATOR, sama seperti listing Test Run).

**Response `ReportOverviewResponseDTO`:**
```
totalTestRuns          -- jumlah test run project ini, semua status
totalRunningTestRuns   -- jumlah test run berstatus RUNNING
totalPassed            -- jumlah HASIL EKSEKUSI berstatus PASSED, semua test run
totalFailed            -- idem, FAILED
totalBlocked           -- idem, BLOCKED
passRatePercentage     -- totalPassed / totalExecutions * 100 (penyebut = SEMUA hasil,
                          termasuk NEW/PENDING), dibulatkan; 0 kalau belum ada test case
executionRatePercentage-- (totalPassed+totalFailed) / totalExecutions * 100, dibulatkan
                          (field tambahan, dipakai kartu Execution Rate di FE)
```
Definisi & alasan tiap angka didokumentasikan lengkap di javadoc
`ReportOverviewResponseDTO`. [Dikoreksi di bagian 11] Penyebut pass rate memang
SEMUA hasil (termasuk NEW/PENDING) -- sesuai kode `ReportServiceImpl`; catatan
lama di sini yang bilang penyebutnya hanya passed+failed+blocked sudah usang.

**File baru:** `modules/report/{dto,service,service/impl,controller}` --
`ReportOverviewResponseDTO`, `ReportService`, `ReportServiceImpl`,
`ReportController`. Tidak ada file lain yang diubah; query agregasi
(`TestRunRepository.countByProjectId/countByProjectIdAndStatus`,
`TestResultRepository.countGroupedByStatus`) sudah tersedia dari pekerjaan
sebelumnya.

**Belum dikerjakan (menunggu arahan Pigay/FE):** UI menu Report (2 tab,
kartu-kartu Overview, tombol "Add New Test Run") belum dibuat -- request ini
eksplisit hanya minta backend & API.

---

## 8. UI menu Report — tab Overview (Pigay, frontend)

Mengikuti mockup. Backend (Luhut) diperluas sedikit karena mockup butuh 2
angka yang belum ada di response `report/overview` (lihat bagian 7):

- **`ReportOverviewResponseDTO`** ditambah `totalExecutions` (total baris hasil
  eksekusi test_result, semua status, semua test run) dan `totalPending`
  (gabungan NEW + PENDING — tidak dibedakan di tab Overview). Dihitung dari
  agregat `countByStatus` yang sudah ada, TIDAK ada query baru ke DB.

**Frontend** — modul baru `modules/report/`:
- `ReportsView.vue`: judul "Reports" + subjudul, tombol **Export Excel**, tab
  **Overview** / **Run Details**. Tab Overview: 6 kartu (Total Runs, Total
  Executions, Passed, Failed, Blocked, Pass Rate) + "Overall Execution
  Distribution" (bar proporsional + legend Passed/Failed/Blocked/Pending).
  Tab **Run Details** placeholder "coming soon" (requirement #4, next phase).
- Route `project-reports` di `project/routes.ts` diarahkan ke `ReportsView.vue`
  (sebelumnya `ProjectMenuPlaceholderView`). Komponen placeholder itu sendiri
  TIDAK dihapus (masih dipakai ulang kalau ada menu baru sebelum modulnya siap),
  tapi saat ini tidak ada route yang memakainya.
- **Export Excel = CSV, bukan .xlsx asli.** Project ini tidak punya dependency
  pembuat file Excel (`package.json` cuma vue/vue-router/axios) dan sandbox
  tidak bisa `npm install` paket baru (jaringan dimatikan). File `.csv` yang
  dihasilkan bisa langsung dibuka Excel, tapi BUKAN format `.xlsx` asli (tidak
  ada styling/multi-sheet). Kalau butuh `.xlsx` asli, install `xlsx` (SheetJS)
  dulu di `package.json`, lalu isi ulang `utils/exportOverviewCsv.ts` -- fungsi
  ini sudah diisolasi supaya penggantinya tidak perlu ubah `ReportsView.vue`.
- Ikon baru: `DownloadIcon`.

---

## [Retroaktif, tidak terdokumentasi sebelumnya] Test Case — field Priority, Type, Scenario

Ditemukan saat audit modul report (bagian 9 di bawah): entity `TestCase`,
enum `TestCasePriority` (HIGHEST/HIGH/MEDIUM/LOW), `TestCaseType`
(MANUAL/AUTOMATION), `TestCaseScenarioType` (POSITIVE/NEGATIVE), serta field
di `CreateTestCaseRequestDTO`/`UpdateTestCaseRequestDTO`/`TestCaseResponseDTO`
SUDAH ADA & konsisten (priority & type wajib diisi, scenarioType opsional),
tapi belum pernah dicatat di sini. Migrasi dokumentasi `V14` ditambahkan
sekarang (kolomnya sendiri sudah ada di database, dibuat otomatis oleh
Hibernate ddl-auto=update) -- lihat peringatan operasional di file migrasi
itu soal ALTER TABLE NOT NULL pada tabel yang sudah ada isinya.

---

## 9. Report — tab Run Details + Export Excel (Luhut, backend)

Melanjutkan modul `report` (bagian 7 & 8). Saat audit ditemukan
`ReportService.getRunDetails()`, `ReportServiceImpl`, dan
`ReportExportService`/`ReportExportServiceImpl` (generate .xlsx pakai Apache
POI, dependency `poi-ooxml` sudah ada di `pom.xml`) SUDAH DIBUAT & konsisten
dgn DTO yang berlaku sekarang, tapi `ReportController` belum expose
endpoint-nya. Yang dikerjakan sekarang: 2 endpoint baru di `ReportController`.

**`GET /api/projects/{projectId}/report/run-details`** (requirement #1-2)
- OWNER maupun COLLABORATOR. Balas `List<TestRunDetailResponseDTO>` --
  SEMUA test run di project ini (terbaru dulu), masing-masing SUDAH lengkap
  dgn `testResults` (test case yang di-mapping + hasil eksekusinya), supaya
  FE bisa expand 1 baris test run (sesuai mockup) tanpa panggil API lagi.
  Delegasi ke `TestRunService.listTestRunDetails()` yang sudah ada -- tidak
  ada duplikasi logic mapping.

**`GET /api/projects/{projectId}/report/export`** (requirement #3-4)
- OWNER maupun COLLABORATOR. Balas file `.xlsx` (`Content-Disposition:
  attachment; filename="Test_Report_yyyyMMdd_HHmmss.xlsx"` -- pola nama sama
  dgn contoh file yang diberikan user), `Content-Type:
  application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`.
- Isi workbook (`ReportExportServiceImpl`, sudah ada sebelumnya):
  - **Sheet "Summary"** -- tabel Metric/Value (sama persis dgn kartu tab
    Overview: Total Runs, Total Executions, Passed, Failed, Blocked,
    Pending, Pass Rate %) + **diagram pie chart asli** (Apache POI
    `XDDFChart`) "Overall Execution Distribution" di sampingnya. **Beda
    dgn contoh file user**: contoh file TIDAK punya chart beneran (dicek
    langsung di XML-nya, kosong) dan punya baris "Skipped" terpisah dari
    "Pending" -- app ini tidak punya konsep status "skipped"
    (`TestResultStatus` cuma NEW/PENDING/PASSED/FAILED/BLOCKED, NEW+PENDING
    sudah digabung "Pending"), jadi baris itu SENGAJA tidak ditiru drpd
    menampilkan angka 0 yang menyesatkan (seolah ada fitur skip).
  - **Sheet "All Results"** -- flat, 1 baris per test case per test run,
    kolom Run Title/Run Status/Created At/Test Case/Priority/Type/Scenario/
    Status/Comment. Formatnya disamakan PERSIS dgn contoh file user.
  - **1 sheet per test run** (nama sheet = judul run, disanitasi & dibuat
    unik kalau ada 2 run bertitle sama -- lihat javadoc
    `uniqueSheetName()`), kolom Test Case/Priority/Type/Scenario/Status/
    Comment.
  - Kalau pembuatan chart gagal (mis. edge case versi POI), export TETAP
    jalan tanpa chart (di-log warning) drpd gagal total -- lihat
    `writeSummarySheet()`.

**File yang diubah:** hanya `ReportController.java` (tambah 2 endpoint +
javadoc yang tadinya bilang "Run Details belum dibuat" diperbaiki). Tidak
ada perubahan lain di modul report/testrun/testcase.

**Belum diverifikasi via `mvn compile`** -- sandbox tidak bisa `npm
install`/`mvn` krn jaringan dimatikan (Apache POI & Lombok tidak ada di
cache lokal). Sudah dicek: sintaks Java valid (`javac` parse-only, semua
error yang muncul murni "package tidak ditemukan" utk Lombok/POI/Spring
yang memang tidak ter-install di sandbox, bukan error struktur), field &
method yang dipanggil `ReportExportServiceImpl` (getter `TestCase*` di
`TestResultResponseDTO`, `getTestResults()`/`getTitle()`/`getStatus()`/
`getCreatedAt()` di `TestRunDetailResponseDTO`) semua sudah dicocokkan
manual baris-per-baris dgn DTO yang ada. **Jalankan `mvn compile` sebelum
deploy** utk validasi penuh Apache POI API usage (`XDDFChart`, dll) yang
tidak bisa dicek tanpa jar-nya.

---

## 10. UI tab Run Details + tombol Export Excel asli (Pigay, frontend)

Melengkapi modul `report` FE (bagian 8) memakai 2 endpoint baru Luhut
(bagian 9): `GET /report/run-details` dan `GET /report/export`.

**Sinkronisasi tipe yang tertinggal:** `TestResultResponse` (FE) belum
punya `testCasePriority`/`testCaseType`/`testCaseScenarioType` padahal
`TestResultResponseDTO` (backend) sudah punya 3 field itu sejak modul
report/testcase diperluas -- ditambahkan sekarang, type diimpor dari
`modules/testcase` (union `TestCasePriority`/`TestCaseType`/`TestCaseScenarioType`
yang sudah ada) supaya tidak didefinisikan dobel.

**`ReportsView.vue` — tab Run Details** (mengikuti mockup):
- Tabel Run Title/Status/Cases/Passed/Failed/Progress/Created, 1 baris per
  test run (lazy-load: baru fetch saat tab ini pertama dibuka).
- Klik baris = expand/collapse (ikon chevron) -> tabel detail Test Case/
  Priority/Type/Scenario/Status/Comment utk seluruh test case di run itu.
  Passed/Failed/Progress dihitung di FE dari `run.testResults` (status
  "Completed" = PASSED/FAILED/BLOCKED, konsisten dgn TestRunExecuteView.vue).

**Export Excel: sekarang file asli dari backend, bukan CSV client-side lagi.**
`utils/exportOverviewCsv.ts` (bagian 8) DIHAPUS -- `reportService.downloadExport()`
memanggil `GET /report/export` dgn `responseType: 'blob'` lalu trigger
download; nama file diambil dari header `Content-Disposition` yang dikirim
backend (fallback `Test_Report.xlsx` kalau header itu ternyata di-strip
sesuatu di tengah jalan, mis. proxy).

**File yang diubah:** `modules/testrun/types/testRun.types.ts` (3 field
baru), `modules/report/services/report.service.ts` (ditulis ulang: +
`getRunDetails`, ganti `downloadExport`), `modules/report/views/ReportsView.vue`
(ditulis ulang), `modules/report/utils/exportOverviewCsv.ts` (dihapus).

---

## 11. Hardening keamanan, akses, performa & database (Luhut, backend only)

Menindaklanjuti analisis Sudaryono. Semua perubahan backend; FE tetap jalan tanpa
perubahan KECUALI poin bertanda **[FE]** (Pigay).

### Keamanan
- **Evidence upload** (`shared/storage/EvidenceFileType`): tipe & ekstensi ditentukan dari
  ISI file (magic bytes), bukan `Content-Type`/nama file klien. Whitelist: png, jpg, gif,
  webp, mp4, mov, webm (SVG/HTML/HEIC ditolak -> 400). Download memakai `nosniff` + CSP
  `sandbox`; file lama berekstensi di luar whitelist otomatis disajikan sebagai
  `attachment` (tidak pernah inline).
- **JWT dicabut saat password berubah**: token membawa claim `pv` (sidik jari password hash).
  `JwtAuthenticationFilter` kini juga mengecek user masih ada + verified + `pv` cocok ->
  selain itu **401** (dulu user terhapus jadi 500). **Token lama tanpa `pv` tidak berlaku:
  semua user login ulang sekali setelah deploy.** `POST /api/auth/change-password` membalas
  `accessToken` baru **[FE: simpan token ini]**.
- **Rate limit** in-memory per IP (`shared/security/RateLimitFilter`): login, register,
  forgot/resend/reset-password, dan search collaborator -> 429 + `Retry-After`. Default bisa
  di-override lewat `app.rate-limit.*` (lihat bagian manual di bawah).
- **Cooldown 60 detik per email** untuk forgot-password & resend-verification.
- **Search collaborator** hanya cocok PERSIS dengan email dan hanya user verified (maks 1
  hasil). `add collaborator` juga hanya menerima user verified.

### Fitur baru
- `POST /api/auth/resend-verification` (publik, respons generik).
- `PATCH /api/projects/{projectId}/collaborators/{collaborationId}` (ubah role) dan
  `DELETE ...` (hapus member), OWNER-only, aturan **minimal 1 OWNER** (409). `collaborationId`
  bertipe **Long** (= `id` di response list collaborators).

### Integritas data & performa
- File evidence dihapus **setelah commit** (`FileStorageService.deleteAfterCommit`); file
  baru dibersihkan kalau transaksi rollback (`deleteIfRolledBack`).
- **Snapshot step**: `test_result.test_step_snapshot` / `expected_result_snapshot` diisi saat
  test case di-mapping ke run. Response tetap memakai field lama (`testCaseTestStep`,
  `testCaseExpectedResult`) tapi kini dari snapshot -> mengedit test case TIDAK lagi
  menggeser tanda PASSED/FAILED per-step di run yang sudah berjalan. Baris lama (snapshot
  NULL) fallback ke test case live. Untuk memakai step terbaru: lepas lalu tambah ulang
  test case di run.
- **Optimistic locking**: `test_result.version` (`@Version`). `TestResultResponseDTO.version`
  (baru) dan `UpdateTestResultRequestDTO.version` (opsional). Kalau dikirim dan sudah usang
  -> **409**. **[FE: kirim balik `version` dari response terakhir; tanpa itu hanya
  request yang nyaris bersamaan yang terlindungi.]**
- **N+1 dihapus**: `listTestRuns` = 1 query GROUP BY (dulu 1 query per run);
  `listTestRunDetails` (Run Details & export Excel) = 1 query untuk semua run; test case
  di-JOIN FETCH.
- **`ProjectAccessService`**: aturan "member? / OWNER?" kini satu tempat (dulu diulang 22x di
  6 service).
- **Index** di entity (`@Index`) + `V15`.

### Database / migrasi
- Dihapus file migrasi duplikat (`V10__add_archive...`, `V11__create_test_run...`; isi SQL
  identik dengan `V11`/`V12`). Urutan kini `V1..V15` tanpa nomor ganda.
- `V15__evidence_snapshot_version_and_indexes.sql` (idempotent, `IF NOT EXISTS`).
- **Profil `prod`** (`application-prod.properties`): Flyway aktif, `ddl-auto=validate`,
  `show-sql=false`, semua rahasia dari environment variable. Dependency Flyway ditambahkan
  ke `pom.xml` (di profil lokal tetap mati).

### Yang perlu ditambahkan MANUAL (file di-exclude dari zip sesuai aturan project)
`application.properties` (opsional, semua punya default):
```properties
# app.rate-limit.enabled=true
# app.rate-limit.login-per-minute=20
# app.rate-limit.register-per-minute=10
# app.rate-limit.email-per-minute=5
# app.rate-limit.search-per-minute=30
```
Kalau kolom baru tidak terbentuk otomatis di DB lokal, jalankan isi `V15` sekali secara manual.

### Tidak dikerjakan (alasan)
- **`POST /register` masih 409 untuk email yang sudah ada.** Menjadikannya generik mengubah
  kontrak FE (halaman register menampilkan pesan dari 409). Dimitigasi rate limit; perlu
  keputusan Sudaryono/Pigay.
- **userId di principal**: akan mengubah `authentication.getName()` yang dipakai semua
  controller; hemat 1 query/service call, tidak sebanding risikonya sekarang.
- **Activity log tanpa project/entity id**: butuh perubahan skema `activity_log` + semua
  pemanggil `log(...)`; belum masuk task.
- **Rotasi kredensial** (JWT secret, password DB, password SMTP): tidak bisa dilakukan dari
  kode -- harus oleh pemilik infrastruktur. `backend/.env` dan `target/` tidak disertakan
  lagi di zip ini.

---

## 12. Frontend: tindak lanjut hardening backend (Pigay)

Menyesuaikan FE dengan perubahan Luhut (bagian 11) + task frontend dari Sudaryono.

### Keamanan & sesi
- **Evidence tidak lagi dibuka dengan `window.open(blobUrl)`.** Blob URL satu origin dengan
  aplikasi, jadi file HTML/SVG berskrip bisa membaca JWT di `localStorage`. Kini evidence
  tampil di modal (`testrun/components/EvidenceViewerModal.vue`) lewat `<img>`/`<video>`;
  tipe di luar whitelist (mis. file lama) tidak dirender, hanya ditawarkan sebagai download.
  `testRunService.fetchEvidenceObjectUrl()` diganti `fetchEvidenceBlob()`.
- Validasi upload di FE disamakan dengan backend (`testrun/utils/evidenceTypes.ts`): png, jpg,
  gif, webp, mp4, mov, webm. Input file memakai `accept` yang sama.
- **401 -> halaman Sign In** (dulu `/auth` = Sign Up). Kalau sesi sebelumnya ada, muncul pesan
  "Sesi kamu telah berakhir" (`/auth/signin?expired=true`). Login gagal (password salah) yang
  juga 401 tidak lagi dianggap sesi berakhir.
- **Change password menyimpan token baru** dari respons backend (token lama dicabut saat
  password berubah). Tanpa ini user langsung kena 401 di request berikutnya.
- **Route guard**: cek `exp` JWT (`tokenStorage.hasValidToken()`), user yang sudah login
  diarahkan keluar dari Sign Up/Sign In (kecuali ada query notifikasi), `/` memilih tujuan
  sesuai status login, dan ada halaman **404** (`shared/views/NotFoundView.vue`).
- **Guard role level project**: tiap route anak punya `meta.menu`; `ProjectLayoutView`
  melempar ke dashboard kalau menu itu tidak ada di `availableMenus` (mis. COLLABORATOR yang
  mengetik `/settings` langsung). Proteksi sebenarnya tetap di backend.

### Fitur
- **Kirim ulang verifikasi** (`ResendVerificationForm.vue`): muncul di notifikasi link
  verifikasi expired/invalid, di bawah pesan sukses register, dan saat login ditolak 403
  (belum verified). Cooldown 60 detik di FE, selaras backend.
- **Members**: undang user dengan email lengkap (pencarian hanya jalan setelah email utuh,
  cocok persis dengan backend), **ubah role** dan **hapus member** (OWNER-only, dengan dialog
  konfirmasi). OWNER terakhir tidak bisa diturunkan/dihapus (tombol nonaktif + 409 dari
  backend ditampilkan). Detail project dimuat ulang setelah perubahan (jumlah member, hak
  akses menu; menurunkan/menghapus diri sendiri langsung berefek).
- **Konflik penyimpanan hasil test (409)**: `UpdateTestResultRequest.version` dikirim dari
  `TestResultResponse.version` terakhir. Simpanan beruntun milik sendiri tidak saling
  bentrok (versi diambil dari balasan). Kalau tester lain menyimpan lebih dulu, user melihat
  pesan konflik, sisa simpanan result itu dilewati, dan data dimuat ulang dari server.

### Tipe baru/berubah (match 100% dengan DTO backend)
`TestResultResponse.version`, `UpdateTestResultRequest.version?`, `ResendVerificationRequest`,
`ChangePasswordResponse`, `UpdateProjectCollaboratorRequest`. Service baru:
`authService.resendVerification`, `projectCollaboratorService.updateCollaboratorRole` /
`removeCollaborator`; `searchUsersToAdd` kini memakai parameter `email` (bukan `keyword`).

### Tidak dikerjakan (alasan)
- **Peringatan "step test case berubah setelah dieksekusi"**: tidak diperlukan lagi -- backend
  kini menyimpan snapshot step saat test case di-mapping ke run, jadi run selalu menampilkan
  step yang dulu dieksekusi.
- **Pindah dari `localStorage` ke cookie httpOnly**: butuh perubahan backend (set cookie,
  CSRF, CORS credentials); belum dijadwalkan. Risiko XSS-ke-token sudah dikurangi lewat poin
  evidence di atas.
- **Halaman Register tetap menampilkan pesan 409** untuk email yang sudah terdaftar, menunggu
  keputusan soal respons register generik di backend.

### Cara verifikasi (belum diuji di browser)
Build penuh (`vue-tsc -b && vite build`) belum dijalankan karena `node_modules` tidak ada di
paket. Yang sudah diperiksa: sintaks seluruh blok script file yang diubah, type-check
`--strict` untuk `tokenStorage.ts` dan `evidenceTypes.ts`, uji logika keduanya di Node, dan
keseimbangan tag template. Jalankan `npm install && npm run build` lalu uji manual:
1. Login, tunggu/hapus token -> diarahkan ke Sign In dengan pesan sesi berakhir.
2. Ganti password di Settings -> tetap login; token lama (tab lain) kena 401.
3. Upload evidence png -> View tampil di modal; coba file `.html` diganti nama `.png` -> ditolak.
4. Dua browser, satu run: simpan bergantian -> pesan konflik muncul di yang kalah.
5. OWNER: ubah role, hapus member, coba hapus OWNER terakhir (tombol nonaktif).
6. COLLABORATOR mengetik `/projects/<id>/settings` -> dilempar ke dashboard.

---

## 13. Rebranding Fulmintis -- Tahap 1, backend (Luhut)

Nama produk di sisi backend (email) disesuaikan dengan requirement rebranding "Tester Gabut" ->
**Fulmintis**. Tidak ada perubahan skema DB, package, `spring.application.name`, maupun
kontrak API.

- `shared/email/EmailServiceImpl`:
  - Subjek: "Verifikasi Akun Fulmintis Kamu" dan "Reset Password Akun Fulmintis Kamu".
  - Isi email: header teks "Fulmintis" (sengaja teks, bukan gambar: banyak klien email
    memblokir SVG, dan gambar butuh URL publik absolut), judul, kalimat pembuka, dan catatan
    kaki menyebut Fulmintis.
  - Nama pengirim: kalau `MAIL_FROM` berisi `Nama <alamat>`, nama itu dipakai; kalau hanya
    alamat, nama produk dipakai otomatis -> inbox menampilkan "Fulmintis".
  - Nama produk dibaca dari `app.brand.name` (default di kode: `Fulmintis`), di-HTML-escape
    sebelum masuk ke email, dan kembali ke default kalau dikosongkan.
- `application-prod.properties`: contoh format `MAIL_FROM` dan `app.brand.name=${BRAND_NAME:Fulmintis}`.
- `EmailServiceImplTest`: 5 test (subjek, isi, nama pengirim otomatis, nama pengirim eksplisit,
  nama kosong/ter-escape).
- Tidak diubah (disengaja): nama DB `frontline`, `spring.application.name`, package
  `com.example.app`, key `access_token` di localStorage.
- Tidak perlu menambah properti di `application.properties` (file yang di-exclude dari zip).

Yang masih manual: set `MAIL_FROM` produksi ke format `Fulmintis <no-reply@domainanda.com>`
dan kirim satu email uji (register / forgot password) untuk memastikan SMTP Anda tidak
menimpa nama pengirim (Gmail SMTP tetap mengganti ALAMAT pengirim ke akun login, tetapi
nama tampil biasanya dipertahankan).

---

## 14. Rebranding Fulmintis -- Tahap 1, frontend (Pigay)

Nama produk di frontend diganti dari "Tester Gabut" menjadi **Fulmintis**. Logo BELUM
diganti: `BugIcon` sengaja dipertahankan sebagai placeholder sampai draft logo mantis
disetujui (Tahap 2).

- `shared/config/brand.ts` (baru): `BRAND_NAME = 'Fulmintis'`, satu-satunya sumber nama produk di FE.
- Teks nama di header/halaman auth kini `{{ BRAND_NAME }}` (bukan teks manual) di 5 tempat:
  `AuthView`, `ForgotPasswordView`, `ResetPasswordView` (`modules/auth/views/`),
  `DashboardView` (`modules/dashboard/views/`), `ProjectLayoutView` (`modules/project/views/`).
- **Judul tab "Fulmintis" di semua halaman**, termasuk 404:
  - `index.html`: `<title>Fulmintis</title>` (sebelumnya `frontend`) dan `<meta name="application-name">`.
    Ini yang tampil sebelum JavaScript dimuat.
  - `router/index.ts`: `router.afterEach` mengisi `document.title = BRAND_NAME`, supaya judul
    selalu mengikuti konstanta. Judul per halaman ("Dashboard · Fulmintis") bisa dibuat nanti
    dengan mengganti isi callback itu (di luar lingkup Tahap 1).
- Tidak diubah (disengaja): key `access_token` di `localStorage` (kalau diganti, semua user
  logout), nama file favicon, `BugIcon`/`NotFoundView` (menunggu logo Tahap 2).

Catatan: `<title>` di `index.html` tetap harus disamakan manual dengan `BRAND_NAME` kalau nama
berubah lagi (Vite tidak bisa membaca konstanta TypeScript di HTML tanpa mengubah
`vite.config.ts`, file yang di-exclude dari paket).

### Cara verifikasi (belum diuji di browser)
Sudah diperiksa: sintaks semua file yang diubah, import dan pemakaian `BRAND_NAME` di kelima
view, tidak ada lagi teks "Tester Gabut" di `src/` dan `index.html`, serta `<title>` dan
`application-name` sama dengan `BRAND_NAME`. Belum dijalankan: `npm run build` dan uji manual
-- buka Sign Up, Forgot Password, Reset Password, Dashboard, halaman project, dan URL ngawur
(404); pada semuanya tab harus "Fulmintis" dan header menampilkan "Fulmintis".

---

## 15. Dashboard project: data nyata (Luhut, backend)

Menggantikan placeholder di dashboard project dengan angka asli. Satu endpoint baru:
`GET /api/projects/{projectId}/dashboard` (modul `dashboard/`, kontrak lengkap di `README.md`).

- **Satu panggilan**, semua dihitung di database (`GROUP BY` / `row_number()`), tidak memuat test case
  ke memori. Otorisasi `ProjectAccessService.requireMember` (non-member 404).
- **Hanya test case `ACTIVE`** yang dihitung (total, scenario, priority); arsip di `archivedTestCases`.
  *(Health tidak lagi memakai aturan ini -- diubah di bagian 17.)*
- ~~**Health** = status eksekusi *terbaru* tiap test case aktif (Passed/Failed/Blocked/Pending/Not run).~~
  **[Digantikan di bagian 17: Health sekarang dihitung sama dengan Report.]**
  Hasil berstatus NEW diabaikan, jadi test case yang lulus di run lama lalu masuk run baru tetap
  terhitung status terakhir yang benar-benar dieksekusi. `notRun` = sisanya (kelima angka selalu
  berjumlah `totalTestCases`).
- **Active runs** = belum FINISHED **dan** belum 100% dieksekusi (angka asli, bukan persentase terbulat).
  Run yang sudah selesai dieksekusi tapi berisi FAILED tidak pernah FINISHED (FINISHED = semua PASSED);
  tanpa aturan ini ia akan menempel selamanya di daftar aktif. Status run tidak diubah.
- **Kolom baru `test_result.executed_at`** (`V16`): diisi hanya saat status berubah ke selain NEW
  (atau baris lama non-NEW yang belum punya), dikosongkan saat kembali ke NEW. Bukan `updated_at`
  (ikut berubah saat komentar/evidence diedit).
- Query health/timeline memakai `COALESCE(executed_at, updated_at, created_at)`, jadi data lama tetap
  terbaca di DB lokal walau backfill `V16` belum dijalankan manual.
- **Execution timeline**: 30 hari terakhir, per status (PASSED/FAILED/BLOCKED), hari kosong diisi 0,
  hari dihitung di zona waktu server. Eksekusi test case yang kemudian diarsipkan tetap dihitung
  (eksekusi adalah kejadian yang sudah terjadi).

### Batasan yang perlu diketahui
- **`executed_at` hanya menyimpan eksekusi TERAKHIR per baris**, bukan riwayat. Kalau hasil yang sama
  dieksekusi ulang (PASSED lalu FAILED), titik harinya pindah ke hari baru dan hari lama kehilangan
  hitungannya. Butuh riwayat akurat / audit = tabel riwayat tersendiri (belum dibuat).
- **Data lama**: backfill `V16` dari `updated_at` adalah perkiraan; titik historis lama bisa bergeser
  kalau komentar/evidence diedit setelah dieksekusi. Hanya mempengaruhi data sebelum fitur ini.
- Daftar run aktif memuat semua run yang belum FINISHED sebelum disaring progresnya; run berisi FAILED
  yang menumpuk (ratusan) akan sedikit memperlambat dashboard. Aman untuk skala sekarang.
- Tidak ada index baru: dengan `COALESCE(...)` pada filter tanggal, index `executed_at` tidak terpakai;
  query bertumpu pada index yang ada (`test_run.project_id`, `test_result.test_run_id/test_case_id`).

### Cara verifikasi
Sudah: kompilasi seluruh backend (0 error), 12 unit test baru (`DashboardCalculatorTest`,
`ProjectDashboardServiceImplTest` dengan repository palsu) bersama 11 test lama, dan teks query native
health & timeline dijalankan terhadap SQLite dengan data sintetis (data lama tanpa `executed_at`, run
baru berstatus NEW, test case arsip, project lain, waktu eksekusi sama). **Belum**: dijalankan terhadap
PostgreSQL sungguhan dan `mvn test` -- terutama `to_char(...)` pada query timeline (Postgres-spesifik,
di SQLite diganti `strftime`) dan validasi JPQL saat startup. Coba `GET .../dashboard` di DB berisi
data sebelum dipakai FE.

---

## 16. Dashboard project: data nyata (Pigay, frontend)

Menghubungkan dashboard project ke endpoint baru backend (bagian 15):
`GET /api/projects/{projectId}/dashboard`. Dashboard utama (daftar project / `ProjectCard`)
**tidak diubah**.

- **Satu panggilan** lewat `projectDashboardService` + composable `useProjectDashboardData`. Dimuat
  ulang tiap halaman dibuka (hasil eksekusi terbaru langsung terlihat setelah kembali dari Test Run) dan
  tiap project berganti. Respons yang datang terlambat dari permintaan lama diabaikan, dan data project
  sebelumnya dikosongkan dulu supaya angka project lain tidak sempat tampil.
- **Kartu statistik**: Total Test Cases (+ jumlah arsip bila ada), Positive vs Negative, Latest Run
  (judul, badge status, % eksekusi, tautan ke halaman eksekusi), Team Members (tetap dari detail project,
  tidak menunggu dashboard).
- **Current Project Health**: donut lima status (Passed/Failed/Blocked/Pending/Not run); total di tengah
  = Total Test Cases.
- **Test Cases by Priority**: empat batang, warna disamakan dengan badge prioritas di Test Repository.
- **Execution Timeline**: data asli 30 hari, tiga seri (Passed/Failed/Blocked), legenda dengan total,
  sumbu Y dinamis (sebelumnya `Y_MAX = 4` tetap), label tanggal dari data, tooltip saat hover. Tanpa data
  tampil satu garis dasar + keterangan "No executions in the last 30 days". Tetap SVG buatan sendiri
  (tanpa library chart baru).
- **Active Test Runs**: daftar (maks 5) dengan badge status, progress bar tersegmentasi, `executed/total`,
  hitungan passed/failed/blocked, dan tautan ke halaman eksekusi; "View all" ke daftar Test Runs.
- **State**: skeleton saat memuat; kartu error dengan tombol "Try again"; empty state per widget tetap ada.
- **Integrations** sekarang "Coming soon" tanpa tombol "Setup now" yang tidak berfungsi dan tanpa
  menyiratkan status "Not connected". Komentar lama yang menyebut modul backend "belum ada" dihapus.

### Detail teknis
- Tipe baru di `project/types/projectDashboard.types.ts`, cocok 100% dengan DTO backend
  (`ProjectDashboardResponse`, `DashboardRun`, `TimelinePoint`, dst).
- `project/utils/dashboardCharts.ts`: fungsi murni (segmen donut, batang prioritas, skala sumbu, titik
  timeline) agar mudah diuji. Warna status disamakan dengan modul Report/Test Run.
- `testrun/utils/runStatusDisplay.ts` (baru, diekspor dari `testrun/index.ts`): badge & label status run
  untuk dashboard. `TestRunsView` dan `ReportsView` masih memakai salinan lokalnya masing-masing (tidak
  disentuh agar perubahan ini tidak melebar); bisa dipindah ke util ini belakangan.

### Cara verifikasi (belum diuji di browser maupun terhadap backend sungguhan)
Sudah: sintaks semua file yang diubah/baru, type-check `--strict` untuk `dashboardCharts.ts` dan tipe
dashboard, 42 uji perilaku fungsi grafik (skala sumbu, segmen donut, batang, titik timeline, label),
keseimbangan tag & identifier template, dan render visual donut serta timeline dari keluaran fungsi yang
sama dengan komponen. **Belum**: `npm run build`, perilaku komponen Vue di browser (composable, hover
tooltip, skeleton), dan integrasi dengan backend -- query backend belum diuji terhadap PostgreSQL
(lihat bagian 15). Uji manual setelah keduanya terpasang:
1. Project baru tanpa data -> semua widget menampilkan empty state, tidak ada error.
2. Eksekusi beberapa test di sebuah run -> kembali ke dashboard: Health, Timeline, Active Runs, Latest Run
   ikut berubah.
3. Run yang semua test-nya sudah dieksekusi tapi berisi FAILED tidak muncul di Active Test Runs.
4. Arsipkan satu test case -> Total Test Cases turun, "N archived" muncul.
5. Ganti project lewat pemilih project -> tidak ada angka project lama yang sempat tampil.
6. Matikan backend / ubah ID project di URL -> kartu error + "Try again" (atau 404 untuk non-member).

---

## 17. Current Project Health = hitungan Report (Luhut, backend)

Menggantikan definisi Health di bagian 15. Health di dashboard kini **dihitung sama persis dengan
Report (tab Overview)**, dan hanya menghitung test case yang ada di test run.

### Aturan baru
- Yang dihitung: **setiap hasil eksekusi** (baris `test_result`) dari **semua test run** di project, per status.
- **`pending` = NEW + PENDING** digabung. Di dashboard dilabeli **"Not Run"**; nama field di API tetap
  `pending` supaya identik dengan `totalPending` di Report.
- Test case yang **belum dimasukkan ke test run tidak dihitung** (kategori "Not run" lama -- test case aktif
  yang belum pernah dieksekusi -- dihapus).
- Test case yang sama di 2 run dihitung **2x**; test case yang di-archive setelah masuk run **tetap terhitung**
  (sama seperti Report; test case memang dipertahankan di run-nya).
- Akibatnya jumlah keempat angka = `totalExecutions` di Report, **bukan lagi** `totalTestCases`; kedua angka
  itu bisa berbeda.

### Perubahan kontrak API (breaking untuk frontend)
`GET /api/projects/{projectId}/dashboard` -> `health`:
```json
// sebelum
"health": { "passed": 20, "failed": 5, "blocked": 2, "pending": 3, "notRun": 12 }
// sesudah
"health": { "passed": 20, "failed": 5, "blocked": 2, "pending": 9 }
```
**`notRun` dihapus dan arti `pending` berubah.** Frontend yang masih membaca `health.notRun` akan
mendapat `undefined` (donut rusak) -- **deploy backend dan frontend (tugas Pigay berikutnya) bersamaan.**

### Perubahan kode
- **`ResultStatusTotals`** (baru, `testrun/service/`): satu-satunya tempat aturan "hasil per status". Dipakai
  `ReportServiceImpl` **dan** `ProjectDashboardServiceImpl`, jadi kedua halaman tidak bisa berbeda lagi.
- `ReportServiceImpl`: dialihkan ke `ResultStatusTotals` **tanpa mengubah angka Report** (dibuktikan lewat
  perbandingan dengan perhitungan lama pada 20.000 kombinasi acak: 0 selisih).
- `HealthBreakdownDTO`: `notRun` dihapus. `DashboardCalculator.buildHealth(ResultStatusTotals)`.
- Query native health (`countLatestStatusPerActiveTestCase`, `row_number()`) **dihapus**. Health kini memakai
  `countGroupedByStatus` yang sama dengan Report, jadi satu query khusus PostgreSQL yang belum teruji di DB
  nyata hilang. `executed_at` (`V16`) tetap dipakai, tetapi hanya untuk Execution Timeline.
- Test: `ResultStatusTotalsTest` (3), `HealthReportParityTest` (3, memastikan angka Health = angka Report
  untuk data yang sama), dan `DashboardCalculatorTest` / `ProjectDashboardServiceImplTest` disesuaikan.

### Cara verifikasi
Sudah: kompilasi seluruh backend (0 error), 29 unit test lulus, dan perbandingan 20.000 kombinasi acak
antara perhitungan Report lama dan `ResultStatusTotals`. **Belum**: `mvn test` dan dijalankan terhadap
PostgreSQL sungguhan (query `countGroupedByStatus` sendiri tidak berubah -- sudah dipakai Report sebelumnya).
Uji manual: bandingkan donut Health dengan tab Overview Report pada project yang sama; keempat angkanya
harus identik.

---

## 18. Current Project Health = hitungan Report (Pigay, frontend)

Menyesuaikan donut Health dengan perubahan backend di bagian 17 dan permintaan label "Not Run".

- **Empat segmen**: Passed, Failed, Blocked, **Not Run**. Segmen "Not Run" memakai field API `pending`
  (= NEW + PENDING, identik dengan `totalPending` di Report) dan warna abu-abu muda; label "Pending" dan
  kategori "Not run" lama (test case belum pernah dieksekusi) dihapus. Nama field di API sengaja tidak
  diubah supaya identik dengan Report.
- **Angka di tengah donut = jumlah hasil eksekusi** (= `totalExecutions` di Report), label "results"
  (sebelumnya "test cases"). Angka ini bisa berbeda dari kartu Total Test Cases: test case yang sama di 2
  run dihitung 2x, dan test case yang belum masuk test run tidak dihitung.
- Subtitle: "Results across all test runs". Teks kosong: "No test run results yet" (project yang punya test
  case tetapi belum punya test run kini menampilkan keadaan kosong, bukan donut penuh).
- `HealthBreakdown` (types): `notRun` dihapus, cocok 100% dengan `HealthBreakdownDTO` backend.
- `dashboardCharts.ts`: warna `pending` abu-abu tua yang tidak terpakai dihapus; nilai hilang/NaN dari API
  dianggap 0, jadi respons backend versi lama (masih mengirim `notRun`, tanpa `pending` baru) tidak
  merusak donut dengan NaN -- namun angkanya tetap tidak sesuai sampai backend baru terpasang.
- Titik legenda diberi cincin tipis agar warna abu-abu muda "Not Run" tetap terlihat di latar putih.

### Cara verifikasi (belum diuji di browser maupun terhadap backend sungguhan)
Sudah: type-check `--strict`, 48 uji perilaku fungsi grafik (empat segmen, label & warna, tanpa NaN pada
respons lama), keseimbangan tag template, dan render visual donut empat segmen. **Belum**: `npm run build`
dan perilaku di browser. Uji manual (backend bagian 17 + frontend ini terpasang bersamaan):
1. Bandingkan donut Health dengan tab Overview Report pada project yang sama: Passed, Failed, Blocked,
   dan Not Run harus identik dengan Passed, Failed, Blocked, dan Pending di Report.
2. Project punya test case tetapi belum punya test run -> "No test run results yet".
3. Masukkan test case ke run -> Not Run bertambah; eksekusi -> Not Run turun, Passed/Failed/Blocked naik.
4. Tidak ada tulisan "Pending" di legenda donut.

---

## 19. Import test case dari Excel (Luhut, backend)

Fitur import di halaman Test Repository (requirement Sudaryono). Kontrak lengkap di `README.md`.

- **Endpoint** (`TestCaseImportController`): unduh template, dan import dengan pratinjau (`dryRun=true`,
  default) atau simpan (`dryRun=false`). OWNER maupun COLLABORATOR boleh (sama seperti membuat test case);
  non-member 404; folder harus milik project (404 kalau tidak).
- **Semua atau tidak sama sekali**: kalau ada satu baris tidak valid, tidak ada yang disimpan (400 dengan
  daftar error per baris). Semua error dikumpulkan sekaligus, bukan berhenti di yang pertama.
- **Auto mapping header** (`ImportColumn`): tanpa membedakan huruf besar/kecil, spasi, tanda hubung, tanda baca;
  sinonim umum (mis. "Judul", "Type", "Precondition", "Expected result"). Urutan kolom bebas, kolom tak dikenal
  diabaikan dan dilaporkan. Wajib: Title, Priority, Test type.
- **Nilai enum** tanpa membedakan huruf besar/kecil + sinonim (Automated/Otomatis, Positif/Negatif).
- **Test step & expected results** (`TestStepSplitter`): satu langkah per baris dalam sel, nomor bawaan user dibuang
  lalu dinomori ulang `1. ...\n2. ...` (format sama dengan `serializeSteps` di FE). Jumlah tidak sama -> peringatan
  + dilengkapi baris kosong bernomor supaya tetap sejajar (tanda PASSED/FAILED per langkah berbasis urutan).
- **Template** (`TestCaseImportTemplateBuilder`): header (kolom wajib berlatar kuning), baris contoh
  `[CONTOH] ...` yang **dilewati saat import** (template tanpa diubah aman, tidak menyisipkan data contoh),
  dropdown yang nilainya diambil dari enum sistem (selalu sinkron), dan sheet Petunjuk.
- **Keamanan**: hanya `.xlsx`; ukuran maks 5 MB dan isi harus berupa ZIP (bukan sekadar ekstensi); maks 500 baris;
  hanya sheet pertama; **formula tidak dihitung** (yang dibaca nilai tersimpan, sel error dianggap kosong);
  batas 20 MB per bagian setelah dibuka -- perlindungan zip-bomb bawaan POI hanya menolak rasio kompresi ekstrem
  (> 100x), sehingga file kecil yang mengembang ~50x lolos tanpa batas ini (dibuktikan lewat uji: file 25 MB
  terbuka penuh di memori tanpa batas, ditolak dengan batas). Rate limit 10/menit/IP. Activity log `IMPORT_TEST_CASES`.
- `GlobalExceptionHandler`: `InvalidImportFileException` -> 400 pesan; `TestCaseImportValidationException` -> 400 dengan
  body hasil lengkap. Pesan batas upload global diubah menjadi generik ("Ukuran file melebihi batas upload (50MB).").

### Keputusan yang diambil (default Sudaryono)
**ID tetap UUID.** Nomor "TC-001" di layar masih dihitung FE dari posisi baris, jadi menambah test case lewat import
dapat menggeser nomor tampilan test case lama. Nomor permanen per project (kolom baru) belum dikerjakan.

### Batasan
- Batas upload global Spring tetap 50 MB (`spring.servlet.multipart`, di file konfigurasi yang di-exclude); file
  > 5 MB baru ditolak setelah diterima server. Turunkan batas global bila perlu.
- `createdAt` tiap test case diisi saat disimpan; urutan di daftar mengikuti urutan baris file selama tidak ada
  dua baris yang disimpan di mikrodetik yang sama (praktis tidak terjadi).
- Properti opsional di `application.properties`: `# app.rate-limit.import-per-minute=10`.

### Cara verifikasi
Sudah: kompilasi seluruh backend (0 error, 222 class), 68 unit test lulus (38 baru: pemecah step, pemetaan header,
parser dengan file Excel sungguhan dibuat lewat POI, template, service dengan repository palsu, batas ukuran),
aturan rate limit import lewat harness (38 uji), dan template dibuka dengan LibreOffice (kedua sheet terbaca).
**Belum**: `mvn test`, dan dijalankan terhadap PostgreSQL / lewat HTTP sungguhan (upload multipart, header
unduhan). Dropdown di template dibuat lewat POI tetapi belum dilihat di Microsoft Excel (hanya LibreOffice).

---

## 20. Import test case dari Excel (Pigay, frontend)

Menghubungkan halaman Test Repository ke endpoint import backend (bagian 19). Kontrak API di `README.md`.

- **Tombol Import** di header folder (gaya sekunder, tepat di sebelah kiri "New Test Case"). Panel test case hanya
  dirender kalau sebuah folder sudah dipilih, jadi tombol otomatis tidak ada selama belum ada folder terpilih.
  Tampil untuk OWNER maupun COLLABORATOR (sama seperti membuat test case; backend penjaga akhir).
- **Modal tiga langkah** (`ImportTestCasesModal.vue`):
  1. **Upload file**: tombol *Download Template* (unduhan lewat blob karena endpoint butuh JWT), area klik atau
     seret file `.xlsx`, pemeriksaan cepat di browser (ekstensi `.xlsx`, tidak kosong, maks 5 MB -- backend tetap
     memeriksa ulang termasuk isi file).
  2. **Review** (`dryRun=true`, tidak menyimpan apa pun): ringkasan (Total rows / Valid / Errors / Warnings),
     daftar error per baris (nomor baris Excel, kolom, pesan) yang **memblokir** import, daftar peringatan yang
     tidak memblokir, pemetaan kolom Excel -> field sistem (+ kolom yang diabaikan), dan pratinjau baris valid.
  3. **Import** (`dryRun=false`): tombol "Import N test case" nonaktif kalau ada error atau tidak ada baris valid;
     saat berjalan menampilkan "Mengimpor...", semua tombol dan penutupan modal dikunci.
- **Notifikasi sukses**: modal tertutup, muncul toast `Import berhasil: N test case ditambahkan ke folder "X".`
  (N = `importedCount` dari backend, yaitu yang benar-benar tersimpan), daftar test case dimuat ulang sesuai filter
  aktif, dan jumlah per folder di sidebar dimuat ulang.
- **Penanganan gagal**: ada dua bentuk 400 dari backend -- body hasil lengkap (daftar error per baris, ditampilkan di
  langkah Review) dan `{ message }` (file tidak bisa diproses: bukan .xlsx, kolom wajib tidak ada, > 500 baris, dst,
  ditampilkan di langkah Upload). 429 (rate limit) punya pesan sendiri. Respons terlambat/ganda tidak bisa menyimpan dua kali:
  semua tombol terkunci selama proses.
- **Komponen notifikasi baru** (belum ada di proyek): `shared/composables/useToast.ts` + `shared/components/ToastContainer.vue`
  (aria-live, tutup otomatis 6 detik, maks 4 sekaligus). Dipasang SEKALI di `ProjectLayoutView` karena `App.vue` di luar
  lingkup perubahan; kalau suatu hari butuh toast di luar halaman project, pindahkan `<ToastContainer />` ke `App.vue`.
- **Tipe baru** `types/testCaseImport.types.ts` cocok 100% dgn DTO backend; `testCaseService` mendapat
  `downloadImportTemplate` dan `importTestCases` (FormData, `dryRun` selalu dikirim eksplisit, timeout 60 detik).

### Keputusan & batasan
- **Nomor "TC-001" tetap dihitung FE dari posisi baris** (ID sebenarnya UUID, default Sudaryono). Import dapat menggeser
  nomor tampilan test case lama; nomor permanen per project belum dikerjakan.
- File dibaca dua kali oleh backend (pratinjau lalu simpan) -- sengaja: simpan selalu memvalidasi ulang.
- Tombol "Pilih file lain" mengosongkan file (perbaiki di Excel lalu unggah ulang); modal tidak menyimpan file antar pembukaan.

### Cara verifikasi (belum diuji di browser)
Sudah: type-check `--strict` util & tipe; 25 uji util (validasi file, pesan sukses, penguraian respons gagal, label
kolom); 8 uji toast; kontrak dicocokkan **dengan JSON asli yang dihasilkan service backend Luhut** (pratinjau, simpan
sukses, penolakan 400): nama field cocok 100% dan util FE memperlakukan payload nyata itu dengan benar; sintaks
dan keseimbangan tag semua file yang berubah. **Belum**: `npm run build`, tampilan visual modal/toast di browser,
seret-lepas file, dan unggah sungguhan ke backend. Uji manual (backend bagian 19 + frontend ini terpasang):
1. Unduh template, isi 3 baris tanpa mengubah struktur -> Check file -> Import: toast "3 test case", daftar & sidebar bertambah.
2. Biarkan baris contoh `[CONTOH]` di template -> tidak ikut terimpor (muncul sebagai peringatan).
3. Isi Priority "Urgent" di satu baris -> Review menampilkan nomor baris Excel + pesan; tombol Import nonaktif; tidak ada yang tersimpan.
4. Ubah urutan kolom / tulis header "Judul", "Test-Type" -> pemetaan kolom tampil benar.
5. Test step 3 baris vs expected 2 baris -> peringatan; setelah import, halaman eksekusi test run menampilkan langkah sejajar.
6. Pilih file `.xls`/`.csv`/> 5 MB -> ditolak di langkah Upload; file .xlsx tanpa kolom Priority -> pesan kolom wajib dari backend.
7. Login sebagai COLLABORATOR -> tombol Import tersedia dan berfungsi.

---

## 21. Menu Automation: generate kode automation dengan AI -- Tahap 1 (Luhut, backend)

Semua yang TIDAK bergantung pada provider AI. Provider/model belum tersedia, jadi dipasang di Tahap 2 lewat satu adapter.
Kontrak lengkap di `README.md`.

### Yang dibangun
- **`shared/ai/`** (generik, tanpa kata "provider tertentu"): antarmuka `AiClient`, `AiRequest`/`AiResponse`, satu bentuk kegagalan
  `AiProviderException` (kuota habis, rate limit, tidak tersedia, timeout, ditolak), konfigurasi per tier (`AiProperties`),
  `AiGateway` (memilih adapter, menerapkan model/batas per tier), **circuit breaker** (setelah provider melapor kuota/saldo habis,
  panggilan ditahan beberapa menit; baris `[AI-ALERT]` di log ERROR sekali per jendela agar admin tahu), dan `AiConfigurationValidator`.
- **Validasi startup** (gagal start, bukan gagal diam-diam): provider kosong -> start normal, fitur nonaktif; `fake` di profil
  `prod` -> **GAGAL START**; provider tanpa adapter -> gagal; provider sungguhan tanpa baseUrl/apiKey/model/batas valid -> gagal.
- **`FakeAiClient`** (`modules/automation/ai/`, hanya ada bila `app.ai.provider=fake`): kode contoh untuk **10 kombinasi** valid x 2 pola,
  setiap berkas bertanda "FAKE AI OUTPUT", plus mode kegagalan (`app.ai.fake.mode`) dan jeda (`app.ai.fake.delay-millis`, bawaan 1500 ms).
- **`modules/automation/`**: setup per project (`automation_setup`, **unique `project_id` di database**), matriks framework x bahasa,
  job generate asinkron (`automation_generation`, snapshot setup, token & model tercatat), kuota harian per user per tier + batas global,
  satu job aktif per user (cek di kode + indeks unik parsial di `V17`), riwayat, detail, unduh zip, `usage`.
- **Job asinkron**: `POST` -> 202, thread pool terbatas (antrean penuh -> `SERVER_BUSY`), dijadwalkan SETELAH commit, panggilan AI di luar
  transaksi, job yang masih QUEUED/RUNNING saat startup ditandai gagal.
- **Prompt**: template terversi di `resources/automation/prompts/v1/` (`PROMPT_VERSION=automation-v1` tercatat di tiap job). Isi test case
  masuk sebagai DATA di blok `<task>` JSON; `</` di-escape sehingga teks test case tidak bisa menutup blok itu.
- **Validasi keluaran AI** (`AutomationOutputParser`): tahan JSON yang dibungkus blok kode/kalimat pengantar, tetapi KERAS terhadap isi --
  path berbahaya (`..`, absolut, `\`, `:`), ekstensi tak diizinkan (`.sh`, `.bat`, `.exe`, ...), duplikat, atau terlalu besar menolak SELURUH
  keluaran. Zip dibuat di server dan nama entrinya divalidasi ulang. Kode hasil AI **tidak pernah dijalankan** server.
- Menu `AUTOMATION` ditambahkan ke `ProjectMenuCode` (OWNER dan COLLABORATOR; backend tidak perlu perubahan menu lain).
  **FE: tambahkan `'AUTOMATION'` ke tipe `ProjectMenuCode`.**
- Rate limit generate (`app.rate-limit.automation-per-minute`, bawaan 6/menit/IP). Activity log `SAVE_AUTOMATION_SETUP`,
  `DELETE_AUTOMATION_SETUP`, `GENERATE_AUTOMATION`. Migrasi `V17`. Tambahan di `application-prod.properties`.

### Keputusan yang diambil (asumsi Sudaryono)
Tier mengikuti **user yang menekan Generate** (bukan pemilik project); `VIP_MONTHLY` dan `VIP_YEARLY` satu konfigurasi; tipe tak dikenal
dianggap FREE. Setup hanya OWNER yang boleh mengubah; semua member boleh membaca dan generate. Hari kuota = tanggal zona waktu server.
Job gagal karena provider/keluaran tidak valid **tidak** menghabiskan jatah harian.

### Batasan
- **Mutu kode hasil AI belum terbukti** -- template prompt baru bisa dikalibrasi dengan model sungguhan (Tahap 2). Yang terbukti hanya alurnya;
  kode contoh klien palsu bukan hasil AI.
- **Pemetaan error kuota provider sungguhan belum ada** (format respons tiap provider berbeda); hanya diuji dengan error buatan.
- Angka tier di `AiTierProperties` hanyalah placeholder dev/uji; angka produksi menunggu provider (model, batas token, harga).
- State job, circuit breaker, dan rate limit ada di memori **satu instance** backend (sama seperti penyimpanan file lokal). Dengan beberapa
  instance, recovery startup instance baru akan ikut menggagalkan job yang berjalan di instance lain.
- Indeks unik parsial "satu job aktif per user" hanya ada lewat `V17` (profil prod); di DB lokal (Hibernate) aturan itu ditegakkan oleh
  pengecekan di kode saja.
- Hasil generate disimpan sebagai teks JSON di database; riwayat tidak dipangkas (tidak ada kebijakan retensi).
- Isi test case dikirim ke pihak ketiga (provider AI) saat Tahap 2 aktif: UI perlu menampilkan pemberitahuan privasi (tugas Pigay).

### Cara verifikasi
Sudah: kompilasi seluruh backend (0 error, 287 class); **137 unit test lulus** (15 kelas, tanpa regresi), 69 di antaranya baru untuk
fitur ini: matriks, path, parser, prompt (termasuk injeksi `</task>`), klien palsu, gateway + circuit breaker + log, validator, **pemasangan di
konteks Spring sungguhan** (binding `app.ai.*`, klien palsu kondisional, gagal start di prod/tanpa adapter), setup (maks 1 per project,
OWNER-only), seluruh alur generate (kuota, batas per tier, satu job aktif, pesan umum, tidak menghabiskan jatah, snapshot, recovery,
dispatcher setelah commit, antrean penuh) dan **semua 10 kombinasi dari setup sampai zip**. **Uji mutasi**: 9 bug sengaja (jatah ikut
terpakai job gagal, path `..` lolos, fake lolos di prod, pesan membocorkan "kuota", COLLABORATOR bisa ubah setup, Cypress+Java diterima,
circuit breaker mati, job diproses dua kali, `</task>` lolos) -- seluruhnya tertangkap (satu test awalnya lemah dan diperkuat). Kode contoh
klien palsu untuk 10 kombinasi x 2 pola (110 berkas, termasuk judul berisi kutip/backslash/baris baru) diperiksa dengan tool asli: Python 22/22
`py_compile`, JavaScript 33/33 `node --check`, TypeScript 33/33 sintaks, Java 22/22 kompilasi penuh dengan stub API.
**Belum**: `mvn test`, dijalankan terhadap PostgreSQL / lewat HTTP sungguhan, dan **provider AI sungguhan**.
**[Koreksi, lihat bagian 23]**: verifikasi di atas TIDAK mencakup pendaftaran repository/entity pada konfigurasi dua datasource, sehingga
Tahap 1 pernah gagal start; sudah diperbaiki. Query turunan dan JPQL `failActiveJobs` kini juga divalidasi (tanpa database) oleh test di bagian 23.

### Tahap 2 (setelah provider tersedia)
Adapter `AiClient` memakai `RestClient` + pemetaan error (terutama `QUOTA_EXHAUSTED`), isi model/batas per tier, kalibrasi template prompt
dengan model nyata (bandingkan hasil FREE vs VIP), uji dengan API key sungguhan. Langkahnya ada di `README.md`.

---

## 22. Menu Automation: generate kode automation dengan AI (Pigay, frontend)

Menghubungkan UI ke backend Tahap 1 (bagian 21). Kontrak API di `README.md`. Provider AI belum ada, jadi seluruh alur diuji dengan backend
`app.ai.provider=fake`; tombol Generate menyesuaikan diri otomatis lewat `usage.aiEnabled`.

### Yang dibangun
- **Menu baru "Automation"** di sidebar project (setelah Reports), route `/projects/:id/automation` (`meta.menu='AUTOMATION'`, jadi guard menu di
  `ProjectLayoutView` ikut berlaku), ikon baru `CodeIcon`. `'AUTOMATION'` ditambahkan ke tipe `ProjectMenuCode`. Menu hanya tampil kalau backend
  mengirim kodenya di `availableMenus` (frontend baru + backend lama = menu tersembunyi, tidak error).
- **Tab Setup** (`AutomationSetupTab`): framework (3), bahasa (4), pola struktur (Page Object Model / Simple), dan struktur folder & aturan penamaan
  (opsional, maks 4000 karakter dgn penghitung). **Matriks framework x bahasa dibaca dari `GET /options`** -- bahasa yang tidak valid untuk framework
  terpilih dicoret, dinonaktifkan, dan memuat alasan di tooltip ("Cypress tidak mendukung Java."); ganti framework otomatis memindahkan bahasa
  ke yang valid pertama. Tidak ada aturan kompatibilitas yang ditulis ulang di frontend. Hanya **OWNER** yang bisa mengubah/menghapus; anggota lain
  melihat setup read-only. Hapus setup lewat dialog konfirmasi (riwayat tetap ada).
- **Tab Generate** (`AutomationGenerateTab`): ringkasan setup + tier + pemakaian harian, pemilih test case, pemberitahuan privasi, tombol Generate,
  progres, hasil, dan riwayat.
  - **Pemilih test case**: folder + pencarian + centang lintas folder, penghitung `n/maks` (maks dari `usage.maxTestCasesPerGeneration`, bergantung
    tier), "pilih semua yang tampil" tetap menghormati batas, urutan hasil mengikuti urutan klik. Hanya test case aktif.
  - **Pemberitahuan privasi** tampil tepat di atas tombol: isi test case dikirim ke layanan AI pihak ketiga.
  - **Alasan tombol nonaktif** selalu ditampilkan: setup belum diatur, **"Fitur generate belum diaktifkan."** (`aiEnabled=false`), job sebelumnya masih
    berjalan, belum memilih, melebihi batas, atau batas harian tercapai.
  - **Polling status** (`POST` 202 -> `GET /generations/{id}` tiap 2-5 detik): menampilkan status nyata (Dalam antrean / Sedang digenerate) + waktu
    berjalan, bukan persentase palsu. Toleran terhadap gangguan jaringan singkat (menyerah setelah 3 kegagalan berturut-turut), berhenti seketika pada
    403/404, menyerah setelah 10 menit dengan keterangan "cek riwayat". Pindah tab tidak menghentikan polling (`v-show`); pindah halaman menghentikannya,
    dan **saat kembali, job milik sendiri yang masih berjalan di riwayat dilanjutkan** (dikenali lewat email di token -- hanya untuk tampilan).
  - **Hasil**: pohon berkas (rekursif), penampil kode dengan tombol Copy (ada cadangan tanpa Clipboard API), catatan dari AI, dan **Download .zip**
    (lewat blob karena butuh JWT). Selalu ada label **"AI-generated, review sebelum dijalankan"**. Isi kode dirender sebagai teks, tidak pernah HTML.
  - **Riwayat** (maks 20, terbaru dulu): status, framework/bahasa, jumlah test case, pemohon, waktu; tombol Lihat utk yang berhasil.
- **Pesan error** (`utils/automationErrors.ts`): kegagalan sisi AI -- termasuk kuota/saldo habis, rate limit provider, timeout -- **selalu tampil sbg
  pesan umum yang SAMA** ("Layanan AI sedang tidak tersedia. Silakan coba lagi nanti."), yang ditulis sbg konstanta di frontend (bukan dibaca dari
  server) supaya tidak ada jalur yang bisa membocorkan kata kuota/billing/provider ke layar. Batas harian milik user (`AI_PLAN_LIMIT_REACHED`) berbeda:
  pesannya spesifik karena bisa ditindaklanjuti. Kasus lain (setup belum diatur, job sedang berjalan, validasi, rate limit umum) punya pesan sendiri.
- **Tidak ada teks di UI yang menyebut nama provider/model.**

### Perubahan di file bersama
`tokenStorage.getTokenSubject()` (email dari klaim `sub`, hanya tampilan); `testcase/index.ts` kini juga mengekspor helper tampilan
(`priorityLabel`, dst); `testrepository/index.ts` mengekspor `buildFolderTree`/`flattenTreeForDropdown`; `CodeIcon`; route + sidebar + tipe menu.

### Keputusan & batasan
- Pilihan test case tidak dipertahankan saat halaman dimuat ulang. Semua test case aktif dimuat sekali (sama seperti Test Repository); project dengan
  ribuan test case perlu pagination di iterasi berikutnya.
- Kode ditampilkan sebagai teks polos (tanpa syntax highlighting, agar tidak menambah dependency). Tidak ada tombol batalkan job (backend belum punya).
- Waktu berjalan pada job yang dilanjutkan dihitung dari `createdAt` server (tanpa zona waktu) dan dibatasi 10 menit agar selisih zona waktu server
  vs browser tidak menampilkan angka aneh.
- Status "Antre" job lama bisa terlihat sesaat setelah server restart sampai backend menandainya gagal saat startup.

### Cara verifikasi (belum diuji di browser maupun terhadap backend lewat HTTP)
Sudah: **kontrak dicocokkan dengan JSON asli yang dihasilkan service backend Luhut** (semua endpoint: opsi, setup, usage, buat, detail QUEUED/SUCCEEDED/FAILED,
riwayat, dan body error 503/429/409/400): 15 pasangan DTO-tipe cocok 100% (nama, jenis nilai, null); 24 uji util frontend terhadap payload nyata itu,
termasuk bahwa job FAILED karena kuota habis tidak pernah menampilkan kata kuota/provider; 97 uji util murni (matriks & form setup, pemetaan error,
pohon berkas, batas pemilihan, alasan tombol, polling dengan timer palsu: sukses, FAILED, timeout, error berselang, dibatalkan); type-check `--strict`
untuk semua TypeScript murni **dan untuk blok `<script setup>` ke-11 komponen** (dengan stub minimal Vue/axios; uji sensitivitas: tiga kesalahan yang
sengaja disisipkan tertangkap); sintaks seluruh file; keseimbangan tag & identifier template. **Belum**: `npm run build` / `vue-tsc`, perilaku dan tampilan
komponen di browser, dan unggah/unduh sungguhan ke backend. Uji manual (backend bagian 21 dgn `app.ai.provider=fake`, frontend ini):
1. Project baru: tab Setup terbuka; pilih Cypress -> Java dan Python dicoret; ganti dari Playwright+Java ke Cypress -> bahasa pindah ke JavaScript. Simpan -> toast.
2. Login sebagai COLLABORATOR: Setup read-only (tidak ada tombol simpan), Generate tetap bisa.
3. Pilih 6 test case sebagai Free (maks 5): kotak ke-6 nonaktif + keterangan batas; pilih 2 -> Generate -> progres (jeda fake 1,5 dtk) -> hasil + zip.
4. `app.ai.fake.mode=QUOTA_EXHAUSTED`: pesan umum tanpa kata kuota; sesudahnya Generate langsung ditolak dgn pesan yang sama; di log server ada `[AI-ALERT]`.
5. Hasil gagal lain: `INVALID_OUTPUT` -> "Hasil generate tidak dapat diproses..."; `WRAPPED_OUTPUT` -> tetap berhasil.
6. Provider kosong: tombol Generate nonaktif "Fitur generate belum diaktifkan."; tab Setup tetap bisa disimpan.
7. Mulai generate lalu pindah ke Dashboard dan kembali: progres dilanjutkan / hasil muncul di riwayat. Batas harian Free (3) -> pesan batas spesifik.
8. Download .zip: berisi `README-AI-GENERATED.txt` + berkas hasil; Copy menyalin isi berkas yang dipilih.

---

## 23. Perbaikan: aplikasi gagal start -- repository/entity automation belum terdaftar (Luhut, backend)

**Gejala**: `Parameter 1 of constructor in ...AutomationSetupServiceImpl required a bean of type '...AutomationSetupRepository' that could not be found.`

**Penyebab**: repository dan entity didaftarkan PER PAKET secara eksplisit di `PrimaryDataSourceConfig` (aplikasi memakai dua datasource:
`frontline` dan `master_data`). Paket `modules.automation.repository` dan `modules.automation.entity` (bagian 21) tidak ada di daftar itu, jadi
Spring tidak membuat bean repository-nya. Setelah paket repository ditambahkan, error berikutnya yang pasti muncul adalah
`Not a managed type: class ...AutomationSetup` (paket entity), jadi keduanya didaftarkan sekaligus.
Ini kelalaian saya: file konfigurasi itu sendiri berkomentar agar paket modul baru didaftarkan, dan verifikasi bagian 21 (kompilasi + unit test
dengan repository palsu) tidak pernah memakai konfigurasi datasource sehingga tidak bisa menangkapnya.

**Perbaikan**
- `PrimaryDataSourceConfig`: tambah `com.example.app.modules.automation.repository` ke `basePackages` `@EnableJpaRepositories`, dan
  `com.example.app.modules.automation.entity` ke daftar entity. Daftar entity dipindah menjadi konstanta `ENTITY_PACKAGES` (isi lama tidak berubah)
  supaya bisa dibaca test; `MasterDataSourceConfig` diperlakukan sama.

**Pengaman baru** (agar kesalahan jenis ini gagal di build, bukan saat start):
- `JpaRegistrationTest` (8 test): setiap `@Entity` dan setiap repository Spring Data di `com.example.app` terdaftar di TEPAT satu datasource;
  sebuah repository dan entity-nya ada di datasource yang sama; metadata Hibernate dan SEMUA repository dibangun tanpa koneksi database
  (menvalidasi nama query turunan, JPQL `@Query`, pemetaan entity); DDL dihasilkan dan **setiap kolom yang dirujuk indeks / unique / foreign key
  diperiksa ada di tabelnya**. Pemeriksaan DDL ini diperlukan karena Hibernate TIDAK memvalidasi nama kolom pada `@Index`/`@UniqueConstraint`
  (salah ketik lolos dan baru gagal di PostgreSQL saat tabel dibuat).
- `AutomationWiringTest` (1 test): merakit konteks Spring dengan `PrimaryDataSourceConfig` dan `MasterDataSourceConfig` yang ASLI (tanpa
  database) dan seluruh modul automation + lapisan AI; memastikan semua bean terakit seperti saat aplikasi start.

**Bukti**: error di atas direproduksi persis (menghapus paket repository dari konfigurasi -> `No qualifying bean of type
...AutomationSetupRepository`; menghapus paket entity -> `Not a managed type`), dan hilang setelah perbaikan. Uji mutasi: 7 bug sengaja
(paket repository/entity lupa didaftarkan, method query turunan merujuk properti yang tidak ada, JPQL salah properti, `@Index` dan
`@UniqueConstraint` salah kolom) seluruhnya tertangkap. Dua kelemahan test saya sendiri ditemukan lewat uji mutasi dan diperbaiki: (1) kolom
indeks yang salah awalnya lolos; (2) constraint unique yang ditulis Hibernate DI DALAM `create table` awalnya tidak terbaca pemeriksa DDL.
Seluruh suite: 146 test lulus (137 sebelumnya + 9 baru), tanpa regresi.
Efek samping bermanfaat: query turunan dan JPQL repository automation (termasuk `failActiveJobs`) serta seluruh entity di kedua datasource kini
tervalidasi tanpa database.

**Belum**: `mvn test` dan start terhadap PostgreSQL sungguhan; perbandingan entity vs migrasi Flyway (profil prod memakai `ddl-auto=validate`);
test perakitan hanya mencakup modul automation + dependensi minimalnya, bukan seluruh aplikasi (security, email, dll.).

**Checklist modul baru yang punya tabel** (juga ada di `README.md`): daftarkan paket repository di `@EnableJpaRepositories` dan paket entity di
`ENTITY_PACKAGES` pada datasource yang tepat; test di atas akan gagal dan menyebut paket mana yang terlewat.

---

## 24. Generate test case dengan AI + adapter Gemini (Luhut, backend)

Mengaktifkan backend utk tombol "Generate with AI" di Test Repository (requirement Sudaryono) dan memasang adapter provider sungguhan pertama (Gemini). Kontrak lengkap di `README.md`.

### Yang dibangun
- **Adapter Gemini** (`shared/ai/gemini/GeminiAiClient`, hanya aktif bila `app.ai.provider=gemini`): `generateContent` lewat REST, key HANYA di header `x-goog-api-key` (tidak pernah di URL/log,
  bentuk key tidak divalidasi sehingga key `AQ.` sah), mode JSON, skema keluaran opsional, thinking opsional, token thinking dihitung sbg output, dan SEMUA kegagalan diterjemahkan ke
  `AiProviderException` (429 kuota harian -> `QUOTA_EXHAUSTED` agar circuit breaker menahan; 429 per menit/tidak jelas -> `RATE_LIMITED`; 5xx -> `UNAVAILABLE`; 400/401/403/404 karena key/izin/model
  -> kesalahan konfigurasi: `[AI-CONFIG]` ERROR di log tetapi user hanya melihat pesan umum; keluaran diblokir/kosong -> `REJECTED`).
- **Konfigurasi tier & lingkungan:** `app.ai.billing-tier` (`free`|`paid`; **gagal start di profil prod bila provider=gemini tanpa `paid`**), `sandbox` pada `usage`, batas per tier utk fitur ini
  (`testcase-daily-limit` FREE 3 / VIP 15, `testcase-max-drafts` FREE 3 / VIP 15, token keluaran & timeout sendiri), `app.ai.testcase.*`, `app.ai.gemini.*`. Default `application-prod.properties`
  utk `billing-tier` SENGAJA `free` agar produksi tidak bisa start sebelum `paid` diisi eksplisit.
- **Generate -> review -> commit:** job asinkron (202 + polling) memakai thread pool yang sama dgn Automation; draft disimpan di `testcase_ai_generation` (migrasi `V18`), teks requirement
  TIDAK disimpan (diteruskan lewat memori); commit memvalidasi ulang tiap item, atomik (klaim `UPDATE ... WHERE committed=false` + simpan dalam satu transaksi), hanya sekali per generate;
  draft pribadi (404 utk user lain); `GET /pending` utk lanjut setelah reload; pembersihan draft kedaluwarsa saat startup.
- **Kuota terpisah dari Automation** + `AiGlobalBudget` baru: `global-daily-limit` kini menghitung total LINTAS fitur lewat `AiUsageCounter` yang didaftarkan tiap fitur (modul tidak saling mengimpor).
  `AutomationQuotaService` diubah memakainya. `AiRequest` mendapat `jsonOutput`/`responseSchema` (bentuk 5 argumen lama tetap ada), `AiGateway.generate(tier, AiCallSpec)` memungkinkan batas keluaran per fitur.
- **Parser draft** tahan JSON terbungkus dan **keluaran terpotong** (menyelamatkan draft yang lengkap, menandai `truncated`); enum memakai `TestCaseEnumValues` yang kini dipakai bersama import Excel.
  `FakeAiClient` mendapat mekanisme `FakeAiResponder` per fitur (+ mode `TRUNCATED_OUTPUT`) sehingga fitur ini bisa dijalankan dgn `provider=fake` tanpa key.
- Rate limit `app.rate-limit.testcase-ai-per-minute` (6), activity log `GENERATE_TEST_CASES_AI` / `COMMIT_AI_TEST_CASES`. Entity & repository ada di paket `modules.testcase` yang SUDAH terdaftar di konfigurasi datasource.

### Keputusan (Sudaryono)
Kuota terpisah dari Automation; FREE 3 generate/hari & 3 draft, VIP 15 & 15; requirement maks 6.000 karakter. Hasil generate dihitung ke kuota walau tidak disimpan; "Generate ulang" memakan jatah.

### Temuan saat verifikasi
**Bug nyata di adapter (sudah diperbaiki):** versi awal memakai `HttpURLConnection`, yang melempar `HttpRetryException` saat isi respons **401** dibaca pada POST bermode streaming -- sehingga 401 `UNAUTHENTICATED`
(error yang paling mungkin dgn key `AQ.`) tampil sbg "server tidak terjangkau" tanpa log `[AI-CONFIG]`. Test menangkapnya; kini memakai `java.net.http` dan kasus itu dipetakan benar.
Aturan "wajib `paid` di prod" awalnya terlalu luas (semua provider); dipersempit ke Gemini sesuai spesifikasi.

### Batasan / BELUM terverifikasi
- **Uji dengan key Gemini sungguhan BELUM dijalankan** (sandbox tanpa jaringan; key yang dikirim lewat chat dianggap bocor dan tidak dipakai). Semua perilaku adapter diuji terhadap server HTTP palsu yang MENIRU Gemini
  berdasarkan dokumentasi dan laporan pihak ketiga; format respons/error nyata (terutama isi 429 yang membedakan "PerDay" vs "PerMinute", dan error 401 utk key AQ) perlu dicocokkan dgn panggilan nyata.
- Parameter pengganti `responseSchema` (ditandai deprecated) dan parameter thinking BELUM terverifikasi -> bawaan mati. ID model final ditentukan dari daftar model utk key Anda.
- Mutu draft test case dari model nyata belum dinilai; prompt (`testcase-ai-v1`) perlu dikalibrasi.
- Jumlah test case yang sama pernah digenerate lagi tidak dicegah (hanya ditandai kembar vs test case di folder). Draft memuat teks hasil AI yang ditampilkan di UI: frontend wajib merendernya sbg teks.
- Satu instance backend (state job, circuit breaker, dan rate limit di memori); `V18` memuat indeks unik parsial "satu job aktif per user" yang hanya ada di prod.
- Menyimpan draft tidak menautkan test case ke job asalnya (tidak ada kolom `source_generation_id` di tabel test case).

### Cara verifikasi
Sudah: kompilasi seluruh backend; **seluruh suite lulus** (lihat angka di pesan serah-terima), termasuk test baru utk adapter terhadap server HTTP palsu (permintaan yang dikirim, setiap jenis error, timeout, key tidak bocor),
parser, prompt, alur lengkap generate/commit (kuota 3/15, terpisah dari Automation, anggaran global lintas fitur, commit atomik dgn 8 thread bersamaan, requirement tidak pernah tersimpan/ter-log), konfigurasi
(penjaga produksi, binding properti Spring termasuk angka opsional kosong), kontrak JSON, **perakitan konteks Spring dgn konfigurasi datasource asli**, dan `JpaRegistrationTest` (entity, query, DDL baru).
**Uji mutasi:** 15 bug sengaja (kuota memakai batas Automation, commit tanpa klaim atomik, requirement ter-log, key di URL, klasifikasi 429 tertukar, 401 tidak dikenali, penjaga prod mati, bawaan VIP 30,
anggaran global satu fitur, draft user lain terbaca, gagal memakan kuota, tidak dipangkas, tanda kembar terbalik, truncated tidak ditandai, off-by-one batas) -- seluruhnya tertangkap.
**Belum:** `mvn test`, PostgreSQL sungguhan, dan panggilan ke Gemini sungguhan.

---

## 25. Generate test case dengan AI (Pigay, frontend)

Menghubungkan tombol **Generate with AI** di Test Repository ke backend bagian 24 (kontrak di `README.md`). Hasil AI adalah DRAFT: user mereview/mengedit
dulu, baru menyimpannya. Backend (bagian 24, termasuk migrasi `V18`) harus sudah terpasang.

### Yang dibangun
- **Tombol "Generate with AI"** (`TestCasePanel`) kini aktif. Status fitur & jatah harian dibaca dari `GET /usage`; tombol nonaktif dgn **alasan yang terlihat**
  (teks di bawah toolbar, bukan hanya tooltip): "Fitur generate belum diaktifkan." (`aiEnabled=false`), "Batas generate harian tercapai (N per hari)...", atau
  "Fitur generate tidak dapat dimuat saat ini." (usage gagal dimuat -- lebih baik nonaktif + alasan daripada membuka modal yang pasti gagal).
- **Modal 3 langkah** (`AiGenerateTestCasesModal`):
  1. **Requirement**: textarea dgn penghitung (maks dari `usage.maxRequirementChars`), jumlah draft (1..`usage.maxDraftsPerGeneration`, bawaan = batas tier), opsi skenario
     negatif & batas, label jatah ("Hari ini: x dari y generate terpakai"), pemberitahuan privasi, dan **peringatan "Mode uji: jangan masukkan data asli"** bila `usage.sandbox`.
  2. **Proses**: status nyata (Dalam antrean / Sedang digenerate) + waktu berjalan, tanpa persentase palsu. Polling 2-5 dtk; berhenti seketika pada 401/403/404; menyerah setelah
     3 kegagalan berturut-turut atau 10 menit.
  3. **Review & Simpan**: kartu draft (`AiDraftCard`) yang **diciutkan secara bawaan** (judul, priority, type, scenario, jumlah langkah) dan dibuka utk diedit -- semua kolom form create
     (title dgn penghitung, priority, type, scenario toggle, description, objective, pre-conditions, tabel langkah + expected result). Centang per draft ("Pilih semua", indeterminate),
     "Buka semua / Ciutkan semua", buang draft, peringatan judul kembar dgn test case di folder (hanya peringatan), catatan "N dari M draft" bila hasil kurang dari yang diminta
     (mis. keluaran AI terpotong), validasi per kartu (aturan sama dgn form create), tombol "Simpan N test case ke folder X" (nonaktif + alasan bila belum valid), "Generate ulang"
     (konfirmasi: memakai 1 jatah & membuang editan; diblokir bila jatah habis), "Batal" (konfirmasi hanya bila ada editan: isi diubah atau draft dibuang; mengubah centang bukan editan).
- **Bertahan setelah ditutup/reload**: menutup jendela saat proses berjalan TIDAK membatalkannya (komponen tetap ter-mount); begitu selesai muncul toast & drafnya ada saat jendela dibuka
  lagi. Setelah reload, `GET /pending` menawarkan **"Lanjutkan review"** (tanpa memakai jatah baru). Generate ulang yang ditolak (mis. jatah habis) tidak menghilangkan draft sebelumnya:
  bisa dilanjutkan lewat tawaran yang sama.
- **Simpan**: hanya draft tercentang, urutan tampil terjaga; langkah diserialisasi dgn `serializeSteps` (teks bernomor yang sama dgn form create). Klik ganda hanya mengirim SATU permintaan.
  Setelah berhasil: modal tertutup, toast "N test case ditambahkan ke folder X.", daftar & jumlah di sidebar dimuat ulang (event `test-cases-imported` dipakai ulang), requirement dikosongkan.
- **Error dari backend**: `400 INVALID_DRAFTS` -> pesan per kolom menempel ke kartu yang benar (`index` = posisi pada daftar yang DIKIRIM, bukan daftar tampil), kartu dibuka otomatis, tidak ada yang
  tersimpan; `409 GENERATION_ALREADY_COMMITTED / DRAFT_EXPIRED / GENERATION_NOT_READY` dan `404` -> kembali ke langkah awal dgn penjelasan (requirement tetap); gangguan sementara (5xx/jaringan) ->
  draft DAN editan utuh, boleh coba lagi. Kegagalan sisi AI (termasuk kuota provider habis) **selalu pesan umum yang sama**; batas harian user (`AI_PLAN_LIMIT_REACHED`) pesan spesifik dari server.
- **Tidak ada angka batas di frontend** (3/15 generate, 3/15 draft, 6.000 karakter): semuanya dari `usage`, jadi mengubah batas tier di backend tidak membutuhkan perubahan di sini.

### Perubahan di berkas bersama (refaktor, perilaku identik)
Pemetaan error AI dan mesin polling dipindah dari modul automation ke `shared/utils/aiErrors.ts` dan `shared/utils/aiPolling.ts` (generik: tipe hasil ditentukan fitur pemanggil) agar modul
tidak saling mengimpor; indikator progres jadi `shared/components/AiJobProgress.vue`. Berkas lama di `modules/automation/utils` kini hanya me-re-export (nama lama dipertahankan, komponen & test
automation tidak berubah) dan `GenerationProgress.vue` menjadi pembungkus tipis. `describeAiError` mengenali kode error test case. `aiPolling` mendapat `isFatalPollStatus`.

### Struktur baru (`modules/testcase/`)
`types/testCaseAi.types.ts` (cocok dgn DTO backend), `services/testCaseAi.service.ts`, `utils/testCaseAiGate.ts` (gerbang tombol/input, teks status), `utils/testCaseAiDraft.ts` (draft yang bisa diedit,
validasi, ke payload commit, deteksi editan, pemetaan error per item), `composables/useTestCaseAiGeneration.ts` (seluruh alur; service/timer/callback diinjeksi), `components/AiDraftCard.vue`,
`components/AiGenerateTestCasesModal.vue` (hanya template di atas composable).

### Keputusan & batasan
- Jumlah draft bawaan = batas tier (sama dgn bawaan backend bila tidak diisi): FREE 3, VIP 15. (Versi awal memakai 1; ditemukan saat uji dan diperbaiki.)
- Membatalkan review membuang EDITAN, bukan draft aslinya: draft asli dari AI masih bisa dibuka lagi lewat "Lanjutkan review" selama belum disimpan/kedaluwarsa (7 hari di backend).
- Hanya satu proses aktif per user (aturan backend): bila user memulai lagi saat proses lama masih berjalan, muncul pesan + petunjuk untuk menunggu lalu membuka kembali jendela.
- Esc tidak menutup modal (sama dgn modal lain di proyek). Teks hasil AI dirender sbg teks (Vue mengescape), tidak pernah HTML.
- Perpindahan folder tidak mereset modal; draft yang sudah ada tetap disimpan ke folder tempat draft itu dibuat (atau ke folder yang sedang dibuka bila folder asalnya sudah dihapus).

### Cara verifikasi
Sudah (semua di sandbox tanpa browser):
- **Kontrak**: kode backend Luhut dijalankan utk mengeluarkan **27 payload asli** (usage FREE/VIP/nonaktif/sandbox, 202, detail QUEUED/SUCCEEDED/terpotong/FAILED/committed/folder-dihapus, pending, hasil commit,
  dan 12 body error). Tipe FE dicocokkan DUA ARAH (nama field & tipe nilai, termasuk field backend yang tidak ada di tipe FE): **58 uji**, plus util FE dijalankan terhadap payload yang sama.
- **Orkestrasi** (composable) dgn service palsu, jam virtual, dan payload asli: **104 uji** (alur normal, gerbang, setiap penolakan saat kirim, polling bermasalah, tutup saat proses, resume, simpan sukses/
  INVALID_DRAFTS/409/404/5xx/jaringan, klik ganda, generate ulang, hasil proses lama yang selesai belakangan, pindah project, dispose). **Util murni**: 73 uji. Test automation lama tetap **97 + 24** lulus.
- **Type-check strict** blok script 15 komponen dan semua berkas `.ts` baru; **pemeriksa template** (identifier, keseimbangan tag, kecocokan prop/event antar komponen) bersih dan terbukti menangkap 6 kesalahan yang
  sengaja disisipkan.
- **Uji mutasi**: ~20 bug sengaja (simpan mengirim semua draft, klik ganda tak dijaga, index error dipetakan ke daftar tampil, pesan AI bocor, tutup membatalkan proses, generate ulang tanpa jatah, kode "draft hilang"
  tak dikenali, konfirmasi tutup dilewati, requirement tak dikosongkan, pending tak dimuat, toast saat terbuka, timer tak dihentikan, 404 dicoba ulang, draft tak valid boleh disimpan, title tak dipangkas, dst) --
  semuanya tertangkap. Dua celah cakupan ditemukan lewat mutasi dan ditutup (folder dihapus `folderName: null`; pengosongan requirement setelah simpan); satu mutan terbukti setara (penjaga proses basi dua lapis).
Belum: `npm run build` / `vue-tsc`, uji di browser, dan Vue sungguhan (paket `vue` di sandbox hanya stub; composable diuji dgn shim `ref`/`computed`, template hanya diperiksa lewat skrip). Alur ke Gemini sungguhan
belum teruji (urusan backend, bagian 24).

---

## 26. Perbaikan: generate automation gagal `AI_INVALID_OUTPUT` karena berkas pendamping (Luhut, backend)

**Gejala:** job `FAILED` dgn `AI_INVALID_OUTPUT` (pesan umum ke user), padahal Gemini sudah membalas. Log server:
`menghasilkan keluaran AI yang tidak valid: path ditolak '.gitignore': berkas tanpa ekstensi yang dikenal`.

**Penyebab:** AI (Gemini) menambahkan `.gitignore` di samping page object & test. Parser memperlakukan SETIAP path yang jenisnya tidak didukung sbg alasan menolak SELURUH keluaran,
padahal berkas pendamping seperti itu tidak berbahaya. (Alasan sebenarnya hanya ada di log WARN; user selalu melihat pesan umum.)

**Perbaikan:** `GeneratedPaths.classify()` membedakan tiga hasil:
- `OK` -- dipakai.
- `SKIPPABLE` -- aman tetapi jenisnya tidak didukung: dotfile (`.gitignore`, `.env`, `.env.example`), tanpa ekstensi (`Dockerfile`, `Makefile`), ekstensi tak dikenal. **Dilewati**, bukan menggagalkan hasil;
  dan **dicatat di `notes`** hasil generate ("Berkas yang tidak disertakan (jenis tidak didukung atau ganda): ...", maks 10 nama + hitungan sisanya; catatan AI dipotong dulu agar keterangan ini selalu muat dalam 2000 karakter),
  serta di log INFO (`output.skippedPaths`). Path ganda (tanpa membedakan huruf besar/kecil) juga dilewati: yang pertama dipertahankan.
- `UNSAFE` -- seluruh keluaran TETAP ditolak: path traversal/absolut/karakter terlarang, terlalu dalam/panjang, dan **berkas yang bisa dieksekusi** (`.sh`, `.bat`, `.cmd`, `.ps1`, `.exe`, `.dll`, `.jar`, dst).
  Ini disengaja: skrip di keluaran AI bisa menandakan prompt injection dari isi test case, jadi tidak dilewati diam-diam.
Bila SEMUA berkas dilewati, keluaran tetap ditolak ("tidak ada berkas yang dapat dipakai"). Batas jumlah (30) dicek pada keluaran mentah; batas ukuran berlaku pada berkas yang dipakai.
`ParsedOutput` mendapat `skippedPaths` (konstruktor 2 argumen lama tetap ada). Nama berkas yang dilewati disanitasi (karakter kontrol -> `?`) sebelum masuk catatan.
`FakeAiClient` mendapat mode `EXTRA_FILES` (`app.ai.fake.mode=EXTRA_FILES`) yang menambahkan `.gitignore` dan `Dockerfile` -- utk mereproduksi kasus ini di dev.

**Tidak berubah:** `GeneratedPaths.problemWith()` dan `AutomationZipBuilder` tetap ketat (pertahanan berlapis); kontrak API; frontend (catatan hasil sudah ditampilkan di tab hasil).

**Belum dikerjakan (rekomendasi, bukan penyebab kegagalan ini):** mode JSON utk Automation (`jsonOutput=true`, seperti generate test case) agar format keluaran Gemini lebih konsisten, dan `finishReason` di log
utk membedakan keluaran terpotong dari format salah. Template prompt `automation-v1` tidak diubah (mengubahnya berarti menaikkan versi).

**Verifikasi:** test baru utk klasifikasi path, kasus `.gitignore` nyata, daftar+hitungan berkas dilewati, batas catatan, semua-dilewati, executable/traversal di samping berkas jinak, sanitasi nama, duplikat, batas jumlah mentah, dan
job end-to-end (`EXTRA_FILES` -> SUCCEEDED tanpa `.gitignore`/`Dockerfile`, catatan memuat keduanya). 9 bug sengaja pada logika baru -- seluruhnya tertangkap.


---

## 27. Fitur Admin: pendaftaran admin pertama, undangan admin, pemisahan akses (Luhut, backend)

**Keputusan produk:** admin pertama bebas mendaftar; admin berikutnya hanya bisa dibuat admin yang sudah login (undangan email). Admin tidak boleh memakai API/dashboard user, dan sebaliknya.

**Tipe user baru `ADMIN`** (master_data): `UserTypeCode.ADMIN` + `UserTypeSeeder` (label "Admin", urutan 4; idempotent, tanpa migrasi SQL). `GET /api/user-types` TIDAK lagi memuat ADMIN.

**Pemisahan akses (`shared/security`, `SecurityConfig`):**
- `Roles` memetakan `user_type` -> `ROLE_ADMIN` / `ROLE_USER`. `JwtAuthenticationFilter` mengisi authorities dari **user_type di DB pada setiap request** (filter ini memang sudah memuat user); claim `role` di JWT hanya info untuk FE dan TIDAK dipercaya untuk otorisasi. Token lama tanpa claim dianggap USER.
- `/api/admin/**` -> hanya `ROLE_ADMIN`; `/api/auth/profile` & `/api/auth/change-password` -> semua yang login; **semua endpoint lain -> hanya `ROLE_USER`** (admin dapat 403 di API project/test case/AI/dst). `JwtAccessDeniedHandler` membalas 403 JSON dgn format yang sama dgn 401/handler lain.
- `RateLimitFilter`: aturan baru utk login admin, daftar admin (default 5/menit, `app.rate-limit.admin-register-per-minute`), terima undangan, undang admin, dan dua GET publik admin.

**Auth user (`modules/auth`):** `/api/auth/login` menolak akun ADMIN dgn 401 generik yang SAMA dgn password salah (tidak membocorkan akun admin). `LoginResponseDTO` mendapat `role` ("USER"|"ADMIN"), juga di respons change-password. Link verifikasi email admin -> `/admin/signin`; link reset password admin -> `/admin/reset-password`. `/api/auth/register` tetap selalu FREE.

**Modul baru `modules/admin`:**
- `GET /api/admin/auth/registration-status` -> `{open, bootstrapCodeRequired}` (open = belum ada user ADMIN).
- `POST /api/admin/auth/register` -- hanya saat belum ada admin, selain itu 403 "Pendaftaran admin sudah ditutup". Cek-lalu-simpan diserialkan dgn `pg_advisory_xact_lock` (aman utk request paralel dan banyak instance). Bila `app.admin.bootstrap-code` diisi, `bootstrapCode` wajib benar (perbandingan waktu-konstan); kosong = bebas.
- `POST /api/admin/auth/login` -- non-admin = 401 generik yang sama dgn password salah.
- `POST /api/admin/admins` (ROLE_ADMIN) -- undang admin: akun ADMIN belum aktif dgn password acak yang tak diketahui siapa pun + token undangan 24 jam (kolom baru, bukan kolom reset/verifikasi). Email milik user biasa/admin aktif -> 409 (tidak ada "promosi" akun); undangan yang belum diterima dikirim ulang dgn token baru.
- `GET /api/admin/auth/invitation/validate`, `POST /api/admin/auth/accept-invitation` -- penerima membuat password; akun otomatis terverifikasi; token sekali pakai.
- `GET /api/admin/me`. `AdminBootstrapWarning`: WARN saat start di profil `prod` bila belum ada admin dan bootstrap-code kosong (tidak pernah menggagalkan startup).
- `User` mendapat `adminInvitationToken` + `adminInvitationExpiresAt` -> **migrasi `V19__add_admin_invitation_columns_to_users.sql`** (prod/Flyway; lokal diurus ddl-auto=update).
- `EmailService.sendAdminInvitationEmail(...)`; nama & pengundang di-escape HTML.
- Pencarian/penambahan team member project mengecualikan akun ADMIN (`findByEmailAndVerifiedTrueAndUserTypeNot`). `AiTier.fromUserType(ADMIN)` = FREE secara eksplisit (admin sudah 403 di API AI; ini lapis kedua).

**Konfigurasi baru -- semua punya default di kode, jadi application*.properties TIDAK wajib diubah:**
```
app.admin.bootstrap-code=${ADMIN_BOOTSTRAP_CODE:}            # kosong = admin pertama bebas; isi di production
app.security.admin-invitation-expiry-minutes=1440            # masa berlaku undangan (menit)
app.rate-limit.admin-register-per-minute=5
```
Rekomendasi: di production isi `ADMIN_BOOTSTRAP_CODE` sampai admin pertama terbentuk, lalu kosongkan.

**Batasan yang perlu diketahui:**
- Jika admin pertama salah ketik email dan tidak bisa verifikasi, pendaftaran tetap tertutup (akun admin tak terverifikasi tetap dihitung). Pemulihan: hapus barisnya di tabel `users` (operasional/DB).
- Belum ada daftar/nonaktifkan/hapus admin dan belum ada pergantian peran.
- Dua undangan bersamaan ke email BARU yang sama bisa menghasilkan satu 500 (unique email); sangat jarang dan aman (tidak ada data ganda).
- `lockAdvisory` memakai query native PostgreSQL (`select 1 from (select pg_advisory_xact_lock(:key)) as lock_row`) -- belum dijalankan terhadap PostgreSQL sungguhan (lihat Verifikasi).

**Test baru:** `AdminAuthServiceTest`, `AdminManagementServiceTest`, `AdminDtoValidationTest`, `AuthAdminSeparationTest`, `JwtAuthenticationFilterRoleTest`, `RolesAndJwtRoleTest`, `RateLimitFilterAdminTest`, `UserTypeAdminTest`, `AiTierAdminTest`, `EmailServiceAdminInvitationTest` (+ pembantu `AdminTestSupport`).

**Verifikasi:** seluruh 320 berkas Java lolos pemeriksaan sintaks (parser javac) dan semua `import com.example.app...` baru ter-resolve. **`mvn test` TIDAK dapat dijalankan di lingkungan ini (Maven Central tidak terjangkau)**, jadi kode & test baru belum dikompilasi/dijalankan dgn Spring/Lombok/JUnit; jalankan `mvn test` sebelum merge. Aturan matcher `SecurityConfig` belum diuji end-to-end; cek manual di bawah.

**Cek manual (setelah backend jalan):**
1. `GET /api/admin/auth/registration-status` -> `open:true`. Daftar admin pertama, verifikasi email, login via `/api/admin/auth/login` -> `role:"ADMIN"`.
2. Daftar lagi -> 403 ditutup; `registration-status` -> `open:false`.
3. Dengan token admin: `GET /api/projects` -> **403**; `GET /api/admin/me` -> 200. Dengan token user: `GET /api/admin/me` -> **403**.
4. `POST /api/auth/login` dgn akun admin -> 401 "Email atau password salah".
5. Admin `POST /api/admin/admins` -> email undangan; buka link, buat password, login admin kedua.

---

## 28. Fitur Admin: halaman admin, pemisahan area user/admin (Pigay, frontend)

Menyesuaikan dengan backend bagian 27. Admin punya area sendiri (`/admin/...`); admin tidak bisa membuka dashboard/halaman user, dan user biasa tidak bisa membuka area admin.

**Halaman baru (`modules/admin`):**
- `/admin/signup` -- pendaftaran ADMIN PERTAMA. Saat dibuka memanggil `GET /api/admin/auth/registration-status`: `open=false` -> layar "Pendaftaran admin ditutup" (admin berikutnya hanya lewat undangan); field "Kode pendaftaran" hanya muncul bila `bootstrapCodeRequired`. Bila submit dibalas 403, FE menanyakan status ke backend (bukan menebak dari teks pesan): ditutup -> layar ditutup, kode salah -> pesan di form.
- `/admin/signin` -- login admin (`POST /api/admin/auth/login`). Menampilkan notifikasi `?verified=` / `?reset=` / `?accepted=` / `?expired=`; link "Daftarkan admin pertama" hanya tampil selama pendaftaran terbuka; "Forgot Password?" memakai halaman lupa password yang sudah ada (link di email admin mengarah ke `/admin/reset-password`).
- `/admin/accept-invitation?token=` -- penerima undangan memvalidasi link lalu membuat password (pola sama dengan reset password); sukses -> `/admin/signin?accepted=true`.
- `/admin/reset-password` -- memakai ulang `ResetPasswordView` (route `meta.area = 'admin'` -> setelah sukses ke `/admin/signin`).
- Area admin (`AdminLayoutView` = header + sidebar + `<RouterView/>`; `meta: { requiresAuth: true, role: 'ADMIN' }` diwarisi semua anak): `/admin/dashboard` (kartu "Tambah Admin"), `/admin/admins/new` (form undang admin: nama + email; 409 ditampilkan inline), serta empat menu sidebar **User, Log user, Payment, AI token used** -> `/admin/users`, `/admin/user-logs`, `/admin/payments`, `/admin/ai-token-usage`, semuanya `ComingSoonView` ("Coming soon", judul dari `meta.title`). Tidak ada panggilan API untuk menu itu.
- Komponen: `AdminAuthShell`, `AdminPasswordField` (input password + show/hide + checklist), `AdminSidebar`. Ikon baru `CreditCardIcon`.

**Pemisahan akses (UX; proteksi sebenarnya di backend):**
- `router/guards.ts` (fungsi murni, diuji tanpa browser): belum login -> sign in area yang sesuai (`expired=true` bila token habis); peran tidak cocok -> dashboard perannya sendiri (admin ke `/dashboard`/halaman project -> `/admin/dashboard`; user ke `/admin/*` -> `/dashboard`); sudah login di halaman sign in/up -> dashboard perannya (kecuali ada query notifikasi). Route butuh-login tanpa `meta.role` = area USER, jadi route lama tidak perlu diubah.
- `tokenStorage.getRole()` membaca klaim `role` JWT (token lama tanpa klaim = USER; token rusak = null) -- hanya untuk UX. `LoginResponse.role` ditambahkan (match `LoginResponseDTO.role`).
- `LoginForm` (user) menolak respons ber-role ADMIN tanpa menyimpan token (lapis kedua; backend sudah 401). `AdminSigninView` menolak respons non-ADMIN.
- `apiClient`: respons 401 sekarang mengarahkan ke `/admin/signin` bila sesi yang berakhir adalah sesi admin (`/auth/signin` bila bukan); pengecualian anti-loop mencakup halaman publik admin (`shared/services/authPaths.ts`). Login admin (`/admin/auth/login`) ikut dikecualikan karena URL-nya memuat `/auth/login`.
- **Penyimpangan dari rencana:** interceptor TIDAK mengarahkan otomatis pada respons 403. Backend memakai 403 untuk banyak hal yang sah di dalam halaman (mis. bukan OWNER project, akun belum terverifikasi), jadi redirect otomatis akan merusaknya. Pemisahan peran dijaga oleh route guard.

**Kontrak (`modules/admin/types/admin-auth.types.ts`) match 100% dengan DTO backend:** `AdminRegisterRequest/Response`, `AdminRegistrationStatus`, `AdminInviteRequest/Response`, `AdminInvitationValidation`, `AdminAcceptInvitationRequest`, `AdminProfile`.

**Test baru** (`frontend/tests/`, Node test runner bawaan, tanpa dependensi baru): `npm test` (= `node --experimental-strip-types --test "tests/*.test.ts"`, butuh Node >= 22.6). 30 test: guard (semua kombinasi tanpa token/kedaluwarsa/USER/ADMIN x halaman user/admin/entry/publik/notifikasi), `tokenStorage` (role/legacy/rusak + perilaku lama), `authPaths`, `adminErrors`, dan tabel route (nama tujuan redirect ada, tidak ada nama ganda, route publik vs terproteksi, empat judul menu). `package.json` hanya mendapat satu baris `scripts.test` (package-lock tidak berubah).

**Verifikasi:** 30 test lulus; 9 mutasi sengaja pada guard/tokenStorage/authPaths/adminErrors seluruhnya tertangkap. Script semua `.vue` yang baru/diubah lolos type-check TypeScript strict (dengan shim vue/vue-router/axios) dan template-nya lolos pemeriksa identifier (semua variabel/komponen yang dipakai template terdefinisi, tidak ada impor tak terpakai; pemeriksa sendiri diuji mutasi).
**BELUM diverifikasi:** `npm run build` (`vue-tsc -b` + Vite) dan tampilan di browser -- registry npm menolak paket yang dibutuhkan di lingkungan ini, jadi dependensi tidak bisa dipasang. Jalankan `npm run build` dan `npm test`, lalu cek manual: (1) `/admin/signup` saat belum ada admin, (2) setelah ada admin -> "ditutup", (3) login admin -> `/admin/dashboard`, buka `/dashboard` -> kembali ke `/admin/dashboard`, (4) login user, buka `/admin/dashboard` -> kembali ke `/dashboard`, (5) klik tiap menu sidebar -> "Coming soon", (6) undang admin -> buka link di email -> buat password -> sign in.

---

## 29. Fitur Admin: ubah password & lupa/reset password admin (Luhut, backend)

**Tidak ada endpoint, DTO, migrasi, atau konfigurasi baru.** Sesuai keputusan "flow sama dengan user", admin memakai endpoint `auth` yang sudah ada; yang berubah hanya dua pengaman kecil dan test yang mengunci perilakunya.

**Yang sudah berlaku sejak bagian 27 (sekarang dikunci test):**
- `POST /api/auth/change-password` hanya mensyaratkan login (semua peran), jadi admin bisa memakainya. Token baru yang dibalas tetap membawa klaim `role=ADMIN`, dan token lama otomatis tidak berlaku (klaim `pv`). Password saat ini salah -> 400 `IncorrectCurrentPasswordException` (bukan 401, jadi FE tidak ikut logout).
- `POST /api/auth/forgot-password` untuk akun admin mengirim link `{frontend}/admin/reset-password?token=...`; `POST /api/auth/reset-password` generik, token sekali pakai, sesi lama dicabut.

**Perubahan:**
- `RateLimitFilter`: aturan baru `change-password` (POST `/api/auth/change-password`, per IP, memakai `app.rate-limit.email-per-minute`, default 5/menit). Berlaku untuk user biasa dan admin; mencegah penebakan "password saat ini" dengan token curian. Tidak ada properti baru.
- `AuthServiceImpl.forgotPassword`: akun **ADMIN yang belum terverifikasi** (undangan belum diterima) diabaikan diam-diam -- tidak ada token reset, tidak ada email, respons ke klien tetap generik (tidak membuka enumerasi email). Alasannya: reset tidak memverifikasi akun dan tidak menyentuh token undangan, jadi akan meninggalkan akun admin tak-terverifikasi dengan token undangan yang masih hidup. User biasa tidak terpengaruh.

**Batasan yang perlu diketahui:**
- Admin pertama yang sudah mendaftar tetapi belum klik link verifikasi juga tidak bisa lewat forgot-password; jalurnya `POST /api/auth/resend-verification` (link verifikasi admin tetap ke `/admin/signin`).
- `change-password` ikut dibatasi 5 percobaan/menit/IP (termasuk percobaan dengan input tidak valid). Jika di belakang proxy, pastikan `app.rate-limit.trust-forward-headers` benar agar IP klien yang dihitung.

**Test baru/diperluas:**
- `AdminPasswordFlowTest` (baru, 8 test): ubah password mempertahankan `role=ADMIN` di token & body dan mengganti versi password; password saat ini salah tidak mengubah apa pun; login admin hanya dengan password baru; forgot-password admin terverifikasi -> link `/admin/reset-password`; admin belum terverifikasi -> tanpa token/email dan token undangan tidak tersentuh; user biasa belum terverifikasi tetap dikirimi link; reset sekali pakai + login password baru; token kedaluwarsa ditolak.
- `RateLimitFilterAdminTest`: batas `change-password` (percobaan ke-6 -> 429, aturan lain tidak ikut terkunci; GET tidak dibatasi).
- `JwtAuthenticationFilterRoleTest`: token admin lama ditolak setelah password berubah, token baru diterima.

**Verifikasi:** seluruh berkas Java lolos pemeriksaan sintaks (parser javac) dan semua `import com.example.app...` di berkas baru/diubah ter-resolve. **`mvn test` TIDAK dapat dijalankan di lingkungan ini (Maven Central tidak terjangkau)**; jalankan `mvn test` sebelum merge.

**Cek manual (setelah backend jalan):**
1. Login admin, `POST /api/auth/change-password` dgn password saat ini benar -> 200 + `accessToken` baru; token lama -> 401; `GET /api/admin/me` dgn token baru -> 200.
2. Password saat ini salah -> 400 "Password saat ini salah."; panggil 6x dalam semenit -> 429.
3. `POST /api/auth/forgot-password` dgn email admin terverifikasi -> email berisi link `/admin/reset-password?token=...`; selesaikan reset, login `/api/admin/auth/login` dgn password baru.
4. Undang admin baru (belum diterima), lalu `forgot-password` dgn emailnya -> respons sama (200 generik) tetapi tidak ada email reset.

---

## 30. Fitur Admin: ubah password & lupa/reset password admin (Pigay, frontend)

Menyesuaikan dengan backend bagian 29. Alurnya sama dengan user dan memakai endpoint `auth` yang sama (`POST /api/auth/change-password`, `forgot-password`, `reset-password`) -- tidak ada tipe atau DTO baru; `ChangePasswordRequest/Response` dan `ForgotPasswordRequest` yang sudah ada tetap match 100% dengan DTO backend.

**Dropdown ikon user di header admin (`AdminLayoutView`):** sekarang berisi **Ubah Password** (ikon `ShieldIcon`) dan **Logout**, dipisah garis tipis. Menu menutup saat klik di luar atau menekan Esc (listener `document` dipasang di `onMounted`, dilepas di `onBeforeUnmount`) dan setelah salah satu item dipilih. Atribut aksesibilitas: `aria-haspopup`, `aria-expanded`, `role="menu"` / `role="menuitem"`.

**Halaman ubah password -- `/admin/change-password`** (`AdminChangePasswordView`, anak `/admin`, jadi mewarisi `requiresAuth` + `role: 'ADMIN'`):
- Field: password saat ini, password baru (dengan checklist aturan yang sama dengan backend), konfirmasi. Tombol aktif bila semua terisi, aturan lolos, dan konfirmasi sama.
- Sukses -> token tersimpan diganti `accessToken` baru dari respons (token lama dicabut backend), sehingga sesi tidak putus dan peran tetap ADMIN. Field dikosongkan dan pesan sukses ditampilkan.
- Error: 400 validasi -> per field; 400 "Password saat ini salah." -> di bawah field password saat ini (400, bukan 401, jadi tidak memicu logout otomatis); 429 (rate limit baru backend), 403, dan gangguan jaringan -> pesan umum di atas tombol.
- Tautan "Kirim link reset ke email saya" (muncul setelah `GET /api/admin/me` mengembalikan email): memanggil `forgot-password` untuk email admin sendiri dan menampilkan pesan generik dari server. Setelah reset diselesaikan lewat email, sesi lama dicabut dan interceptor mengarahkan ke `/admin/signin?expired=true` (sama dengan user).
- `AdminPasswordField` mendapat prop opsional `autocomplete` (`'new-password'` default; `'current-password'` untuk password saat ini) agar password manager tidak salah mengisi.

**Lupa password admin -- `/admin/forgot-password`** (publik, `name: 'admin-forgot-password'`, `meta.area = 'admin'`): memakai ulang `ForgotPasswordView` seperti `ResetPasswordView` memakai ulang untuk reset. Bedanya hanya tombol "Back to Sign In" -> `/admin/signin`.
- `AdminSigninView`: "Forgot Password?" sekarang ke halaman admin ini (sebelumnya ke halaman user, sehingga tombol kembalinya salah arah).
- `ResetPasswordView`: bila token tidak valid/kedaluwarsa, tombol minta link baru kini ke `/admin/forgot-password` untuk area admin.
- `shared/services/authPaths.ts`: `/admin/forgot-password` ditambahkan ke daftar halaman publik (anti redirect-loop untuk respons 401).

**Test** (`frontend/tests/`, `npm test`): 36 test (sebelumnya 30). Baru/diperluas: nama & path & `meta.area` route lupa password admin (dan halaman user tidak ikut ber-area admin); `admin-change-password` ada di bawah `/admin` berjudul "Ubah Password"; guard `admin-change-password` (ADMIN lolos; user -> `/dashboard`; tamu/kedaluwarsa -> `/admin/signin`); `/admin/forgot-password` publik dan `/admin/change-password` bukan publik di `authPaths`; token baru setelah ubah password tetap `ADMIN`; serta pemeriksa statis bahwa setiap tujuan `router.push({ name })` di view admin dan lupa/reset password adalah nama route yang ada, dan link "Forgot Password?" admin tidak menunjuk ke halaman user.

**Verifikasi:** 36 test lulus; 8 mutasi sengaja (link signin, authPaths, nama/path/judul route, `meta.area`, nama tujuan di reset & layout) tertangkap semuanya. Script semua `.vue` di `modules/admin` dan `modules/auth` lolos type-check TypeScript strict dengan shim (tersisa 4 error lama di `ResendVerificationForm` dan `apiClient` yang tidak diubah, artefak shim). Pemeriksa template atas 61 berkas `.vue` bersih (variabel/komponen terdefinisi, tidak ada impor tak terpakai) dan terbukti mendeteksi 3 mutasi.
**BELUM diverifikasi:** `npm run build` (`vue-tsc -b` + Vite) dan tampilan di browser (registry npm tidak terjangkau di lingkungan ini). Jalankan `npm run build` dan `npm test`, lalu cek manual: (1) login admin -> klik ikon user: ada Ubah Password dan Logout; klik di luar/Esc menutup, (2) Ubah Password: password saat ini salah -> pesan di bawah field; benar -> pesan sukses dan halaman admin lain tetap bisa dibuka tanpa login ulang, (3) coba 6x dalam semenit -> pesan "Terlalu banyak percobaan", (4) `/admin/signin` -> "Forgot Password?" -> `/admin/forgot-password` -> email -> link `/admin/reset-password` -> sign in dengan password baru, (5) buka `/admin/change-password` sebagai user biasa -> kembali ke `/dashboard`.

---

## 31. Fitur Admin: menu Admin -- daftar berpaginasi, nonaktifkan, aktifkan kembali (Luhut, backend)

**Keputusan produk:** admin bisa melihat daftar admin (berpaginasi), mengundang admin (endpoint undangan yang sudah ada, termasuk kirim ulang), menonaktifkan, dan mengaktifkan kembali admin.

**Migrasi `V20__add_is_active_to_users.sql`** (prod/Flyway; `ddl-auto=validate` di prod, jadi V20 WAJIB jalan): `users.is_active BOOLEAN NOT NULL DEFAULT TRUE` -- semua akun yang ada tetap aktif. Di lokal (`ddl-auto=update`) kolom dibuat Hibernate; `columnDefinition = "boolean not null default true"` pada `User.active` membuat penambahan kolom NOT NULL ke tabel berisi tidak gagal.

**Endpoint baru (`/api/admin/**`, hanya ROLE_ADMIN; tanpa perubahan `SecurityConfig`):**
- `GET /api/admin/admins?page=0&size=10` -> `{items, page, size, totalItems, totalPages}`. **Terbaru dulu** (`createdAt` menurun, lalu `id`). `page` mulai 0; `page < 0` -> 0, `size < 1` -> 10, `size > 50` -> 50 (dipaksa masuk batas, bukan error; respons memuat nilai yang benar-benar dipakai). Halaman di luar jangkauan -> `items` kosong dengan total yang tetap benar. Tiap item: `id, name, email, status, self, createdAt, invitationExpiresAt`.
- `POST /api/admin/admins/{id}/deactivate` dan `.../activate` -> item admin terbaru. Idempoten (yang sudah di status tujuan dikembalikan apa adanya, tanpa log).
- Kirim ulang undangan tidak butuh endpoint baru: `POST /api/admin/admins` yang sudah ada mengirim ulang untuk admin berstatus menunggu undangan (token baru, token lama mati).

**Status (`AdminStatus`, diturunkan dari kolom yang ada):** `INACTIVE` (active=false, mengalahkan yang lain) | `ACTIVE` (aktif + terverifikasi) | `PENDING` (belum terverifikasi; `invitationExpiresAt` terisi hanya bila menunggu undangan, null untuk admin pertama yang belum klik link verifikasi).

**Aturan penonaktifan:**
1. Tidak boleh menonaktifkan diri sendiri -> 400 (`CannotDeactivateSelfException`; email dibandingkan tanpa peduli huruf besar/kecil).
2. Admin aktif terakhir dilindungi -> 409 (`LastActiveAdminException`). Karena pemanggil selalu admin aktif dan tidak boleh menarget diri sendiri, aturan ini praktis hanya bisa kena saat balapan; penjagaannya: seluruh `deactivate` diserialkan dengan `pg_advisory_xact_lock` (kunci `7_201_810_002L`, beda dari kunci pendaftaran admin) dan jumlah admin aktif dihitung SETELAH kunci diambil. Hanya admin aktif+terverifikasi yang dihitung, jadi membatalkan undangan admin PENDING tidak terhalang.
3. Id yang tidak ada ATAU bukan akun ADMIN -> 404 (`AdminNotFoundException`; user biasa tidak dibedakan dari "tidak ada").
4. Efek langsung: `JwtAuthenticationFilter` menolak user nonaktif di setiap request (token yang masih berlaku pun -> 401, FE diarahkan ke sign in); login admin -> **403 dengan `code: "ACCOUNT_DEACTIVATED"`** (hanya SETELAH password benar; password salah tetap 401 generik). `GlobalExceptionHandler` mendapat field opsional `code`; 403 "belum verifikasi" tetap TANPA `code`, itulah pembeda untuk FE (form kirim ulang verifikasi hanya untuk yang tanpa `code`).
5. Token reset password dihapus saat dinonaktifkan; `forgot-password` untuk akun nonaktif diabaikan diam-diam (respons tetap generik); `reset-password`/`validate` menolak akun nonaktif.
6. **Penyimpangan kecil dari analisis:** token undangan TIDAK dihapus saat dinonaktifkan -- selama nonaktif undangan ditolak (validasi & terima), dan `invite` ke admin nonaktif -> 409 ("aktifkan kembali dulu"). Alasannya: bila token dihapus, admin PENDING yang diaktifkan kembali tidak bisa dibedakan dari admin pertama yang belum verifikasi dan tidak bisa diundang ulang. Dengan token dipertahankan, setelah diaktifkan kembali statusnya PENDING dengan masa berlaku lama (FE menandai kedaluwarsa) dan "kirim ulang" berjalan lewat endpoint undangan yang sama.
7. Log aktivitas: `ADMIN_DEACTIVATED:<email target>` / `ADMIN_REACTIVATED:<email target>` atas nama pelaku (dipotong 255 karakter sesuai kolom `activity`).

**Berkas:** `User.active`, `UserRepository` (`findAllByUserType(Pageable)`, `countByUserTypeAndActiveTrueAndVerifiedTrue`), `AdminManagementService(Impl)` (`listAdmins`, `deactivate`, `activate`), `AdminManagementController`, DTO `AdminListItemResponseDTO`, `AdminListResponseDTO`, enum `AdminStatus`, exception `CannotDeactivateSelfException`, `LastActiveAdminException`, `AdminNotFoundException`, `AccountDeactivatedException`; diubah: `JwtAuthenticationFilter`, `AdminAuthServiceImpl` (login, undangan), `AuthServiceImpl` (forgot/reset/validate), `GlobalExceptionHandler`.

**Batasan yang perlu diketahui:**
- Token JWT lama milik admin yang diaktifkan kembali berlaku lagi sampai habis masa berlakunya (password tidak berubah). Bila perlu dicabut paksa, ganti password lewat reset.
- Admin pertama yang belum verifikasi email bisa dinonaktifkan/diaktifkan seperti admin lain; `registration-status` tetap menghitung admin nonaktif, jadi pendaftaran admin pertama tidak pernah terbuka lagi.
- Menonaktifkan tidak mencabut link verifikasi email admin pertama (link tetap bisa memverifikasi, tetapi akun nonaktif tetap tidak bisa login).
- Belum ada pencarian/filter status pada daftar, hapus admin, atau penonaktifan akun user biasa (kolom `is_active` sudah ada dan filter JWT berlaku untuk semua tipe, tetapi belum ada API untuk mengubahnya selain admin).
- `lockAdvisory` memakai query native PostgreSQL yang belum dijalankan terhadap PostgreSQL sungguhan (lihat Verifikasi).

**Test baru/diperluas:**
- `AdminListAndDeactivateTest` (17): urutan terbaru dulu, pemaksaan `page`/`size`, nilai efektif di respons, status/`self`/masa berlaku undangan per baris, total & halaman di luar jangkauan, nonaktifkan (kunci, token reset, log), diri sendiri, admin aktif terakhir, PENDING tidak dihitung, id tak dikenal/bukan admin, idempoten, pemotongan teks log, aktifkan, undangan tetap bisa dikirim ulang setelah aktif kembali, invite ke admin nonaktif -> 409.
- `GlobalExceptionHandlerAdminTest` (3): status & body error, `code` hanya pada nonaktif.
- `AdminListDtoJsonTest` (3): nama field JSON tepat sama dengan kontrak FE (`items/page/size/totalItems/totalPages`, `self`, `invitationExpiresAt: null` tetap muncul, enum sebagai string).
- Diperluas: `AdminAuthServiceTest` (login nonaktif = 403 ber-code, password salah tidak membocorkan apa pun, aktif kembali bisa login, undangan ditolak saat nonaktif lalu berlaku lagi), `JwtAuthenticationFilterRoleTest` (token yang sama ditolak saat nonaktif dan diterima lagi saat aktif), `AdminPasswordFlowTest` (forgot/reset akun nonaktif).

**Verifikasi:** seluruh 331 berkas Java lolos pemeriksaan sintaks (parser javac), semua `import com.example.app...` ter-resolve, dan nama method/field yang dipakai test sudah dicocokkan dengan definisinya. **`mvn test` TIDAK dapat dijalankan di lingkungan ini (Maven Central tidak terjangkau)**, jadi kode & test baru belum dikompilasi/dijalankan dengan Spring/Lombok/JUnit; jalankan `mvn test` sebelum merge.

**Cek manual (setelah backend jalan; V20 akan terpasang otomatis):**
1. Login admin, `GET /api/admin/admins?page=0&size=2` -> `items` terbaru dulu, `self:true` pada baris sendiri, `totalPages` benar; `page=99` -> `items` kosong; `size=500` -> `size:50` di respons.
2. Undang 2-3 admin lewat `POST /api/admin/admins`, ulangi GET: admin baru muncul di halaman 1 berstatus `PENDING` dengan `invitationExpiresAt`.
3. `POST .../{id}/deactivate` pada admin kedua (yang sudah login di sesi lain): respons `INACTIVE`; token sesi lain -> 401; login admin itu -> 403 dengan `code:"ACCOUNT_DEACTIVATED"`; `forgot-password` untuknya -> 200 generik tanpa email.
4. `deactivate` pada diri sendiri -> 400; pada id user biasa -> 404. Dua admin saling `deactivate` bersamaan (dua curl paralel) -> salah satunya 409.
5. `.../activate` -> `ACTIVE`; admin itu bisa login lagi.
6. Kirim ulang undangan: `POST /api/admin/admins` dengan email admin `PENDING` -> email baru terkirim; ke email admin nonaktif -> 409.


## 32. Fitur Admin: menu Admin -- daftar berpaginasi, nonaktifkan, aktifkan kembali, kirim ulang undangan (Pigay, frontend)

Mengikuti kontrak backend bagian 31 (`GET /api/admin/admins`, `POST .../{id}/deactivate|activate`). Semua kode di `frontend/src/modules/admin/`.

**Sidebar:** menu **Admin** berada paling atas, sebelum User (`utils/adminMenu.ts`, `ADMIN_MENU`). Halaman "Undang Admin" tetap menyorot menu Admin.

**Halaman baru `views/AdminListView.vue` (`/admin/admins`, nama route `admin-admins`):**
- Tabel Nama / Email / Status (badge) / Dibuat / Aksi, 10 baris per halaman. Halaman disimpan di URL (`?page=2`, mulai dari 1; API tetap mulai dari 0), jadi refresh dan tombol Back tetap di halaman yang sama. `?page=99` atau halaman yang kosong setelah data berkurang otomatis pindah ke halaman terakhir.
- Baris sendiri berlabel "(Anda)" tanpa tombol aksi. Aksi lain mengikuti status: Aktif -> Nonaktifkan; Nonaktif -> Aktifkan; Menunggu undangan -> Kirim ulang undangan + Nonaktifkan; Menunggu verifikasi email (admin pertama) -> Nonaktifkan.
- Nonaktifkan memakai dialog konfirmasi (`AdminConfirmDialog`); Aktifkan dan Kirim ulang undangan langsung jalan. Kirim ulang memakai endpoint undangan yang sama (`adminManagementService.invite`).
- Setelah aksi, daftar dimuat ulang di halaman yang sama. Error 404/409 (data sudah berubah di server, mis. admin aktif terakhir) juga memuat ulang. Ada state memuat, kosong, dan gagal (tombol "Coba lagi"). Respons lama yang terlambat tidak menimpa data baru. Tombol dan pager terkunci selama memuat.

**Berkas baru:** `utils/adminMenu.ts`, `utils/adminList.ts` (logika murni agar bisa diuji), `components/AdminConfirmDialog.vue`, `components/AdminPager.vue`, `views/AdminListView.vue`.
**Diubah:** `types/admin-auth.types.ts`, `services/adminManagement.service.ts` (`list/deactivate/activate`), `utils/adminErrors.ts` (`code`, `isAccountDeactivated`, `offersVerificationResend`), `components/AdminSidebar.vue`, `routes.ts`, `AdminInviteView.vue` (Kembali -> daftar admin, tautan "Lihat daftar admin" setelah sukses), `AdminDashboardView.vue` (kartu "Kelola Admin"), `AdminSigninView.vue`.
**Sign in admin:** 403 `ACCOUNT_DEACTIVATED` menampilkan pesan akun dinonaktifkan **tanpa** form kirim ulang verifikasi; 403 lain (belum diverifikasi) tetap menampilkan form itu.

**Test:** `adminMenu.test.ts`, `adminList.test.ts` (baru); `adminRoutes`, `guards`, `adminErrors` diperluas. Total 54 test Node lulus; 8 mutasi (aksi baris sendiri, kirim ulang, batas kedaluwarsa, offset halaman URL, sorotan menu, nama route, urutan cek nonaktif) semuanya tertangkap.

**Verifikasi:** type-check strict (shim) bersih kecuali 4 artefak lama shim, pemeriksa template 14 berkas `.vue` admin bersih. **`npm run build`/`vue-tsc -b` dan tampilan browser TIDAK dapat dijalankan di lingkungan ini (registry npm diblokir)**; jalankan `npm test` dan `npm run build` sebelum merge.

**Catatan:** `invitationExpiresAt` adalah waktu server tanpa zona; FE membandingkannya dengan jam browser, jadi label "Undangan kedaluwarsa" bisa meleset jika zona waktu server dan browser berbeda (backend tetap menjadi penentu sebenarnya saat undangan dipakai).

**Cek manual:**
1. Sidebar: Admin muncul pertama, sebelum User; di /admin/admins/new menu Admin tetap menyala.
2. Buka /admin/admins: baris sendiri berlabel (Anda) tanpa tombol. Dengan >10 admin, pager bekerja dan `?page=2` bertahan setelah refresh.
3. Undang admin baru, kembali ke daftar: muncul "Menunggu undangan". Kirim ulang undangan -> email baru terkirim.
4. Nonaktifkan admin lain -> dialog konfirmasi -> status Nonaktif; sesinya di browser lain langsung ke sign in. Login admin itu -> pesan nonaktif tanpa form kirim ulang.
5. Aktifkan kembali -> bisa login lagi. Coba nonaktifkan admin aktif terakhir (dari sesi lain) -> pesan error dan daftar tersinkron.

# Deploy produksi (Docker Compose)

Arsitektur: `web` (Nginx: SPA + proxy `/api`) → `backend` (Spring Boot, profil `prod`) → `db` (PostgreSQL 16, database `frontline` dan `master_data`). Satu origin, jadi tidak ada masalah CORS. Database tidak dibuka ke luar.

## Yang ada di git
`docker-compose.yml`, `.env.example`, `backend/Dockerfile`, `frontend/Dockerfile`, `frontend/nginx.conf`, `deploy/*`, `.github/workflows/ci.yml`, plus kode. **Tidak** di git: `.env` (rahasia).

## Pertama kali di server
Prasyarat: Docker Engine + Compose plugin, domain yang mengarah ke server.

```sh
git clone <repo> fulmintis && cd fulmintis
cp .env.example .env && chmod 600 .env
openssl rand -base64 48   # jalankan 2x: untuk JWT_SECRET dan DB_PASSWORD
nano .env                 # isi PUBLIC_URL, DB_*, JWT_SECRET, MAIL_*, (AI_* bila perlu)
./deploy/deploy.sh
```

Database dan tabel `master_data` dibuat otomatis pada start pertama (volume kosong). Tabel `frontline` dibuat Flyway (V1..V21). Admin pertama: buka `PUBLIC_URL/admin/signup` (hanya terbuka selama belum ada admin).

## HTTPS (wajib)
Container `web` hanya mendengarkan di `127.0.0.1:8080`. Pasang reverse proxy TLS di host, mis. Caddy:

```
app.domainanda.com {
    reverse_proxy 127.0.0.1:8080
}
```
Caddy otomatis mengurus sertifikat dan mengirim `X-Forwarded-Proto: https`. Link email memakai `PUBLIC_URL`, jadi harus `https://...`.

## Update berikutnya
`./deploy/deploy.sh` (git pull + build + restart; data aman di volume). Migrasi Flyway jalan otomatis.

## Cadangan
`./deploy/backup.sh /var/backups/fulmintis` (pg_dump kedua database + folder evidence); jadwalkan lewat cron. **Uji restore** minimal sekali.

## Pemeriksaan
`docker compose ps` (semua `healthy`), `docker compose logs -f backend`. Backend gagal start dengan sengaja bila variabel wajib kosong atau `AI_PROVIDER=fake`.

## Catatan
- Pertama kali dari database lama buatan Hibernate: lihat `spring.flyway.baseline-*` di `application-prod.properties`.
- Gemini produksi: key project Google Cloud berbayar + `AI_BILLING_TIER=paid`. Key yang pernah bocor wajib dicabut.
- Ubah port/bind lewat `WEB_PORT` dan `WEB_BIND` di `.env`.

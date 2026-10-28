#!/bin/sh
# filepath: /deploy/deploy.sh
# Jalankan DI SERVER dari root repo: ./deploy/deploy.sh
# Menarik kode terbaru, membangun ulang image, lalu restart tanpa menghapus data (volume tetap).
set -eu
cd "$(dirname "$0")/.."
[ -f .env ] || { echo "File .env belum ada. cp .env.example .env lalu isi." >&2; exit 1; }
git pull --ff-only
docker compose --env-file .env up -d --build
docker compose ps

#!/bin/sh
# filepath: /deploy/backup.sh
# Cadangan harian: ./deploy/backup.sh [folder-tujuan]  (cron: 0 2 * * * /path/repo/deploy/backup.sh /var/backups/fulmintis)
set -eu
cd "$(dirname "$0")/.."
. ./.env
OUT="${1:-./backups}"; STAMP=$(date +%Y%m%d-%H%M%S)
mkdir -p "$OUT"
for DB in frontline master_data; do
  docker compose exec -T db pg_dump -U "$DB_USERNAME" -Fc "$DB" > "$OUT/$DB-$STAMP.dump"
done
# Evidence (volume uploads)
docker compose exec -T backend tar -C /var/data -cf - frontline-uploads > "$OUT/uploads-$STAMP.tar"
# Simpan 14 cadangan terakhir per jenis
for P in frontline master_data uploads; do ls -1t "$OUT"/$P-* 2>/dev/null | tail -n +15 | xargs -r rm --; done
echo "Cadangan tersimpan di $OUT"

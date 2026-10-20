#!/usr/bin/env bash
# NFR-REL-002: encrypted application-database export + isolated restore rehearsal.
set -euo pipefail
umask 077
passphrase="${BACKUP_ENCRYPTION_PASSPHRASE:-}"

if [[ "${PGDATABASE:-}" != 'facilityflow_prod' ||
      "${PGUSER:-}" != facilityflow_prod_backup.* ||
      "${PGSSLMODE:-}" != 'verify-full' ||
      ! "${PG_CONTAINER:-}" =~ ^[a-f0-9]{64}$ ||
      -z "${PGPASSWORD:-}" || ${#passphrase} -lt 32 ]]; then
    echo 'Configure Production backup identity, verified TLS, container and encryption secret.' >&2
    exit 2
fi
if [[ "${RESTORE_PASSWORD:-}" != 'disposable-restore-only' ]]; then
    echo 'Restore is restricted to the disposable CI service.' >&2
    exit 2
fi

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
started="$(date +%s)"
output="${RUNNER_TEMP:?}/facilityflow-production-${GITHUB_RUN_ID:?}-${GITHUB_RUN_ATTEMPT:?}.dump.gpg"
docker cp src/main/resources/certs/supabase-root-2021.crt "$PG_CONTAINER:/tmp/facilityflow-ca.crt" >/dev/null
export PGSSLROOTCERT=/tmp/facilityflow-ca.crt

# Passwords are passed through environment, never command-line values or logs.
if ! docker exec --env PGHOST --env PGPORT --env PGDATABASE --env PGUSER \
    --env PGPASSWORD --env PGSSLMODE --env PGSSLROOTCERT "$PG_CONTAINER" \
    pg_dump --format=custom --enable-row-security --schema=facilityflow \
    > "$work/database.dump" 2> "$work/private-error.txt"; then
    echo 'Production dump failed; verify backup role grants, pooler and TLS.' >&2
    exit 1
fi
printf '%s' "$BACKUP_ENCRYPTION_PASSPHRASE" | gpg --batch --yes --pinentry-mode loopback \
    --passphrase-fd 0 --symmetric --cipher-algo AES256 --output "$output" \
    "$work/database.dump" 2> "$work/private-error.txt"
test -s "$output"
# Verify the actual encrypted artifact, rather than restoring the pre-encryption file.
printf '%s' "$BACKUP_ENCRYPTION_PASSPHRASE" | gpg --batch --yes --pinentry-mode loopback \
    --passphrase-fd 0 --decrypt "$output" > "$work/restored.dump" 2> "$work/private-error.txt"
cmp "$work/database.dump" "$work/restored.dump"

# Restore commands cannot inherit the remote Production connection settings.
restore=(docker exec --env PGHOST=127.0.0.1 --env PGPORT=5432 --env PGUSER=postgres
    --env PGDATABASE=facilityflow_restore --env PGSSLMODE=disable
    --env PGPASSWORD="$RESTORE_PASSWORD" "$PG_CONTAINER")
"${restore[@]}" psql --dbname=postgres --set ON_ERROR_STOP=1 --command \
    'CREATE ROLE facilityflow_prod_migrator; CREATE ROLE facilityflow_prod_app;
     CREATE ROLE facilityflow_app; CREATE ROLE facilityflow_prod_backup;' >/dev/null
"${restore[@]}" psql --dbname=postgres --set ON_ERROR_STOP=1 --command \
    'CREATE DATABASE facilityflow_restore OWNER facilityflow_prod_migrator;' >/dev/null
docker cp "$work/restored.dump" "$PG_CONTAINER:/tmp/restore.dump" >/dev/null
if ! "${restore[@]}" pg_restore --exit-on-error --no-owner --role=facilityflow_prod_migrator \
    --dbname=facilityflow_restore /tmp/restore.dump > "$work/private-restore.txt" 2>&1; then
    echo 'Isolated restore failed; do not treat the backup as verified.' >&2
    exit 1
fi
"${restore[@]}" psql --set ON_ERROR_STOP=1 --command \
    "SET ROLE facilityflow_prod_app;
     DO \$verify\$ BEGIN
       IF (SELECT max(version::int) FROM facilityflow.schema_version WHERE success) IS DISTINCT FROM 6
          OR (SELECT count(*) FROM facilityflow.user_account) < 1
          OR has_table_privilege(current_user, 'facilityflow.user_account', 'DELETE')
          OR has_table_privilege(current_user, 'facilityflow.audit_event', 'UPDATE')
          OR has_schema_privilege(current_user, 'facilityflow', 'CREATE') THEN
         RAISE EXCEPTION 'Restored runtime/schema verification failed';
       END IF;
     END \$verify\$;" > "$work/private-check.txt" 2>&1
elapsed="$(( $(date +%s) - started ))"
if (( elapsed > 14400 )); then echo 'Restore exceeded the four-hour RTO target.' >&2; exit 1; fi
sha256sum "$output" > "$output.sha256"
{
    echo '### NFR-REL-002 encrypted backup and isolated restore'
    echo '- Database: facilityflow_prod; schema: facilityflow'
    echo '- Encryption: AES256; artifact checksum recorded'
    echo '- Restored schema version 6, account read and runtime restrictions passed'
    echo "- Backup plus restore elapsed: ${elapsed}s (target RTO: 14400s)"
    echo '- Destination: disposable CI PostgreSQL, never the live database'
} >> "${GITHUB_STEP_SUMMARY:?}"
echo "Encrypted backup and isolated restore passed in ${elapsed}s."

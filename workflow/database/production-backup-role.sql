-- NFR-REL-002 / NFR-SEC-001: owner connection to facilityflow_prod only.
-- Reapply grants/policies after later schema migrations; never change login passwords here.
BEGIN;
DO $backup$
DECLARE item record;
BEGIN
    IF current_database() <> 'facilityflow_prod' OR current_user <> 'postgres' THEN
        RAISE EXCEPTION 'Use owner connection to facilityflow_prod';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'facilityflow_prod_backup') THEN
        CREATE ROLE facilityflow_prod_backup NOLOGIN NOSUPERUSER NOCREATEDB
            NOCREATEROLE NOREPLICATION NOBYPASSRLS;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'facilityflow_prod_backup'
               AND (rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls))
       OR EXISTS (SELECT 1 FROM pg_auth_members WHERE member = 'facilityflow_prod_backup'::regrole) THEN
        RAISE EXCEPTION 'Backup role must be restricted without role memberships';
    END IF;
    IF has_schema_privilege('facilityflow_prod_backup', 'facilityflow', 'CREATE')
       OR has_database_privilege('facilityflow_prod_backup', current_database(), 'CREATE')
       OR EXISTS (SELECT 1 FROM pg_database WHERE datdba = 'facilityflow_prod_backup'::regrole)
       OR EXISTS (SELECT 1 FROM pg_namespace WHERE nspowner = 'facilityflow_prod_backup'::regrole)
       OR EXISTS (SELECT 1 FROM pg_class WHERE relowner = 'facilityflow_prod_backup'::regrole)
       OR EXISTS (SELECT 1 FROM pg_proc WHERE proowner = 'facilityflow_prod_backup'::regrole)
       OR EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                  CROSS JOIN LATERAL aclexplode(c.relacl) a
                  WHERE a.grantee = 'facilityflow_prod_backup'::regrole
                    AND (n.nspname <> 'facilityflow' OR c.relkind NOT IN ('r', 'p', 'v', 'm', 'S')
                         OR a.privilege_type <> 'SELECT' OR a.is_grantable))
       OR EXISTS (SELECT 1 FROM pg_attribute col JOIN pg_class c ON c.oid = col.attrelid
                  JOIN pg_namespace n ON n.oid = c.relnamespace
                  CROSS JOIN LATERAL aclexplode(col.attacl) a
                  WHERE a.grantee = 'facilityflow_prod_backup'::regrole
                    AND (n.nspname <> 'facilityflow' OR c.relkind NOT IN ('r', 'p', 'v', 'm')
                         OR a.privilege_type <> 'SELECT' OR a.is_grantable))
       OR EXISTS (SELECT 1 FROM pg_namespace n CROSS JOIN LATERAL aclexplode(n.nspacl) a
                  WHERE a.grantee = 'facilityflow_prod_backup'::regrole
                    AND (n.nspname <> 'facilityflow' OR a.privilege_type <> 'USAGE' OR a.is_grantable))
       OR EXISTS (SELECT 1 FROM pg_database d CROSS JOIN LATERAL aclexplode(d.datacl) a
                  WHERE a.grantee = 'facilityflow_prod_backup'::regrole
                    AND (d.datname <> 'facilityflow_prod' OR a.privilege_type <> 'CONNECT' OR a.is_grantable))
       -- Reject direct ACLs on other object types/databases and any ownership.
       OR EXISTS (SELECT 1 FROM pg_shdepend d
                  WHERE d.refclassid = 'pg_authid'::regclass
                    AND d.refobjid = 'facilityflow_prod_backup'::regrole
                    AND (d.deptype IN ('o', 'i') OR (d.deptype = 'a' AND NOT (
                        (d.dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
                         AND d.classid IN ('pg_class'::regclass, 'pg_namespace'::regclass))
                        OR (d.dbid = 0 AND d.classid = 'pg_database'::regclass))))) THEN
        RAISE EXCEPTION 'Backup role has unexpected privileges or ownership; inspect before setup';
    END IF;
    GRANT CONNECT ON DATABASE facilityflow_prod TO facilityflow_prod_backup;
    GRANT USAGE ON SCHEMA facilityflow TO facilityflow_prod_backup;
    GRANT SELECT ON ALL TABLES IN SCHEMA facilityflow TO facilityflow_prod_backup;
    GRANT SELECT ON ALL SEQUENCES IN SCHEMA facilityflow TO facilityflow_prod_backup;
    FOR item IN SELECT c.oid::regclass AS object_name FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'facilityflow' AND c.relkind = 'r'
    LOOP
        EXECUTE format('DROP POLICY IF EXISTS backup_read ON %s', item.object_name);
        EXECUTE format('CREATE POLICY backup_read ON %s FOR SELECT TO facilityflow_prod_backup USING (true)',
                       item.object_name);
    END LOOP;
END
$backup$;
COMMIT;

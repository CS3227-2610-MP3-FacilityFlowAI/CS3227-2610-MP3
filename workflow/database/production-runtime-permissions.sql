-- NFR-DEP-001 / NFR-SEC-001: run as Production migrator after each migration.
-- Reuse migration-defined permissions without changing V1-V6 checksums or
-- granting membership in development roles (roles are cluster-wide).
DO $permissions$
DECLARE
    item record;
BEGIN
    IF current_database() <> 'facilityflow_prod'
       OR current_user <> 'facilityflow_prod_migrator' THEN
        RAISE EXCEPTION 'Use the Production database and migration identity';
    END IF;
    IF pg_has_role('facilityflow_prod_app', 'facilityflow_app', 'MEMBER')
       OR pg_has_role('facilityflow_prod_app', 'facilityflow_migrator', 'MEMBER')
       OR pg_has_role('facilityflow_prod_app', 'facilityflow_prod_migrator', 'MEMBER')
       OR has_database_privilege('facilityflow_app', 'facilityflow_prod', 'CONNECT') THEN
        RAISE EXCEPTION 'Database role isolation precondition failed';
    END IF;

    -- Table revocation does not remove column grants; reset both atomically.
    FOR item IN
        SELECT c.oid::regclass AS object_name, col.attname
        FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
        JOIN pg_attribute col ON col.attrelid = c.oid AND col.attnum > 0 AND NOT col.attisdropped
        WHERE n.nspname = 'facilityflow' AND EXISTS (
            SELECT 1 FROM aclexplode(col.attacl) a
            WHERE a.grantee = 'facilityflow_prod_app'::regrole)
    LOOP
        EXECUTE format('REVOKE ALL (%I) ON TABLE %s FROM facilityflow_prod_app',
            item.attname, item.object_name);
    END LOOP;
    REVOKE ALL ON ALL TABLES IN SCHEMA facilityflow FROM facilityflow_prod_app;
    REVOKE ALL ON ALL SEQUENCES IN SCHEMA facilityflow FROM facilityflow_prod_app;
    GRANT USAGE ON SCHEMA facilityflow TO facilityflow_prod_app;
    FOR item IN
        SELECT c.oid::regclass AS object_name, c.relkind, a.privilege_type
        FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
        CROSS JOIN LATERAL aclexplode(c.relacl) a
        WHERE n.nspname = 'facilityflow' AND a.grantee = 'facilityflow_app'::regrole
    LOOP
        EXECUTE format('GRANT %s ON %s %s TO facilityflow_prod_app',
            item.privilege_type, CASE WHEN item.relkind = 'S' THEN 'SEQUENCE' ELSE 'TABLE' END,
            item.object_name);
    END LOOP;
    FOR item IN
        SELECT c.oid::regclass AS object_name, col.attname, a.privilege_type
        FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
        JOIN pg_attribute col ON col.attrelid = c.oid AND col.attnum > 0 AND NOT col.attisdropped
        CROSS JOIN LATERAL aclexplode(col.attacl) a
        WHERE n.nspname = 'facilityflow' AND a.grantee = 'facilityflow_app'::regrole
    LOOP
        EXECUTE format('GRANT %s (%I) ON TABLE %s TO facilityflow_prod_app',
            item.privilege_type, item.attname, item.object_name);
    END LOOP;
    FOR item IN
        SELECT p.polname, c.oid::regclass AS object_name, p.polroles
        FROM pg_policy p JOIN pg_class c ON c.oid = p.polrelid
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'facilityflow' AND 'facilityflow_app'::regrole = ANY(p.polroles)
    LOOP
        IF item.polroles <> ARRAY['facilityflow_app'::regrole::oid] THEN
            RAISE EXCEPTION 'Review policy with unexpected role list';
        END IF;
        EXECUTE format('ALTER POLICY %I ON %s TO facilityflow_prod_app',
            item.polname, item.object_name);
    END LOOP;
END
$permissions$;

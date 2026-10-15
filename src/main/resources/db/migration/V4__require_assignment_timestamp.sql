-- REQ-MGR-004 / REQ-TECH-003: new assignments need timestamps; existing migrated
-- unknown times remain unknown until the next assignment. Never invent history.
ALTER TABLE facilityflow.maintenance_request
    ADD CONSTRAINT maintenance_request_timestamp_requires_assignee
    CHECK (assigned_at IS NULL OR assigned_technician_id IS NOT NULL);

CREATE FUNCTION facilityflow.require_new_assignment_timestamp()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.assigned_technician_id IS NULL OR NEW.assigned_at IS NOT NULL THEN
        RETURN NEW;
    END IF;
    IF TG_OP = 'UPDATE' THEN
        IF NEW.assigned_technician_id IS NOT DISTINCT FROM OLD.assigned_technician_id
            AND OLD.assigned_at IS NULL
            AND NOT (NEW.status = 'ASSIGNED' AND OLD.status <> 'ASSIGNED') THEN
            RETURN NEW;
        END IF;
    END IF;
    RAISE EXCEPTION 'A new assignment requires an assignment timestamp'
        USING ERRCODE = '23514', CONSTRAINT = 'maintenance_request_new_assignment_timestamp';
END;
$$;

CREATE TRIGGER maintenance_request_assignment_timestamp_guard
    BEFORE INSERT OR UPDATE ON facilityflow.maintenance_request
    FOR EACH ROW EXECUTE FUNCTION facilityflow.require_new_assignment_timestamp();

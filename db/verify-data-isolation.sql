-- Run as the non-owner application role; table owners and BYPASSRLS roles bypass RLS unless FORCE applies.
BEGIN;
SELECT set_config('app.current_tenant', 'tenant-a', true);
SELECT * FROM service_request; -- only tenant-a rows
INSERT INTO service_request(id,tenant_id,request_no,title,status,created_at)
VALUES (gen_random_uuid(),'tenant-b','RLS-DENIED','must fail','OPEN',now()); -- rejected by WITH CHECK
ROLLBACK;

BEGIN;
SELECT set_config('app.current_tenant', 'tenant-b', true);
SELECT * FROM service_request; -- only tenant-b rows
ROLLBACK;

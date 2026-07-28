BEGIN;
SELECT set_config('app.current_tenant', 'tenant-a', true);
DELETE FROM service_request;
SELECT set_config('app.current_tenant', 'tenant-b', true);
DELETE FROM service_request;
COMMIT;

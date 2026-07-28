-- Run this psql script as a local PostgreSQL administrator (for example, postgres).
-- The role, database, and password below are demo-only defaults from application.yaml.
SELECT format(
    'CREATE ROLE %I LOGIN PASSWORD %L',
    'saas_sample_app',
    'local-demo-password'
)
WHERE NOT EXISTS (
    SELECT 1 FROM pg_roles WHERE rolname = 'saas_sample_app'
) \gexec

SELECT format(
    'CREATE DATABASE %I OWNER %I',
    'saas_sample',
    'saas_sample_app'
)
WHERE NOT EXISTS (
    SELECT 1 FROM pg_database WHERE datname = 'saas_sample'
) \gexec

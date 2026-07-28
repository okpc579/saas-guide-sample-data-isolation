CREATE TABLE service_request (
  id UUID PRIMARY KEY, tenant_id VARCHAR(64) NOT NULL, request_no VARCHAR(64) NOT NULL,
  title VARCHAR(200) NOT NULL, status VARCHAR(20) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT uk_service_request_tenant_no UNIQUE (tenant_id, request_no)
);
CREATE INDEX ix_service_request_tenant_created ON service_request (tenant_id, created_at);

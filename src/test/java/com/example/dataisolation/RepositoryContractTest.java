package com.example.dataisolation;
import static org.assertj.core.api.Assertions.assertThat;
import com.example.dataisolation.dataisolation.ServiceRequestRepository;
import java.lang.reflect.Method; import java.nio.file.Files; import java.nio.file.Path;
import org.junit.jupiter.api.Test;
class RepositoryContractTest {
    @Test void tenantScopedRepositoryMethodsAreExplicit() {
        assertThat(ServiceRequestRepository.class.getDeclaredMethods()).extracting(Method::getName)
            .contains("findAllByTenantIdOrderByCreatedAt", "findByTenantIdAndId", "deleteByTenantIdAndId");
    }
    @Test void postgresMigrationContainsRlsPolicy() throws Exception {
        String sql=Files.readString(Path.of("src/main/resources/db/migration/postgresql/V1__create_service_request_with_rls.sql"));
        assertThat(sql).contains("tenant_id", "UNIQUE (tenant_id, request_no)", "ENABLE ROW LEVEL SECURITY", "FORCE ROW LEVEL SECURITY", "current_setting('app.current_tenant', true)", "WITH CHECK");
    }
}

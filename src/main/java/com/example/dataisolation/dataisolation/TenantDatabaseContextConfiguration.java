package com.example.dataisolation.dataisolation;

import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class TenantDatabaseContextConfiguration {
    @Bean @ConditionalOnProperty(name="demo.rls.enabled", havingValue="true")
    TenantDatabaseContext postgresContext(EntityManager entityManager) {
        return tenantId -> entityManager.createNativeQuery("select set_config('app.current_tenant', :tenantId, true)").setParameter("tenantId", tenantId).getSingleResult();
    }
    @Bean @ConditionalOnProperty(name="demo.rls.enabled", havingValue="false", matchIfMissing=true)
    TenantDatabaseContext noOpContext() { return tenantId -> { }; }
}

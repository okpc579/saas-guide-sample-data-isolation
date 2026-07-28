package com.example.dataisolation.dataisolation;
import java.util.List; import java.util.Optional; import java.util.UUID; import org.springframework.data.jpa.repository.JpaRepository;
public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, UUID> {
    List<ServiceRequest> findAllByTenantIdOrderByCreatedAt(String tenantId);
    Optional<ServiceRequest> findByTenantIdAndId(String tenantId, UUID id);
    long deleteByTenantIdAndId(String tenantId, UUID id);
}

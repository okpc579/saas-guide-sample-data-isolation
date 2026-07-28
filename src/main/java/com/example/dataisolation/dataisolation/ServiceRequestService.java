package com.example.dataisolation.dataisolation;

import com.example.dataisolation.api.ResourceNotFoundException;
import com.example.dataisolation.tenant.TenantContextHolder;
import java.util.List; import java.util.UUID;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;

@Service
public class ServiceRequestService {
    private final ServiceRequestRepository repository; private final TenantDatabaseContext databaseContext;
    public ServiceRequestService(ServiceRequestRepository repository, TenantDatabaseContext databaseContext){this.repository=repository;this.databaseContext=databaseContext;}
    @Transactional(readOnly=true) public List<ServiceRequest> list(){String tenant=tenant(); return repository.findAllByTenantIdOrderByCreatedAt(tenant);}
    @Transactional(readOnly=true) public ServiceRequest get(UUID id){String tenant=tenant(); return repository.findByTenantIdAndId(tenant,id).orElseThrow(ResourceNotFoundException::new);}
    @Transactional public ServiceRequest create(String no,String title){String tenant=tenant(); return repository.saveAndFlush(new ServiceRequest(tenant,no,title));}
    @Transactional public ServiceRequest update(UUID id,String title,ServiceRequest.Status status){String tenant=tenant(); ServiceRequest value=repository.findByTenantIdAndId(tenant,id).orElseThrow(ResourceNotFoundException::new); value.revise(title,status); return value;}
    @Transactional public void delete(UUID id){String tenant=tenant(); if(repository.deleteByTenantIdAndId(tenant,id)==0) throw new ResourceNotFoundException();}
    private String tenant(){String tenant=TenantContextHolder.require().tenantId(); databaseContext.apply(tenant); return tenant;}
}

package com.example.dataisolation.dataisolation;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="service_request", uniqueConstraints=@UniqueConstraint(name="uk_service_request_tenant_no", columnNames={"tenant_id","request_no"}))
public class ServiceRequest {
    @Id private UUID id;
    @Column(name="tenant_id", nullable=false, updatable=false) private String tenantId;
    @Column(name="request_no", nullable=false, updatable=false) private String requestNo;
    @Column(nullable=false) private String title;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private Status status;
    @Column(name="created_at", nullable=false, updatable=false) private Instant createdAt;
    protected ServiceRequest() { }
    ServiceRequest(String tenantId, String requestNo, String title) { this.id=UUID.randomUUID(); this.tenantId=tenantId; this.requestNo=requestNo; this.title=title; this.status=Status.OPEN; this.createdAt=Instant.now(); }
    void revise(String title, Status status) { this.title=title; this.status=status; }
    public UUID getId(){return id;} public String getTenantId(){return tenantId;} public String getRequestNo(){return requestNo;} public String getTitle(){return title;} public Status getStatus(){return status;} public Instant getCreatedAt(){return createdAt;}
    public enum Status { OPEN, IN_PROGRESS, CLOSED }
}

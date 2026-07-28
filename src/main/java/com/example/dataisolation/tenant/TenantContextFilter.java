package com.example.dataisolation.tenant;

import com.example.dataisolation.api.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TenantContextFilter extends OncePerRequestFilter {
    private final ObjectMapper objectMapper;
    public TenantContextFilter(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String tenantId = request.getHeader("X-Tenant-Id");
        String userId = request.getHeader("X-User-Id");
        if (tenantId == null || tenantId.isBlank()) { writeError(response, "TENANT_HEADER_REQUIRED", "X-Tenant-Id header is required"); return; }
        if (userId == null || userId.isBlank()) { writeError(response, "USER_HEADER_REQUIRED", "X-User-Id header is required"); return; }
        try { TenantContextHolder.set(new TenantContext(tenantId, userId)); chain.doFilter(request, response); }
        finally { TenantContextHolder.clear(); }
    }
    private void writeError(HttpServletResponse response, String code, String message) throws IOException {
        response.setStatus(400); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ApiError(code, message, Instant.now()));
    }
}

package com.example.dataisolation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.dataisolation.dataisolation.ServiceRequestRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach; import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired; import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest; import org.springframework.http.MediaType; import org.springframework.test.context.ActiveProfiles; import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class ServiceRequestIsolationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper mapper; @Autowired ServiceRequestRepository repository;
    @BeforeEach void clean(){repository.deleteAll();}
    @Test void listOnlyReturnsCurrentTenantAndCreateUsesServerContext() throws Exception {
        create("tenant-a","SHARED","A"); create("tenant-b","SHARED","B");
        mvc.perform(get(url()).headers(headers("tenant-a"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].tenantId").value("tenant-a"));
        assertThat(repository.findAll()).extracting("tenantId").containsExactlyInAnyOrder("tenant-a","tenant-b");
    }
    @Test void foreignIdIs404ForReadUpdateAndDelete() throws Exception {
        String id=create("tenant-b","B-1","secret");
        mvc.perform(get(url()+"/"+id).headers(headers("tenant-a"))).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mvc.perform(put(url()+"/"+id).headers(headers("tenant-a")).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"attack\",\"status\":\"CLOSED\"}" )).andExpect(status().isNotFound());
        mvc.perform(delete(url()+"/"+id).headers(headers("tenant-a"))).andExpect(status().isNotFound());
        mvc.perform(get(url()+"/"+id).headers(headers("tenant-b"))).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("secret"));
    }
    @Test void duplicateNumberConflictsOnlyInsideSameTenant() throws Exception {
        create("tenant-a","DUP","one"); create("tenant-b","DUP","two");
        mvc.perform(post(url()).headers(headers("tenant-a")).contentType(MediaType.APPLICATION_JSON).content("{\"requestNo\":\"DUP\",\"title\":\"again\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_REQUEST_NO"));
    }
    @Test void bodyTenantIdIsRejectedAndHeadersAreRequired() throws Exception {
        mvc.perform(post(url()).headers(headers("tenant-a")).contentType(MediaType.APPLICATION_JSON).content("{\"requestNo\":\"X\",\"title\":\"x\",\"tenantId\":\"tenant-b\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get(url()).header("X-User-Id","user-a")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TENANT_HEADER_REQUIRED"));
    }
    private String create(String tenant,String no,String title) throws Exception {String body=mvc.perform(post(url()).headers(headers(tenant)).contentType(MediaType.APPLICATION_JSON).content("{\"requestNo\":\""+no+"\",\"title\":\""+title+"\"}" )).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(); JsonNode json=mapper.readTree(body); assertThat(json.get("tenantId").asText()).isEqualTo(tenant); return json.get("id").asText();}
    private org.springframework.http.HttpHeaders headers(String tenant){var h=new org.springframework.http.HttpHeaders();h.add("X-Tenant-Id",tenant);h.add("X-User-Id","user-a");return h;}
    private String url(){return "/api/data-isolation/service-requests";}
}

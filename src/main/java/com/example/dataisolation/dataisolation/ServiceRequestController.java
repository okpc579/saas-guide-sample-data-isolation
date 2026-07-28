package com.example.dataisolation.dataisolation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.URI; import java.util.List; import java.util.UUID;
import org.springframework.http.ResponseEntity; import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/data-isolation/service-requests")
public class ServiceRequestController {
    private final ServiceRequestService service; public ServiceRequestController(ServiceRequestService service){this.service=service;}
    @GetMapping public List<Response> list(){return service.list().stream().map(Response::of).toList();}
    @GetMapping("/{id}") public Response get(@PathVariable UUID id){return Response.of(service.get(id));}
    @PostMapping public ResponseEntity<Response> create(@RequestBody CreateRequest request){ServiceRequest value=service.create(request.requestNo(),request.title()); return ResponseEntity.created(URI.create("/api/data-isolation/service-requests/"+value.getId())).body(Response.of(value));}
    @PutMapping("/{id}") public Response update(@PathVariable UUID id,@RequestBody UpdateRequest request){return Response.of(service.update(id,request.title(),request.status()));}
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable UUID id){service.delete(id);return ResponseEntity.noContent().build();}
    @JsonIgnoreProperties(ignoreUnknown=false) public record CreateRequest(String requestNo,String title){}
    @JsonIgnoreProperties(ignoreUnknown=false) public record UpdateRequest(String title,ServiceRequest.Status status){}
    public record Response(UUID id,String tenantId,String requestNo,String title,ServiceRequest.Status status,java.time.Instant createdAt){static Response of(ServiceRequest v){return new Response(v.getId(),v.getTenantId(),v.getRequestNo(),v.getTitle(),v.getStatus(),v.getCreatedAt());}}
}

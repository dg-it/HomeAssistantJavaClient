package io.dgit.haclient.app.web;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/ha-instances/health")
@RequiredArgsConstructor
@Slf4j
public class InstancesHealthController {

    private final WebClient webClient;

    private HashMap<String, String> simpleStatusMappings;

    @PostConstruct
    public void afterPropertiesSet() {
        simpleStatusMappings = new HashMap<>();
        simpleStatusMappings.put("API running.", "UP");
    }

    @GetMapping
    public ResponseEntity<Map<String,Object>> getHealth() {

        String resourceUri = "http://localhost:8123/api/";

        JsonNode body = webClient
                .get()
                .uri(resourceUri)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        //response expected {"message":"API running."}
        String status = Optional.ofNullable(body.get("message"))
                .map(JsonNode::asText)
                .map(str -> {
                    log.debug("Received message for /api/ endpoint: [{}]", str);
                    return this.simpleStatusMappings.get(str);})
                .orElse("UNKNOWN");
        log.debug("Resolved status [{}] for /api/,", status);
        return ResponseEntity.ok(Map.of("status", status));
    }
}

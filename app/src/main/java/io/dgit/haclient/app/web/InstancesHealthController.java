package io.dgit.haclient.app.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

@RestController
@RequestMapping("/ha-instances/health")
@RequiredArgsConstructor
public class InstancesHealthController {

    private final WebClient webClient;

    //TODO
    @GetMapping
    public ResponseEntity getHealth() {
        return ResponseEntity.ok("Health is OK");
    }

    @GetMapping("/test")
    public Object index() {
//    public Object index() {
        String resourceUri = "http://localhost:8123/api/";

        Object body = webClient
                .get()
                .uri(resourceUri)
//                .attributes(oauth2AuthorizedClient(authorizedClient))
                .retrieve()
                .bodyToMono(Object.class)
                .block();

        return body;
    }
}

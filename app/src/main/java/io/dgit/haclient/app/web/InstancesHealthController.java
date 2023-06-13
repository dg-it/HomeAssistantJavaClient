package io.dgit.haclient.app.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ha-instances/health")
public class InstancesHealthController {

    //TODO
    @GetMapping
    public ResponseEntity getHealth() {
        return ResponseEntity.ok("Health is OK");
    }
}

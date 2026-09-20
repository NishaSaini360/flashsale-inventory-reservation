package com.flashsale.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.flashsale.inventory", "com.flashsale.commons"})
@EnableScheduling
public class InventoryServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}

// TEMPORARY — delete in Phase 14 once seed.sh exists.
@org.springframework.web.bind.annotation.RestController
class TempTokenController {
    private final com.flashsale.commons.security.TokenFactory tf;
    TempTokenController(com.flashsale.commons.security.TokenFactory tf) { this.tf = tf; }

    @org.springframework.web.bind.annotation.GetMapping("/actuator/devtoken")
    public String token(@org.springframework.web.bind.annotation.RequestParam String tenant,
                        @org.springframework.web.bind.annotation.RequestParam(defaultValue = "USER") String role) {
        return tf.token("dev-user", tenant, java.util.List.of(role), java.time.Duration.ofHours(8));
    }
}

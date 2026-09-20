package com.flashsale.order.client;

import com.flashsale.commons.error.DomainException;
import com.flashsale.commons.error.Problems;
import com.flashsale.commons.security.TokenFactory;
import com.flashsale.commons.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Component
public class InventoryClient {

    private static final Logger log = LoggerFactory.getLogger(InventoryClient.class);

    private final RestClient client;
    private final TokenFactory tokens;

    public InventoryClient(@Value("${app.inventory.base-url}") String baseUrl,
                           @Value("${app.http.connect-timeout-ms}") long connectMs,
                           @Value("${app.http.read-timeout-ms}") long readMs,
                           TokenFactory tokens) {
        this.tokens = tokens;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectMs));
        factory.setReadTimeout(Duration.ofMillis(readMs));
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    public void commit(String allocationRef) {
        client.post()
              .uri("/internal/v1/allocations/{ref}/commit", allocationRef)
              .header("Authorization", "Bearer " + token())
              .retrieve()
              .toBodilessEntity();
    }

    /** Compensation path: try to re-acquire stock for a late-but-successful payment. */
    public boolean tryAllocate(String allocationRef, String sku, int qty, Instant expiresAt) {
        try {
            client.post()
                  .uri("/internal/v1/allocations")
                  .header("Authorization", "Bearer " + token())
                  .body(Map.of("allocationRef", allocationRef, "sku", sku,
                               "qty", qty, "expiresAt", expiresAt))
                  .retrieve()
                  .toBodilessEntity();
            return true;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode().value() == 409) return false;   // no stock left
            throw Problems.invalidState("Inventory re-allocate failed: " + e.getStatusCode());
        } catch (DomainException e) {
            throw e;
        } catch (Exception e) {
            log.error("Inventory re-allocate error ref={}", allocationRef, e);
            throw Problems.upstreamUnavailable("inventory-service");
        }
    }

    private String token() { return tokens.serviceToken(TenantContext.require()); }
}

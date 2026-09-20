package com.flashsale.reservation.client;

import com.flashsale.commons.error.Problems;
import com.flashsale.commons.security.TokenFactory;
import com.flashsale.commons.tenant.TenantContext;
import com.flashsale.commons.web.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Component
public class InventoryClient {

    private static final Logger log = LoggerFactory.getLogger(InventoryClient.class);

    private final RestClient client;
    private final TokenFactory tokens;

    public InventoryClient(@Value("${app.inventory.base-url}") String baseUrl,
                           @Value("${app.inventory.connect-timeout-ms}") long connectMs,
                           @Value("${app.inventory.read-timeout-ms}") long readMs,
                           TokenFactory tokens) {
        this.tokens = tokens;

        // Connect timeout on the JDK client, read timeout on the Spring factory.
        // Without both, a hung inventory-service would pin this thread indefinitely
        // and the brief's "timeout on the inventory HTTP call" would not be met.
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectMs))
                .build();

        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofMillis(readMs));

        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    /**
     * Retry on timeout is SAFE here, and only here, because inventory keys allocation
     * on allocationRef. If the first call actually landed but we never saw the response,
     * the retry returns the existing allocation instead of allocating twice.
     */
    public AllocationView allocate(String allocationRef, String sku, int qty, Instant expiresAt) {
        try {
            return client.post()
                    .uri("/internal/v1/allocations")
                    .header("Authorization", "Bearer " + serviceToken())
                    .header(CorrelationIdFilter.HEADER, correlationId())
                    .body(Map.of("allocationRef", allocationRef, "sku", sku,
                                 "qty", qty, "expiresAt", expiresAt))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        if (res.getStatusCode().value() == 409) {
                            throw Problems.insufficientStock(sku, qty, 0);
                        }
                        if (res.getStatusCode().value() == 404) {
                            throw Problems.notFound("Product", sku);
                        }
                        throw Problems.invalidState("Inventory rejected allocate: " + res.getStatusCode());
                    })
                    .body(AllocationView.class);
        } catch (com.flashsale.commons.error.DomainException e) {
            throw e;
        } catch (Exception e) {
            log.error("Inventory allocate failed ref={}", allocationRef, e);
            throw Problems.upstreamUnavailable("inventory-service");
        }
    }

    /** Idempotent at inventory — safe to call repeatedly. */
    public void release(String allocationRef) {
        client.post()
              .uri("/internal/v1/allocations/{ref}/release", allocationRef)
              .header("Authorization", "Bearer " + serviceToken())
              .header(CorrelationIdFilter.HEADER, correlationId())
              .retrieve()
              .toBodilessEntity();
    }

    /** MDC is empty on scheduler/worker threads; a null header value throws. */
    private String correlationId() {
        String cid = MDC.get("correlationId");
        return cid != null ? cid : java.util.UUID.randomUUID().toString();
    }

    private String serviceToken() {
        return tokens.serviceToken(TenantContext.require());
    }

    public record AllocationView(String id, String allocationRef, String sku,
                                 int qty, String status, Instant expiresAt) {}
}

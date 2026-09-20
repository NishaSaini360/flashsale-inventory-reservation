package com.flashsale.order.client;

import com.flashsale.commons.error.DomainException;
import com.flashsale.commons.error.Problems;
import com.flashsale.commons.security.TokenFactory;
import com.flashsale.commons.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Component
public class ReservationClient {

    private final RestClient client;
    private final TokenFactory tokens;

    public ReservationClient(@Value("${app.reservation.base-url}") String baseUrl,
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

    public ReservationView get(UUID id) {
        try {
            return client.get()
                    .uri("/internal/v1/reservations/{id}", id)
                    .header("Authorization", "Bearer " + token())
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (rq, rs) -> {
                        throw Problems.notFound("Reservation", id);
                    })
                    .body(ReservationView.class);
        } catch (DomainException e) {
            throw e;
        } catch (Exception e) {
            throw Problems.upstreamUnavailable("reservation-service");
        }
    }

    /** Returns false when the reservation has expired — the late-payment signal. */
    public boolean confirm(UUID id) {
        try {
            client.post()
                  .uri("/internal/v1/reservations/{id}/confirm", id)
                  .header("Authorization", "Bearer " + token())
                  .retrieve()
                  .toBodilessEntity();
            return true;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode().value() == 409) return false;
            throw Problems.invalidState("Reservation confirm failed: " + e.getStatusCode());
        } catch (Exception e) {
            throw Problems.upstreamUnavailable("reservation-service");
        }
    }

    public void cancel(UUID id) {
        client.post()
              .uri("/internal/v1/reservations/{id}/cancel", id)
              .header("Authorization", "Bearer " + token())
              .retrieve()
              .toBodilessEntity();
    }

    private String token() { return tokens.serviceToken(TenantContext.require()); }

    public record ReservationView(UUID reservationId, String sku, int qty,
                                  String status, Instant expiresAt, String allocationRef) {}
}

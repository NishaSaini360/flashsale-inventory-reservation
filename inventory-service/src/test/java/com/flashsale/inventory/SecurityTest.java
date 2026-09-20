package com.flashsale.inventory;

import com.flashsale.commons.security.TokenFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityTest extends AbstractIntegrationTest {

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired TokenFactory tokens;

    private String token(String tenant, String role) {
        return tokens.token("t", tenant, List.of(role), Duration.ofMinutes(5));
    }

    private HttpHeaders auth(String jwt) {
        var h = new HttpHeaders();
        h.setBearerAuth(jwt);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private String url(String path) { return "http://localhost:" + port + path; }

    @Test
    void no_token_is_401() {
        var res = rest.getForEntity(url("/api/v1/stock/ANY"), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void user_cannot_reach_admin_endpoints() {
        var body = new HttpEntity<>(Map.of("sku", "X", "name", "X"), auth(token("t1", "USER")));
        var res = rest.postForEntity(url("/api/v1/admin/products"), body, String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    /**
     * The one that matters: a USER must not be able to bypass reservation-service
     * and grab stock straight from inventory.
     */
    @Test
    void user_cannot_reach_internal_allocate() {
        var body = new HttpEntity<>(
                Map.of("allocationRef", "hack", "sku", "X", "qty", 1),
                auth(token("t1", "USER")));
        var res = rest.postForEntity(url("/internal/v1/allocations"), body, String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void admin_cannot_reach_internal_allocate_either() {
        var body = new HttpEntity<>(
                Map.of("allocationRef", "hack2", "sku", "X", "qty", 1),
                auth(token("t1", "ADMIN")));
        var res = rest.postForEntity(url("/internal/v1/allocations"), body, String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void actuator_is_open() {
        var res = rest.getForEntity(url("/actuator/health"), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void errors_use_problem_detail_shape() {
        var res = rest.exchange(url("/api/v1/stock/NOPE"), HttpMethod.GET,
                new HttpEntity<>(auth(token("t1", "USER"))), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getBody()).contains("\"type\"").contains("\"status\"").contains("\"detail\"");
    }
}

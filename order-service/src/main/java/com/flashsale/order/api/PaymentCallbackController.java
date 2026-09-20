package com.flashsale.order.api;

import com.flashsale.commons.tenant.TenantContext;
import com.flashsale.order.api.dto.Dtos.PaymentCallbackRequest;
import com.flashsale.order.payment.PaymentOutcome;
import com.flashsale.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * ROLE_SERVICE only. The in-process fake adapter calls the handler directly; this
 * endpoint exists so the callback path is externally exercisable (and testable).
 */
@RestController
@RequestMapping("/internal/v1/payments")
public class PaymentCallbackController {

    private final OrderService service;

    public PaymentCallbackController(OrderService service) { this.service = service; }

    @PostMapping("/callback")
    public ResponseEntity<Void> callback(@Valid @RequestBody PaymentCallbackRequest req) {
        service.handleCallback(req.externalRef(), TenantContext.require(),
                PaymentOutcome.valueOf(req.outcome()));
        return ResponseEntity.ok().build();
    }
}

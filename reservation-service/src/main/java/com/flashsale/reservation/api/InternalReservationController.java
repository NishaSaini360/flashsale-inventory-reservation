package com.flashsale.reservation.api;

import com.flashsale.reservation.api.dto.Dtos.ReservationView;
import com.flashsale.reservation.service.ReservationService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** ROLE_SERVICE only. order-service calls these. */
@RestController
@RequestMapping("/internal/v1/reservations")
public class InternalReservationController {

    private final ReservationService service;

    public InternalReservationController(ReservationService service) { this.service = service; }

    @GetMapping("/{id}")
    public ReservationView get(@PathVariable UUID id) {
        return ReservationController.view(service.get(id));
    }

    @PostMapping("/{id}/confirm")
    public ReservationView confirm(@PathVariable UUID id) {
        return ReservationController.view(service.confirm(id));
    }

    @PostMapping("/{id}/cancel")
    public ReservationView cancel(@PathVariable UUID id) {
        return ReservationController.view(service.cancel(id));
    }
}

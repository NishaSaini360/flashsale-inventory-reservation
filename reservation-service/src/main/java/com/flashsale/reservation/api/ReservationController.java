package com.flashsale.reservation.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.reservation.api.dto.Dtos.*;
import com.flashsale.reservation.domain.Reservation;
import com.flashsale.reservation.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    private final ReservationService service;
    private final ObjectMapper mapper;

    public ReservationController(ReservationService service, ObjectMapper mapper) {
        this.service = service; this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<?> reserve(@Valid @RequestBody CreateReservationRequest req,
                                     @RequestHeader(value = "Idempotency-Key", required = false) String idemKey)
            throws Exception {
        String key = (idemKey == null || idemKey.isBlank()) ? UUID.randomUUID().toString() : idemKey;
        String rawBody = mapper.writeValueAsString(req);

        var result = service.reserve(req.sku(), req.qty(), key, rawBody);

        if (result.isReplay()) {
            return ResponseEntity.status(result.replayStatus())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(result.replayBody());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(view(result.reservation()));
    }

    @GetMapping("/{id}")
    public ReservationView get(@PathVariable UUID id) { return view(service.get(id)); }

    static ReservationView view(Reservation r) {
        return new ReservationView(r.getId(), r.getSku(), r.getQty(),
                r.getStatus().name(), r.getExpiresAt(), r.getAllocationRef());
    }
}

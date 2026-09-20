package com.flashsale.inventory.api;

import com.flashsale.inventory.api.dto.Dtos.*;
import com.flashsale.inventory.domain.Allocation;
import com.flashsale.inventory.service.AllocationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** ROLE_SERVICE only — enforced in commons SecurityConfig. A USER token gets 403 here. */
@RestController
@RequestMapping("/internal/v1/allocations")
public class InternalAllocationController {

    private final AllocationService service;

    public InternalAllocationController(AllocationService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<AllocationView> allocate(@Valid @RequestBody AllocateRequest req) {
        Allocation a = service.allocate(req.allocationRef(), req.sku(), req.qty(), req.expiresAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(view(a));
    }

    @PostMapping("/{ref}/release")
    public AllocationView release(@PathVariable String ref) { return view(service.release(ref)); }

    @PostMapping("/{ref}/commit")
    public AllocationView commit(@PathVariable String ref) { return view(service.commit(ref)); }

    private AllocationView view(Allocation a) {
        return new AllocationView(a.getId(), a.getAllocationRef(), a.getSku(), a.getQty(),
                a.getStatus().name(), a.getExpiresAt());
    }
}

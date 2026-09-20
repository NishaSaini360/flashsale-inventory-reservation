package com.flashsale.inventory.api;

import com.flashsale.inventory.api.dto.Dtos.*;
import com.flashsale.inventory.domain.StockItem;
import com.flashsale.inventory.service.CatalogService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final CatalogService catalog;

    public AdminController(CatalogService catalog) { this.catalog = catalog; }

    @PostMapping("/products")
    public ResponseEntity<Map<String, Object>> createProduct(@Valid @RequestBody CreateProductRequest req) {
        var p = catalog.createProduct(req.sku(), req.name());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("id", p.getId(), "sku", p.getSku(), "name", p.getName()));
    }

    @PostMapping("/stock")
    public ResponseEntity<StockView> addStock(@Valid @RequestBody AddStockRequest req) {
        StockItem s = catalog.addStock(req.sku(), req.warehouseId(), req.qty());
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new StockView(req.sku(), s.getWarehouseId(), s.getOnHand(), s.getReserved(), s.getAvailable()));
    }
}

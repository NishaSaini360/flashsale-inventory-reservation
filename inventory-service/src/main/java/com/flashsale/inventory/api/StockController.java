package com.flashsale.inventory.api;

import com.flashsale.inventory.api.dto.Dtos.*;
import com.flashsale.inventory.domain.StockItem;
import com.flashsale.inventory.service.CatalogService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stock")
public class StockController {

    private final CatalogService catalog;

    public StockController(CatalogService catalog) { this.catalog = catalog; }

    @GetMapping("/{sku}")
    public SkuStockView get(@PathVariable String sku) {
        List<StockItem> items = catalog.stockFor(sku);
        var warehouses = items.stream()
                .map(s -> new StockView(sku, s.getWarehouseId(), s.getOnHand(), s.getReserved(), s.getAvailable()))
                .toList();
        return new SkuStockView(sku,
                items.stream().mapToInt(StockItem::getOnHand).sum(),
                items.stream().mapToInt(StockItem::getReserved).sum(),
                items.stream().mapToInt(StockItem::getAvailable).sum(),
                warehouses);
    }
}

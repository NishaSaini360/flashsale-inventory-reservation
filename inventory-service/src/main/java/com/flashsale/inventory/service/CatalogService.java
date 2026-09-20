package com.flashsale.inventory.service;

import com.flashsale.commons.error.Problems;
import com.flashsale.commons.tenant.TenantContext;
import com.flashsale.inventory.domain.Product;
import com.flashsale.inventory.domain.StockItem;
import com.flashsale.inventory.repo.ProductRepository;
import com.flashsale.inventory.repo.StockItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CatalogService {

    private final ProductRepository products;
    private final StockItemRepository stock;

    public CatalogService(ProductRepository products, StockItemRepository stock) {
        this.products = products; this.stock = stock;
    }

    @Transactional
    public Product createProduct(String sku, String name) {
        String tenantId = TenantContext.require();
        products.findByTenantIdAndSku(tenantId, sku).ifPresent(p -> {
            throw Problems.invalidState("SKU already exists: " + sku);
        });
        return products.save(new Product(sku, name));
    }

    @Transactional
    public StockItem addStock(String sku, String warehouseId, int qty) {
        String tenantId = TenantContext.require();
        Product product = products.findByTenantIdAndSku(tenantId, sku)
                .orElseThrow(() -> Problems.notFound("Product", sku));
        return stock.findByTenantIdAndProductIdOrderByWarehouseIdAsc(tenantId, product.getId()).stream()
                .filter(s -> s.getWarehouseId().equals(warehouseId))
                .findFirst()
                .map(existing -> {
                    stock.addOnHand(existing.getId(), tenantId, qty);
                    return stock.findById(existing.getId()).orElseThrow();
                })
                .orElseGet(() -> stock.save(new StockItem(product.getId(), warehouseId, qty)));
    }

    @Transactional(readOnly = true)
    public List<StockItem> stockFor(String sku) {
        String tenantId = TenantContext.require();
        Product product = products.findByTenantIdAndSku(tenantId, sku)
                .orElseThrow(() -> Problems.notFound("Product", sku));
        return stock.findByTenantIdAndProductIdOrderByWarehouseIdAsc(tenantId, product.getId());
    }
}

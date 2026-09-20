package com.flashsale.inventory.repo;

import com.flashsale.inventory.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    /** Tenant is explicit in the query, never implied by thread context. */
    Optional<Product> findByTenantIdAndSku(String tenantId, String sku);
}

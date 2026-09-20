package com.flashsale.inventory.repo;

import com.flashsale.inventory.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    Optional<Product> findBySku(String sku);   // tenant scoping comes from the Hibernate filter
}

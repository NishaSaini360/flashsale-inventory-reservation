package com.flashsale.inventory;

import org.springframework.boot.test.context.SpringBootTest;

/**
 * Runs against the Postgres from docker-compose (see README).
 * Each test uses a unique SKU and tenant, so runs do not interfere.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {
}

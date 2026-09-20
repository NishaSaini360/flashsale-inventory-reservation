package com.flashsale.reservation;

import org.springframework.boot.test.context.SpringBootTest;

/** Runs against docker-compose Postgres AND a running inventory-service on 8081. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {
}

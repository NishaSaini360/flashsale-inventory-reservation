package com.flashsale.order;

import org.springframework.boot.test.context.SpringBootTest;

/** Runs against the Postgres from docker-compose (see README). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {
}

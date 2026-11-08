package com.example.wallet;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
public abstract class AbstractIntegrationTest {

    private static class SharedPostgreSqlContainer extends PostgreSQLContainer<SharedPostgreSqlContainer> {
        SharedPostgreSqlContainer(String image) {
            super(image);
        }

        @Override
        public void stop() {
            // Keep container alive across test classes sharing the Spring context; Ryuk will clean up on JVM exit.
        }
    }

    @Container
    @ServiceConnection
    protected static final PostgreSQLContainer<?> postgres = new SharedPostgreSqlContainer("postgres:16-alpine")
            .withCommand("postgres", "-c", "max_connections=300");
}

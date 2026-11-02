package se.backend.labb2.helpdesk;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Spring Boot skips Docker Compose in tests by default. This test starts the full
// app, which needs the Postgres database from compose.yaml, so we turn it on here.
@SpringBootTest(properties = "spring.docker.compose.skip.in-tests=false")
class HelpdeskApplicationTests {

    @Test
    void contextLoads() {
    }

}

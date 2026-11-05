package se.backend.labb2.helpdesk;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.seed.enabled=false")
class HelpdeskApplicationTests {

    @Test
    void contextLoads() {
    }

}

package de.secretsoft.vatsim_stats;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest( properties = {
    "spring.datasource.url=jdbc:h2:mem:vatsim-stats-smoke-test",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false",
    "vatsim.scheduling.enabled=false"
} )
class VatsimStatsApplicationTests {

    @Test
    void contextLoads() {
    }
}

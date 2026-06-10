package com.fipe.valuation;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * M1 gate (T1.2/T1.4): the Spring context — including {@code FipeProperties}, the FIPE
 * {@code WebClient} and the CORS filter — wires up cleanly.
 */
@SpringBootTest
class FipeValuationApplicationTests {

    @Test
    void contextLoads() {
        // Passes iff all beans (properties, WebClient, CORS filter) are constructible.
    }
}

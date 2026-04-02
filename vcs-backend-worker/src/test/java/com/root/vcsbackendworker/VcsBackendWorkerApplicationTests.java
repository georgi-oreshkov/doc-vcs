package com.root.vcsbackendworker;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "app.worker.redis.listener-enabled=false")
class VcsBackendWorkerApplicationTests {

    @Test
    void contextLoads() {
    }

}

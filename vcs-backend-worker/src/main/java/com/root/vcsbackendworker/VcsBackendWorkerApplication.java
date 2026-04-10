package com.root.vcsbackendworker;

import com.root.vcsbackendworker.shared.config.WorkerRuntimeHints;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@ImportRuntimeHints(WorkerRuntimeHints.class)
public class VcsBackendWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(VcsBackendWorkerApplication.class, args);
    }

}

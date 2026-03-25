package com.root.vcsbackend.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing
public class JpaConfig {
    // TODO: register AuditorAware bean (extract userId from SecurityContext)
}


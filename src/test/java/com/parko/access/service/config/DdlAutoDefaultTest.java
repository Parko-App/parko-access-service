package com.parko.access.service.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DdlAutoDefaultTest {

    @Test
    void ddlAuto_defaultsToValidate_andCanBeOverriddenByEnvironment() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yaml"));

        Object ddlAuto = sources.get(0).getProperty("spring.jpa.hibernate.ddl-auto");

        assertThat(ddlAuto).isEqualTo("${HIBERNATE_DDL_AUTO:validate}");
    }
}

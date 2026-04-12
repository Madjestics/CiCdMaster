package com.example.cicdmaster.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI cicdMasterOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("CI/CD Master Service API")
                        .description("Управляющий сервис CI/CD: пайплайны, стадии, задачи, шаблоны и взаимодействие с executor-сервисами через Kafka")
                        .version("v1")
                        .license(new License().name("Internal")));
    }
}
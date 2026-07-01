package com.project.cqrs.config.swagger;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.*;
import io.swagger.v3.oas.models.security.*;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CQRS API")
                        .description("""
                                API do projeto CQRS com Spring Boot 3, Kafka, MySQL e Redis.
                                
                                **Autenticação:** Login via Google OAuth2.
                                Após o login, o JWT é armazenado automaticamente em cookie HttpOnly.
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Guilherme D'Antoni")
                                .url("https://github.com/dantonigui/cqrs-spring-boot"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url(baseUrl).description("Servidor atual"),
                        new Server().url("http://localhost:8080").description("Local")
                ))
                // Esquema de segurança via cookie JWT
                .addSecurityItem(new SecurityRequirement().addList("cookieAuth"))
                .components(new Components()
                        .addSecuritySchemes("cookieAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.COOKIE)
                                        .name("access_token")
                                        .description("JWT gerado após login OAuth2 com Google")));
    }
}
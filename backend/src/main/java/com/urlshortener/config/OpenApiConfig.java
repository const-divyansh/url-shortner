package com.urlshortener.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.HttpHeaders;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * OpenAPI metadata and the session-token security scheme.
 */
@Configuration
@PropertySource("classpath:openapi.properties")
public class OpenApiConfig {

    public static final String BEARER_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI openApi(org.springframework.core.env.Environment environment) {
        return new OpenAPI()
                .info(new Info()
                        .title(environment.getRequiredProperty("openapi.title"))
                        .description(environment.getRequiredProperty("openapi.description"))
                        .version(environment.getRequiredProperty("openapi.version")))
                .components(new Components().addSecuritySchemes(
                        BEARER_SCHEME_NAME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .in(SecurityScheme.In.HEADER)
                                .name(HttpHeaders.AUTHORIZATION)
                                .scheme("bearer")
                                .bearerFormat("opaque session token")
                                .description("Send the session token as Authorization: Bearer <token>.")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME));
    }
}

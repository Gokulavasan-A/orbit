package com.enterprise.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata. The docs endpoints are enabled in the dev profile only. Every
 * operation requires the bearer token unless it is annotated with an empty
 * {@code @SecurityRequirements} (csrf, login, refresh).
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI enterpriseOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Enterprise Base API")
                        .version("v1")
                        .description("All responses use the ApiResponse envelope: success, data, error, correlationId, timestamp. "
                                + "Sign in with POST /api/v1/auth/login and paste the accessToken into Authorize."))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}

package com.ecommerce.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * General API metadata plus a "bearerAuth" HTTP-bearer security scheme, referenced by
 * {@code @SecurityRequirement(name = "bearerAuth")} on every controller/operation that
 * requires a JWT -- this is what makes Swagger UI show an "Authorize" button that
 * actually attaches the token to try-it-out requests.
 */
@Configuration
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Paste the accessToken returned by POST /api/auth/login or POST /api/auth/refresh (without the 'Bearer ' prefix -- Swagger UI adds it)."
)
@OpenAPIDefinition
public class OpenApiConfig {

    @Bean
    public OpenAPI ecommerceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("E-commerce Backend API")
                        .description("A REST API for a small e-commerce backend: JWT-authenticated accounts "
                                + "(register/login/refresh/logout with revocable refresh tokens), a product "
                                + "catalog with soft delete and optimistic-locked stock, a per-user cart, and a "
                                + "checkout flow that charges through a pluggable PaymentGateway (a mock gateway "
                                + "by default, or real Stripe test-mode when explicitly configured) and writes a "
                                + "full inventory audit trail. Built as a portfolio project demonstrating Spring "
                                + "Security + JWT, optimistic locking, and a real third-party payment integration "
                                + "behind a clean abstraction.")
                        .version("v1.0")
                        .contact(new Contact().name("Arpit Kumar").email("arpit23111999@gmail.com"))
                        .license(new License().name("MIT")));
    }
}

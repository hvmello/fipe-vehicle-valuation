package com.fipe.valuation.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI metadata for the Swagger UI (served at /swagger-ui.html). */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI fipeValuationOpenApi() {
        return new OpenAPI().info(new Info()
                .title("FIPE Vehicle Valuation API")
                .version("v1")
                .description("""
                        Returns a vehicle's price across its manufacture years, with the absolute and \
                        percentage variation versus the previous available year (newest first). \
                        Consumes the FIPE v2 API.""")
                .contact(new Contact().name("FIPE Valuation"))
                .license(new License().name("MIT")));
    }
}

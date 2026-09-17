package com.erpschool.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        SecurityScheme bearer = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");
        return new OpenAPI()
                .info(new Info()
                        .title("ERP School API")
                        .version("0.2.0")
                        .description("""
                                Multi-tenant School ERP SaaS — database + APIs.
                                Auth, tenants, subscriptions, campuses, academics, students/parents,
                                lecture attendance, homework, offline exam results, fees, salaries,
                                announcements, calendar, reports. Tenant isolation is enforced in queries.
                                ERP Owner may send X-Tenant-Id to pin a school for school-scoped APIs.
                                """))
                .servers(List.of(new Server().url("/").description("Current host")))
                .components(new Components().addSecuritySchemes("bearerAuth", bearer))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}

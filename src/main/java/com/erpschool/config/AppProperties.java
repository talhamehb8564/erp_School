package com.erpschool.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    @NotNull
    private Jwt jwt = new Jwt();

    @NotNull
    private Cors cors = new Cors();

    @NotNull
    private Seed seed = new Seed();

    @Getter
    @Setter
    public static class Jwt {
        @NotBlank
        private String secret;
        private long accessTokenTtlSeconds = 900;
        private long refreshTokenTtlSeconds = 604800;
    }

    @Getter
    @Setter
    public static class Cors {
        private String allowedOrigins = "*";
    }

    @Getter
    @Setter
    public static class Seed {
        private boolean enabled = true;
        private String erpOwnerUsername = "erp.owner";
        private String erpOwnerPassword = "Owner@12345";
        private String erpOwnerEmail = "owner@erpschool.local";
        private String demoSchoolCode = "GVS";
        private String demoSchoolName = "Green Valley School";
        private String demoUserPassword = "ChangeMe@123";
    }
}

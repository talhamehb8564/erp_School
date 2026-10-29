package com.erpschool;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ErpSchoolApplication {

    public static void main(String[] args) {
        /* Must be set before the TCP stack initializes. EnvironmentPostProcessor is too late. */
        System.setProperty("java.net.preferIPv4Stack", "true");
        SpringApplication.run(ErpSchoolApplication.class, args);
    }
}

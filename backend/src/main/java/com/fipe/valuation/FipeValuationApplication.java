package com.fipe.valuation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the FIPE vehicle-valuation microservice (Spec 001).
 *
 * <p>Given a vehicle type, brand and model, the service consumes the FIPE v2 API and returns each
 * manufacture year's price plus the variation versus the previous available year.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class FipeValuationApplication {

    public static void main(String[] args) {
        SpringApplication.run(FipeValuationApplication.class, args);
    }
}

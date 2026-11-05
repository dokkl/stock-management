package com.stockmanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class StockManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockManagementApplication.class, args);
    }
}

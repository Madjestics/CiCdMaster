package com.example.cicdmaster;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class CicdMasterApplication {

    public static void main(String[] args) {
        SpringApplication.run(CicdMasterApplication.class, args);
    }
}

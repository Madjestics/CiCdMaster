package com.example.cicdbuild.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.build")
public class BuildServiceProperties {

    private String masterBaseUrl = "http://localhost:8080";
    private String workspaceRoot = "./workspace";
    private long commandTimeoutSeconds = 900;
}

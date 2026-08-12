package com.chilly.researchagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ResearchAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResearchAgentApplication.class, args);
    }
}

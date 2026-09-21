package com.settlegraph.Artifacts;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ArtifactsApplication {
    public static void main(String[] args) {
        SpringApplication.run(ArtifactsApplication.class, args);
    }
}

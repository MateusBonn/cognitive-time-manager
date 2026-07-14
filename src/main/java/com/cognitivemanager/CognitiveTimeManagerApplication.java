package com.cognitivemanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Cognitive Time Manager backend.
 *
 * Architecture: Hexagonal (Ports and Adapters) + Event-Driven Pipeline.
 * See README.md for the full architectural decision record.
 *
 * Start with:
 * <pre>
 *   RABBITMQ_HOST=localhost mvn spring-boot:run
 * </pre>
 *
 * Or with Docker Compose (see README):
 * <pre>
 *   docker-compose up
 * </pre>
 */
@SpringBootApplication
public class CognitiveTimeManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CognitiveTimeManagerApplication.class, args);
    }
}

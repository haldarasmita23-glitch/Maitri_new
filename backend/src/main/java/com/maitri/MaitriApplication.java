package com.maitri;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * Maitri Application — Main Entry Point
 *
 * This is the class that starts the entire Spring Boot application.
 *
 * @SpringBootApplication is a shortcut for three annotations:
 *   @Configuration      — This class can define Spring beans
 *   @EnableAutoConfiguration — Spring auto-configures based on dependencies
 *   @ComponentScan      — Scans this package and sub-packages for Spring components
 *
 * exclude = {UserDetailsServiceAutoConfiguration.class}:
 *   Prevents Spring Boot from auto-creating a default in-memory user
 *   (which would print a random password in logs and serve no purpose here).
 *   We define our own authentication in Phase 3 using JWT.
 *
 * How Spring Boot starts:
 *   1. main() is called by the JVM
 *   2. SpringApplication.run() starts the embedded Tomcat web server
 *   3. Spring scans all classes in com.maitri and sub-packages
 *   4. All @RestController, @Service, @Repository classes are registered
 *   5. The application is ready to receive HTTP requests on port 8080
 */
@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class})
public class MaitriApplication {

    public static void main(String[] args) {
        SpringApplication.run(MaitriApplication.class, args);
    }
}

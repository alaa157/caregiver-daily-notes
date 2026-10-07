package com.caregiver.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Backend entrypoint. Lives under {@code config/} per the #12 allowed paths;
 * the explicit scans cover the whole {@code com.caregiver} namespace.
 */
@SpringBootApplication(scanBasePackages = "com.caregiver")
@EntityScan("com.caregiver")
@EnableJpaRepositories("com.caregiver")
@EnableConfigurationProperties(AuthProperties.class)
public class CaregiverApplication {

  public static void main(String[] args) {
    SpringApplication.run(CaregiverApplication.class, args);
  }
}

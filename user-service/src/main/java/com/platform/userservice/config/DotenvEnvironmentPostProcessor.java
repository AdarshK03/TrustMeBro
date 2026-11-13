package com.platform.userservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Spring Boot EnvironmentPostProcessor that automatically discovers and loads
 * the project .env file during startup, injecting all variables into Spring's Environment.
 * 
 * Runs before application.yml placeholder resolution so ${USER_DB_URL}, etc.,
 * automatically resolve to values provided in .env across all launch modes
 * (terminal, IDE, PowerShell scripts, and jar execution).
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(DotenvEnvironmentPostProcessor.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        File envFile = findDotenvFile();
        if (envFile == null || !envFile.exists() || !envFile.isFile()) {
            log.debug("No .env file found during environment post-processing.");
            return;
        }

        Map<String, Object> envProperties = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(envFile, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eqIndex = line.indexOf('=');
                if (eqIndex > 0) {
                    String key = line.substring(0, eqIndex).trim();
                    String value = line.substring(eqIndex + 1).trim();

                    // Strip optional quotes
                    if ((value.startsWith("\"") && value.endsWith("\"")) ||
                        (value.startsWith("'") && value.endsWith("'"))) {
                        if (value.length() >= 2) {
                            value = value.substring(1, value.length() - 1);
                        }
                    }

                    envProperties.put(key, value);
                    // Also populate system property if not already set, for maximum library compatibility
                    if (System.getProperty(key) == null) {
                        System.setProperty(key, value);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed reading .env file from {}: {}", envFile.getAbsolutePath(), e.getMessage());
            return;
        }

        if (!envProperties.isEmpty()) {
            log.info("[Dotenv] Successfully loaded {} environment variables from {}", 
                    envProperties.size(), envFile.getAbsolutePath());

            // Insert dotenv property source with high priority so application.yml placeholders resolve from it
            MapPropertySource dotenvPropertySource = new MapPropertySource("dotenvProperties", envProperties);
            if (environment.getPropertySources().contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
                environment.getPropertySources().addAfter(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        dotenvPropertySource
                );
            } else {
                environment.getPropertySources().addFirst(dotenvPropertySource);
            }
        }
    }

    private File findDotenvFile() {
        // 1. Explicit DOTENV_FILE property or env
        String explicitPath = System.getProperty("DOTENV_FILE", System.getenv("DOTENV_FILE"));
        if (explicitPath != null && !explicitPath.isBlank()) {
            File f = new File(explicitPath);
            if (f.exists() && f.isFile()) {
                return f;
            }
        }

        // 2. Search upwards starting from current user.dir up to 4 directory levels
        File currentDir = new File(System.getProperty("user.dir", "."));
        for (int i = 0; i < 4 && currentDir != null; i++) {
            File candidate = new File(currentDir, ".env");
            if (candidate.exists() && candidate.isFile()) {
                return candidate;
            }
            currentDir = currentDir.getParentFile();
        }

        return null;
    }

    @Override
    public int getOrder() {
        // Runs with high priority, before ConfigDataEnvironmentPostProcessor loads application.yml
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }
}


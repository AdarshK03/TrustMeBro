package com.platform.gateway.config;

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
 * Spring Boot EnvironmentPostProcessor for Gateway to load project .env file.
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(DotenvEnvironmentPostProcessor.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        File envFile = findDotenvFile();
        if (envFile == null || !envFile.exists() || !envFile.isFile()) {
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

                    if ((value.startsWith("\"") && value.endsWith("\"")) ||
                        (value.startsWith("'") && value.endsWith("'"))) {
                        if (value.length() >= 2) {
                            value = value.substring(1, value.length() - 1);
                        }
                    }

                    envProperties.put(key, value);
                    if (System.getProperty(key) == null) {
                        System.setProperty(key, value);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed reading .env file: {}", e.getMessage());
            return;
        }

        if (!envProperties.isEmpty()) {
            log.info("[Dotenv] Loaded {} environment variables for Gateway from {}", 
                    envProperties.size(), envFile.getAbsolutePath());

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
        String explicitPath = System.getProperty("DOTENV_FILE", System.getenv("DOTENV_FILE"));
        if (explicitPath != null && !explicitPath.isBlank()) {
            File f = new File(explicitPath);
            if (f.exists() && f.isFile()) return f;
        }

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
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }
}


package com.platform.userservice.config;

import com.platform.userservice.model.User;
import com.platform.userservice.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Repeatable, idempotent seed data initializer for development and local testing.
 * Only executes when 'app.seed-data.enabled' or 'APP_SEED_DATA_ENABLED' is set to true.
 * Safely verifies non-existence before persisting to avoid creating duplicates or mutating existing data.
 */
@Component
public class UserDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(UserDataInitializer.class);

    private final UserRepository userRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Value("${app.seed-data.enabled:false}")
    private boolean seedDataEnabled;

    public UserDataInitializer(UserRepository userRepository,
                               org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (!seedDataEnabled) {
            log.info("Development seed data is disabled (app.seed-data.enabled=false). Skipping user initialization.");
            return;
        }

        log.info("Development seed data is enabled. Initializing default sample users if absent...");

        List<User> sampleUsers = List.of(
                new User("Alice Developer", "alice.dev@example.com", "ADMIN", passwordEncoder.encode("Admin@123")),
                new User("Bob Customer", "bob.cust@example.com", "CUSTOMER", passwordEncoder.encode("Customer@123")),
                new User("Charlie Support", "charlie.support@example.com", "SUPPORT", passwordEncoder.encode("Support@123"))
        );

        for (User sample : sampleUsers) {
            if (!userRepository.existsByEmail(sample.getEmail())) {
                userRepository.save(sample);
                log.info("Seeded user: {} ({})", sample.getName(), sample.getEmail());
            } else {
                log.debug("User already exists, skipping seed: {}", sample.getEmail());
            }
        }

        log.info("User seed data verification complete. Total users: {}", userRepository.count());
    }
}

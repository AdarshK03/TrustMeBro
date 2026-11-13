package com.platform.orderservice.client;

import com.platform.orderservice.exception.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Service
public class HttpUserServiceClient implements UserServiceClient {

    private static final Logger log = LoggerFactory.getLogger(HttpUserServiceClient.class);

    private final RestClient restClient;
    private final String userServiceUrl;

    public HttpUserServiceClient(RestClient.Builder restClientBuilder,
                                 @Value("${services.user.url:http://localhost:8081}") String userServiceUrl) {
        this.userServiceUrl = userServiceUrl.endsWith("/")
                ? userServiceUrl.substring(0, userServiceUrl.length() - 1)
                : userServiceUrl;
        this.restClient = restClientBuilder.baseUrl(this.userServiceUrl).build();
        log.info("Initialized HttpUserServiceClient targeting: {}", this.userServiceUrl);
    }

    @Override
    public boolean verifyUserExists(Long userId) {
        if (userId == null || userId <= 0) {
            return false;
        }

        try {
            log.debug("Calling User Service: GET {}/api/users/{}", userServiceUrl, userId);
            restClient.get()
                    .uri("/api/users/{id}", userId)
                    .retrieve()
                    .toBodilessEntity();

            return true;
        } catch (HttpClientErrorException.NotFound ex) {
            log.debug("User {} not found (404) in User Service", userId);
            return false;
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode().value() == 404) {
                return false;
            }
            log.warn("Client error verifying user {}: {} - {}", userId, ex.getStatusCode(), ex.getMessage());
            throw new IllegalArgumentException("User Service rejected request for user ID " + userId + ": " + ex.getStatusText());
        } catch (HttpServerErrorException ex) {
            log.error("User Service returned 5xx server error for user {}: {}", userId, ex.getStatusCode());
            throw new ServiceUnavailableException("User Service is temporarily unavailable. Please try again later.");
        } catch (ResourceAccessException ex) {
            log.error("Connection or timeout error reaching User Service at {} for user {}: {}", userServiceUrl, userId, ex.getMessage());
            throw new ServiceUnavailableException("User Service is currently unreachable. Please try again later.");
        } catch (Exception ex) {
            log.error("Unexpected error contacting User Service for user {}: {}", userId, ex.getMessage());
            throw new ServiceUnavailableException("Unable to contact User Service. Please try again later.");
        }
    }
}


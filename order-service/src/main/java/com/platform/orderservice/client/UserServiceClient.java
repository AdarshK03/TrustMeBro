package com.platform.orderservice.client;

/**
 * Interface contract defining inter-service communication with User Service.
 * Used during order creation to verify customer validity.
 */
public interface UserServiceClient {
    boolean verifyUserExists(Long userId);
}

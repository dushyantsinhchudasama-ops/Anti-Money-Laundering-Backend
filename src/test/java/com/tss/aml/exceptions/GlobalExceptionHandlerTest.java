package com.tss.aml.exceptions;

import com.tss.aml.exceptions.base.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("AuthenticationException returns HTTP 401 Unauthorized with message")
    void handleAuthenticationException_Returns401() {
        AuthenticationException ex = new BadCredentialsException("Invalid credentials provided");
        ResponseEntity<Map<String, String>> response = exceptionHandler.handleAuthentication(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("error", "Invalid credentials provided");
    }

    @Test
    @DisplayName("Generic Exception returns HTTP 500 Internal Server Error with safe generic error message")
    void handleGenericException_Returns500WithSafeMessage() {
        Exception ex = new RuntimeException("Sensitive database stack trace details");
        ResponseEntity<Map<String, String>> response = exceptionHandler.globalException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("error", "An internal server error occurred");
        assertThat(response.getBody().get("error")).doesNotContain("Sensitive database");
    }

    @Test
    @DisplayName("ResourceNotFoundException returns HTTP 404 Not Found")
    void handleResourceNotFoundException_Returns404() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Account not found");
        ResponseEntity<Map<String, String>> response = exceptionHandler.handleResourceNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("error", "Account not found");
    }

    @Test
    @DisplayName("AccessDeniedException returns HTTP 403 Forbidden")
    void handleAccessDeniedException_Returns403() {
        AccessDeniedException ex = new AccessDeniedException("Insufficient privileges");
        ResponseEntity<Map<String, String>> response = exceptionHandler.handleAccessDenied(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("error", "Access denied: Insufficient privileges");
    }
}

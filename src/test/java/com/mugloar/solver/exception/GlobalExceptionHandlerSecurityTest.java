// Fixie Security Regression Test
// Vulnerability: Catching generic Exception and swallowing errors silently
// This test proves the fix is effective.

package com.mugloar.solver.exception;

import com.mugloar.solver.exception.JobNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GlobalExceptionHandlerSecurityTest {

    @InjectMocks
    private GlobalExceptionHandler exceptionHandler;

    @Mock
    private JobNotFoundException jobNotFoundException;

    @Test
    void should_not_expose_exception_details_when_handling_generic_exception() {
        // Arrange
        String sensitiveMessage = "Sensitive error message";
        Exception exception = new Exception(sensitiveMessage);

        // Act
        ResponseEntity<Map<String, String>> response = exceptionHandler.handleGeneric(exception);

        // Assert
        assertEquals("Unexpected server error", response.getBody().get("error"));
        assertEquals("An unexpected error occurred. Please try again later.", response.getBody().get("detail"));
        verify(exceptionHandler, times(1)).log.error("Unexpected server error", exception);
    }

    @Test
    void should_handle_specific_exception_correctly() {
        // Arrange
        when(jobNotFoundException.getMessage()).thenReturn("Job not found");

        // Act
        ResponseEntity<Map<String, String>> response = exceptionHandler.handleGeneric(jobNotFoundException);

        // Assert
        assertEquals("Unexpected server error", response.getBody().get("error"));
        assertEquals("An unexpected error occurred. Please try again later.", response.getBody().get("detail"));
        verify(exceptionHandler, times(1)).log.error("Unexpected server error", jobNotFoundException);
    }
}
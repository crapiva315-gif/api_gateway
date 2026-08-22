package dev.alexeev.api_gateway.exception;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Order(-2)
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(WebClientResponseException.class)
  public org.springframework.http.ResponseEntity<Object> handleDownstreamError(WebClientResponseException ex) {
    return org.springframework.http.ResponseEntity.status(ex.getStatusCode())
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .body(ex.getResponseBodyAsString());
  }

  @ExceptionHandler(Exception.class)
  public org.springframework.http.ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("timestamp", LocalDateTime.now());
    body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
    body.put("error", "Internal Server Error");
    body.put("message", "An unexpected error occurred");
    return org.springframework.http.ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
  }
}
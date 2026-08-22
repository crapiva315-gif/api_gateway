package dev.alexeev.api_gateway.controller;

import dev.alexeev.api_gateway.dto.GatewayRegisterRequest;
import dev.alexeev.api_gateway.service.RegistrationOrchestrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/gateway")
@RequiredArgsConstructor
public class RegistrationController {

  private final RegistrationOrchestrationService registrationOrchestrationService;

  @PostMapping("/register")
  public Mono<ResponseEntity<Map<String, Long>>> register(@Valid @RequestBody GatewayRegisterRequest request) {
    return registrationOrchestrationService.register(request)
            .map(userId -> ResponseEntity.status(HttpStatus.CREATED).body(Map.of("userId", userId)));
  }
}
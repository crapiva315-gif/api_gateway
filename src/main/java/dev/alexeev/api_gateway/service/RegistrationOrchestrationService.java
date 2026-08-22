package dev.alexeev.api_gateway.service;

import dev.alexeev.api_gateway.dto.AuthRegisterRequest;
import dev.alexeev.api_gateway.dto.AuthRegisterResponse;
import dev.alexeev.api_gateway.dto.GatewayRegisterRequest;
import dev.alexeev.api_gateway.dto.UserCreateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationOrchestrationService {

  @Qualifier("authServiceWebClient")
  private final WebClient authServiceWebClient;

  @Qualifier("userServiceWebClient")
  private final WebClient userServiceWebClient;

  private final ServiceAccountTokenProvider tokenProvider;

  public Mono<Long> register(GatewayRegisterRequest request) {
    AuthRegisterRequest authRequest = new AuthRegisterRequest(
            request.getLogin(), request.getPassword(), "user");

    return authServiceWebClient.post()
            .uri("/api/v1/auth/register")
            .bodyValue(authRequest)
            .retrieve()
            .bodyToMono(AuthRegisterResponse.class)
            .flatMap(authResponse -> createUserProfile(authResponse.getUserId(), request)
                    .onErrorResume(userServiceError ->
                            compensateAuthRegistration(authResponse.getUserId(), userServiceError)));
  }

  private Mono<Long> createUserProfile(Long userId, GatewayRegisterRequest request) {
    UserCreateRequest userRequest = new UserCreateRequest(
            userId, request.getName(), request.getSurname(),
            request.getEmail(), request.getBirthDate());

    return userServiceWebClient.post()
            .uri("/api/v1/users")
            .bodyValue(userRequest)
            .retrieve()
            .bodyToMono(Object.class)
            .thenReturn(userId);
  }

  private Mono<Long> compensateAuthRegistration(Long userId, Throwable originalError) {
    log.warn("User Service registration failed for userId={}, rolling back auth credentials. Reason: {}",
            userId, originalError.getMessage());

    return tokenProvider.getAccessToken()
            .flatMap(token -> authServiceWebClient.delete()
                    .uri("/api/v1/auth/credentials/{userId}", userId)
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve()
                    .toBodilessEntity())
            .doOnSuccess(v -> log.info("Successfully rolled back credentials for userId={}", userId))
            .onErrorResume(rollbackError -> {
              log.error("CRITICAL: rollback failed for userId={}. Manual cleanup required.", userId, rollbackError);
              return Mono.empty();
            })
            .then(Mono.error(originalError));
  }
}
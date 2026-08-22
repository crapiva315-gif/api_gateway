package dev.alexeev.api_gateway.service;

import dev.alexeev.api_gateway.dto.LoginRequest;
import dev.alexeev.api_gateway.dto.TokenResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class ServiceAccountTokenProvider {

  private final WebClient authServiceWebClient;
  private final String serviceLogin;
  private final String servicePassword;

  private final AtomicReference<String> cachedAccessToken = new AtomicReference<>();

  public ServiceAccountTokenProvider(
          @Qualifier("authServiceWebClient") WebClient authServiceWebClient,
          @Value("${gateway.service-account.login}") String serviceLogin,
          @Value("${gateway.service-account.password}") String servicePassword) {
    this.authServiceWebClient = authServiceWebClient;
    this.serviceLogin = serviceLogin;
    this.servicePassword = servicePassword;
  }

  public Mono<String> getAccessToken() {
    String cached = cachedAccessToken.get();
    if (cached != null) {
      return Mono.just(cached);
    }
    return login();
  }

  public void invalidateToken() {
    cachedAccessToken.set(null);
  }

  private Mono<String> login() {
    return authServiceWebClient.post()
            .uri("/api/v1/auth/login")
            .bodyValue(new LoginRequest(serviceLogin, servicePassword))
            .retrieve()
            .bodyToMono(TokenResponse.class)
            .map(TokenResponse::getAccessToken)
            .doOnNext(cachedAccessToken::set)
            .doOnError(e -> log.error("Failed to authenticate gateway service account", e));
  }
}
package dev.alexeev.api_gateway.filter;

import dev.alexeev.api_gateway.dto.ValidateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationGlobalFilter implements GlobalFilter, Ordered {

  private static final List<String> PUBLIC_PATHS = List.of(
          "/api/v1/auth/login",
          "/api/v1/auth/register",
          "/api/v1/auth/validate",
          "/api/v1/auth/refresh",
          "/api/v1/gateway/register"
  );

  @Qualifier("authServiceWebClient")
  private final WebClient authServiceWebClient;

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String path = exchange.getRequest().getURI().getPath();

    if (isPublicPath(path)) {
      return chain.filter(exchange);
    }

    String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      return unauthorized(exchange);
    }

    String token = authHeader.substring("Bearer ".length());

    return authServiceWebClient.post()
            .uri(uriBuilder -> uriBuilder.path("/api/v1/auth/validate").queryParam("token", token).build())
            .retrieve()
            .bodyToMono(ValidateResponse.class)
            .flatMap(validateResponse -> {
              if (!validateResponse.isValid()) {
                return unauthorized(exchange);
              }
              ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                      .header("X-User-Id", String.valueOf(validateResponse.getUserId()))
                      .header("X-User-Role", validateResponse.getRole())
                      .build();
              return chain.filter(exchange.mutate().request(mutatedRequest).build());
            })
            .onErrorResume(e -> {
              log.error("Token validation call failed", e);
              return unauthorized(exchange);
            });
  }

  private boolean isPublicPath(String path) {
    return PUBLIC_PATHS.contains(path);
  }

  private Mono<Void> unauthorized(ServerWebExchange exchange) {
    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
    return exchange.getResponse().setComplete();
  }

  @Override
  public int getOrder() {
    return -1;
  }
}
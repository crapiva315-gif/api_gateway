package dev.alexeev.api_gateway.filter;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicReference;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationGlobalFilterTest {

  private WireMockServer authService;
  private JwtAuthenticationGlobalFilter filter;

  @BeforeEach
  void setUp() {
    authService = new WireMockServer(0);
    authService.start();
    WebClient authServiceWebClient = WebClient.builder()
            .baseUrl("http://localhost:" + authService.port())
            .build();
    filter = new JwtAuthenticationGlobalFilter(authServiceWebClient);
  }

  @AfterEach
  void tearDown() {
    authService.stop();
  }

  @Test
  void publicPath_bypassesFilter_noDownstreamCall() {
    ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/v1/auth/login"));
    AtomicReference<ServerWebExchange> chainCalledWith = new AtomicReference<>();

    StepVerifier.create(filter.filter(exchange, ex -> {
              chainCalledWith.set(ex);
              return Mono.empty();
            }))
            .verifyComplete();

    assertThat(chainCalledWith.get()).isNotNull();
    authService.verify(0, postRequestedFor(anyUrl()));
  }

  @Test
  void protectedPath_missingAuthorizationHeader_returns401() {
    ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/users/1"));

    StepVerifier.create(filter.filter(exchange, ex -> Mono.empty()))
            .verifyComplete();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    authService.verify(0, postRequestedFor(anyUrl()));
  }

  @Test
  void protectedPath_invalidToken_returns401() {
    authService.stubFor(post(urlPathEqualTo("/api/v1/auth/validate"))
            .willReturn(okJson("{\"valid\": false}")));

    ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/users/1")
                    .header("Authorization", "Bearer bad-token"));

    StepVerifier.create(filter.filter(exchange, ex -> Mono.empty()))
            .verifyComplete();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void protectedPath_validToken_forwardsRequestWithUserHeaders() {
    authService.stubFor(post(urlPathEqualTo("/api/v1/auth/validate"))
            .willReturn(okJson("{\"valid\": true, \"userId\": 42, \"role\": \"user\"}")));

    ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/users/42")
                    .header("Authorization", "Bearer good-token"));
    AtomicReference<ServerWebExchange> chainCalledWith = new AtomicReference<>();

    StepVerifier.create(filter.filter(exchange, ex -> {
              chainCalledWith.set(ex);
              return Mono.empty();
            }))
            .verifyComplete();

    ServerWebExchange forwarded = chainCalledWith.get();
    assertThat(forwarded).isNotNull();
    assertThat(forwarded.getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("42");
    assertThat(forwarded.getRequest().getHeaders().getFirst("X-User-Role")).isEqualTo("user");
  }

  @Test
  void protectedPath_authServiceDown_returns401InsteadOf500() {
    authService.stubFor(post(urlPathEqualTo("/api/v1/auth/validate"))
            .willReturn(aResponse().withStatus(500)));

    ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/users/1")
                    .header("Authorization", "Bearer some-token"));

    StepVerifier.create(filter.filter(exchange, ex -> Mono.empty()))
            .verifyComplete();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}

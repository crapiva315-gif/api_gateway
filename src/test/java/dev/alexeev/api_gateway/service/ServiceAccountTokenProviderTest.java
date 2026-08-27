package dev.alexeev.api_gateway.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

class ServiceAccountTokenProviderTest {

  private WireMockServer authService;
  private ServiceAccountTokenProvider provider;

  @BeforeEach
  void setUp() {
    authService = new WireMockServer(0);
    authService.start();
    WebClient client = WebClient.builder().baseUrl("http://localhost:" + authService.port()).build();
    provider = new ServiceAccountTokenProvider(client, "admin", "ChangeMe123!");
  }

  @AfterEach
  void tearDown() {
    authService.stop();
  }

  @Test
  void getAccessToken_cachesTokenAcrossCalls() {
    authService.stubFor(post(urlEqualTo("/api/v1/auth/login"))
            .willReturn(okJson("{\"accessToken\": \"tok-1\", \"refreshToken\": \"ref-1\"}")));

    StepVerifier.create(provider.getAccessToken()).expectNext("tok-1").verifyComplete();
    StepVerifier.create(provider.getAccessToken()).expectNext("tok-1").verifyComplete();

    authService.verify(1, postRequestedFor(urlEqualTo("/api/v1/auth/login")));
  }

  @Test
  void invalidateToken_forcesReLoginOnNextCall() {
    authService.stubFor(post(urlEqualTo("/api/v1/auth/login"))
            .willReturn(okJson("{\"accessToken\": \"tok-1\", \"refreshToken\": \"ref-1\"}")));

    StepVerifier.create(provider.getAccessToken()).expectNext("tok-1").verifyComplete();
    provider.invalidateToken();
    StepVerifier.create(provider.getAccessToken()).expectNext("tok-1").verifyComplete();

    authService.verify(2, postRequestedFor(urlEqualTo("/api/v1/auth/login")));
  }

  @Test
  void getAccessToken_loginFails_propagatesError() {
    authService.stubFor(post(urlEqualTo("/api/v1/auth/login"))
            .willReturn(aResponse().withStatus(401)));

    StepVerifier.create(provider.getAccessToken())
            .expectError()
            .verify();
  }
}

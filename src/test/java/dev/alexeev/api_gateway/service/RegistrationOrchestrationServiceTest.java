package dev.alexeev.api_gateway.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.alexeev.api_gateway.dto.GatewayRegisterRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

class RegistrationOrchestrationServiceTest {

  private WireMockServer server;
  private ServiceAccountTokenProvider tokenProvider;

  @BeforeEach
  void setUp() {
    server = new WireMockServer(0);
    server.start();
    tokenProvider = Mockito.mock(ServiceAccountTokenProvider.class);
  }

  @AfterEach
  void tearDown() {
    server.stop();
  }

  private RegistrationOrchestrationService buildService() {
    WebClient client = WebClient.builder().baseUrl("http://localhost:" + server.port()).build();
    return new RegistrationOrchestrationService(client, client, tokenProvider);
  }

  private GatewayRegisterRequest sampleRequest() {
    GatewayRegisterRequest request = new GatewayRegisterRequest();
    request.setLogin("alex");
    request.setPassword("password123");
    request.setName("Alex");
    request.setSurname("Ivanov");
    request.setEmail("alex@example.com");
    request.setBirthDate(LocalDate.of(1995, 1, 1));
    return request;
  }

  @Test
  void register_happyPath_returnsUserIdFromAuthService() {
    server.stubFor(post(urlEqualTo("/api/v1/auth/register"))
            .willReturn(okJson("{\"userId\": 42}")));
    server.stubFor(post(urlEqualTo("/api/v1/users"))
            .willReturn(okJson("{}")));

    StepVerifier.create(buildService().register(sampleRequest()))
            .expectNext(42L)
            .verifyComplete();

    server.verify(1, postRequestedFor(urlEqualTo("/api/v1/auth/register")));
    server.verify(1, postRequestedFor(urlEqualTo("/api/v1/users")));
    server.verify(0, deleteRequestedFor(anyUrl()));
  }

  @Test
  void register_userServiceFails_rollsBackAuthCredentials_andPropagatesOriginalError() {
    server.stubFor(post(urlEqualTo("/api/v1/auth/register"))
            .willReturn(okJson("{\"userId\": 42}")));
    server.stubFor(post(urlEqualTo("/api/v1/users"))
            .willReturn(aResponse().withStatus(500).withBody("user service down")));
    server.stubFor(delete(urlEqualTo("/api/v1/auth/credentials/42"))
            .willReturn(aResponse().withStatus(204)));
    Mockito.when(tokenProvider.getAccessToken()).thenReturn(Mono.just("service-token"));

    StepVerifier.create(buildService().register(sampleRequest()))
            .expectError()
            .verify();

    server.verify(1, deleteRequestedFor(urlEqualTo("/api/v1/auth/credentials/42"))
            .withHeader("Authorization", equalTo("Bearer service-token")));
  }

  @Test
  void register_userServiceFails_andRollbackAlsoFails_stillPropagatesOriginalError() {
    server.stubFor(post(urlEqualTo("/api/v1/auth/register"))
            .willReturn(okJson("{\"userId\": 42}")));
    server.stubFor(post(urlEqualTo("/api/v1/users"))
            .willReturn(aResponse().withStatus(500).withBody("user service down")));
    server.stubFor(delete(urlEqualTo("/api/v1/auth/credentials/42"))
            .willReturn(aResponse().withStatus(500).withBody("rollback also failed")));
    Mockito.when(tokenProvider.getAccessToken()).thenReturn(Mono.just("service-token"));

    // The client must still see the original user-service failure, not a rollback error,
    // and the call must not hang or silently succeed.
    StepVerifier.create(buildService().register(sampleRequest()))
            .expectErrorSatisfies(error ->
                    org.assertj.core.api.Assertions.assertThat(error.getMessage())
                            .contains("500"))
            .verify();
  }

  @Test
  void register_authServiceRejectsRegistration_neverCallsUserServiceOrRollback() {
    server.stubFor(post(urlEqualTo("/api/v1/auth/register"))
            .willReturn(aResponse().withStatus(409).withBody("login already taken")));

    StepVerifier.create(buildService().register(sampleRequest()))
            .expectError()
            .verify();

    server.verify(0, postRequestedFor(urlEqualTo("/api/v1/users")));
    server.verify(0, deleteRequestedFor(anyUrl()));
  }
}

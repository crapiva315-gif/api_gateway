package dev.alexeev.api_gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

  @Bean
  public WebClient authServiceWebClient(@Value("${service-urls.auth-service}") String baseUrl) {
    return WebClient.builder().baseUrl(baseUrl).build();
  }

  @Bean
  public WebClient userServiceWebClient(@Value("${service-urls.user-service}") String baseUrl) {
    return WebClient.builder().baseUrl(baseUrl).build();
  }
}
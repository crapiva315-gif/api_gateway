package dev.alexeev.api_gateway.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TokenResponse {
  private String accessToken;
  private String refreshToken;
}
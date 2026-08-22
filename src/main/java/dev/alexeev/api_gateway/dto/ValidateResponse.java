package dev.alexeev.api_gateway.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ValidateResponse {
  private boolean valid;
  private Long userId;
  private String role;
}
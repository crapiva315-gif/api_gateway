package dev.alexeev.api_gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class AuthRegisterRequest {
  private String login;
  private String password;
  private String role;
}
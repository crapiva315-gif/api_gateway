package dev.alexeev.api_gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
public class UserCreateRequest {
  private Long id;
  private String name;
  private String surname;
  private String email;
  private LocalDate birthDate;
}
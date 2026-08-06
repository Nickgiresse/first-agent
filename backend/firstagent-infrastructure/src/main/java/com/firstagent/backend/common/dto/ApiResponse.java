package com.firstagent.backend.common.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

  private boolean success;
  private String message;
  private T data;

  @Builder.Default private LocalDateTime timestamp = LocalDateTime.now();

  public static <T> ApiResponse<T> success(T data, String message) {
    return ApiResponse.<T>builder()
        .success(true)
        .message(message)
        .data(data)
        .timestamp(LocalDateTime.now())
        .build();
  }

  public static <T> ApiResponse<T> success(T data) {
    return success(data, "Opération réussie");
  }
}

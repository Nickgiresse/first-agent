package com.firstagent.backend.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    private boolean success;
    private String errorCode;
    private String message;
    private List<String> details;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
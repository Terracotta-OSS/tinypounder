package org.terracotta.tinypounder.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standard API response wrapper for all REST endpoints.
 * Converted to Java 21 record for immutability and reduced boilerplate.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
    boolean success,
    T data,
    String message,
    String error,
    String details
) {

  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(true, data, null, null, null);
  }

  public static <T> ApiResponse<T> success(T data, String message) {
    return new ApiResponse<>(true, data, message, null, null);
  }

  public static <T> ApiResponse<T> success(String message) {
    return new ApiResponse<>(true, null, message, null, null);
  }

  public static <T> ApiResponse<T> error(String error) {
    return new ApiResponse<>(false, null, null, error, null);
  }

  public static <T> ApiResponse<T> error(String error, String details) {
    return new ApiResponse<>(false, null, null, error, details);
  }

  public boolean isSuccess() {
    return success;
  }

  public T getData() {
    return data;
  }

  public String getMessage() {
    return message;
  }

  public String getError() {
    return error;
  }

  public String getDetails() {
    return details;
  }
}
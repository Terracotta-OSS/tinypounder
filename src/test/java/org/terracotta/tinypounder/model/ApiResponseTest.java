package org.terracotta.tinypounder.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ApiResponse record (Java 21 feature).
 */
@DisplayName("ApiResponse Model Tests")
class ApiResponseTest {

  @Test
  @DisplayName("Should create success response with message only")
  void shouldCreateSuccessResponseWithMessageOnly() {
    // Given
    String message = "test-data";

    // When
    ApiResponse<Object> response = ApiResponse.success(message);

    // Then
    assertThat(response.success()).isTrue();
    assertThat(response.data()).isNull();
    assertThat(response.message()).isEqualTo(message);
    assertThat(response.error()).isNull();
    assertThat(response.details()).isNull();
  }

  @Test
  @DisplayName("Should create success response with data and message")
  void shouldCreateSuccessResponseWithDataAndMessage() {
    // Given
    String data = "test-data";
    String message = "Operation successful";

    // When
    ApiResponse<String> response = ApiResponse.success(data, message);

    // Then
    assertThat(response.success()).isTrue();
    assertThat(response.data()).isEqualTo(data);
    assertThat(response.message()).isEqualTo(message);
    assertThat(response.error()).isNull();
    assertThat(response.details()).isNull();
  }

  @Test
  @DisplayName("Should create error response")
  void shouldCreateErrorResponse() {
    // Given
    String error = "Operation failed";

    // When
    ApiResponse<Object> response = ApiResponse.error(error);

    // Then
    assertThat(response.success()).isFalse();
    assertThat(response.data()).isNull();
    assertThat(response.message()).isNull();
    assertThat(response.error()).isEqualTo(error);
    assertThat(response.details()).isNull();
  }

  @Test
  @DisplayName("Should create error response with details")
  void shouldCreateErrorResponseWithDetails() {
    // Given
    String error = "Operation failed";
    String details = "Connection timeout after 30 seconds";

    // When
    ApiResponse<Object> response = ApiResponse.error(error, details);

    // Then
    assertThat(response.success()).isFalse();
    assertThat(response.data()).isNull();
    assertThat(response.message()).isNull();
    assertThat(response.error()).isEqualTo(error);
    assertThat(response.details()).isEqualTo(details);
  }

  @Test
  @DisplayName("Should implement equals correctly for records")
  void shouldImplementEqualsCorrectly() {
    // Given
    ApiResponse<String> response1 = ApiResponse.success("data", "message");
    ApiResponse<String> response2 = ApiResponse.success("data", "message");
    ApiResponse<String> response3 = ApiResponse.success("other", "message");

    // Then
    assertThat(response1).isEqualTo(response2);
    assertThat(response1).isNotEqualTo(response3);
    assertThat(response1.hashCode()).isEqualTo(response2.hashCode());
  }

  @Test
  @DisplayName("Should implement toString correctly for records")
  void shouldImplementToStringCorrectly() {
    // Given
    ApiResponse<String> response = ApiResponse.success("test-data", "Success");

    // When
    String toString = response.toString();

    // Then
    assertThat(toString)
        .contains("ApiResponse")
        .contains("success=true")
        .contains("data=test-data")
        .contains("message=Success");
  }

  @Test
  @DisplayName("Should use backward-compatible getters")
  void shouldUseBackwardCompatibleGetters() {
    // Given
    ApiResponse<String> response = ApiResponse.success("data", "message");

    // Then - verify backward-compatible getters work
    assertThat(response.isSuccess()).isTrue();
    assertThat(response.getData()).isEqualTo("data");
    assertThat(response.getMessage()).isEqualTo("message");
    assertThat(response.getError()).isNull();
    assertThat(response.getDetails()).isNull();
  }

  @Test
  @DisplayName("Should handle null data in success response")
  void shouldHandleNullDataInSuccessResponse() {
    // When
    ApiResponse<Object> response = ApiResponse.success((Object) null, "Success");

    // Then
    assertThat(response.success()).isTrue();
    assertThat(response.data()).isNull();
    assertThat(response.message()).isEqualTo("Success");
  }

  @Test
  @DisplayName("Should handle complex data objects")
  void shouldHandleComplexDataObjects() {
    // Given
    record TestData(String field1, int field2) {}
    TestData complexData = new TestData("value1", 42);

    // When
    ApiResponse<TestData> response = ApiResponse.success(complexData);

    // Then
    assertThat(response.success()).isTrue();
    assertThat(response.data()).isEqualTo(complexData);
    assertThat(response.data().field1()).isEqualTo("value1");
    assertThat(response.data().field2()).isEqualTo(42);
  }

  @Test
  @DisplayName("Should create response with empty message")
  void shouldCreateResponseWithEmptyMessage() {
    // When
    ApiResponse<Object> response = ApiResponse.success("");

    // Then
    assertThat(response.success()).isTrue();
    assertThat(response.message()).isEmpty();
  }

  @Test
  @DisplayName("Should handle error response with null details")
  void shouldHandleErrorResponseWithNullDetails() {
    // When
    ApiResponse<Object> response = ApiResponse.error("Error occurred", null);

    // Then
    assertThat(response.success()).isFalse();
    assertThat(response.error()).isEqualTo("Error occurred");
    assertThat(response.details()).isNull();
  }
}

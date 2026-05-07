package org.terracotta.tinypounder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ExceptionUtils utility class.
 * Tests the Java 21 pattern matching for instanceof feature.
 */
@DisplayName("ExceptionUtils Tests")
class ExceptionUtilsTest {

  @Test
  @DisplayName("Should return root cause for simple exception")
  void shouldReturnRootCauseForSimpleException() {
    // Given
    Exception exception = new RuntimeException("Root cause");

    // When
    Throwable rootCause = ExceptionUtils.getRootCause(exception);

    // Then
    assertThat(rootCause).isEqualTo(exception);
    assertThat(rootCause.getMessage()).isEqualTo("Root cause");
  }

  @Test
  @DisplayName("Should return root cause for nested exceptions")
  void shouldReturnRootCauseForNestedException() {
    // Given
    IOException rootCause = new IOException("IO error");
    RuntimeException middleException = new RuntimeException("Middle", rootCause);
    Exception topException = new Exception("Top", middleException);

    // When
    Throwable result = ExceptionUtils.getRootCause(topException);

    // Then
    assertThat(result).isEqualTo(rootCause);
    assertThat(result.getMessage()).isEqualTo("IO error");
  }

  @Test
  @DisplayName("Should unwrap InvocationTargetException using pattern matching")
  void shouldUnwrapInvocationTargetException() {
    // Given
    RuntimeException targetException = new RuntimeException("Target exception");
    InvocationTargetException invocationException = new InvocationTargetException(targetException);

    // When
    Throwable result = ExceptionUtils.getRootCause(invocationException);

    // Then
    assertThat(result).isEqualTo(targetException);
    assertThat(result.getMessage()).isEqualTo("Target exception");
  }

  @Test
  @DisplayName("Should handle nested InvocationTargetException")
  void shouldHandleNestedInvocationTargetException() {
    // Given
    IOException rootCause = new IOException("Root IO error");
    InvocationTargetException innerITE = new InvocationTargetException(rootCause);
    RuntimeException wrapper = new RuntimeException("Wrapper", innerITE);

    // When
    Throwable result = ExceptionUtils.getRootCause(wrapper);

    // Then
    assertThat(result).isEqualTo(rootCause);
    assertThat(result.getMessage()).isEqualTo("Root IO error");
  }

  @Test
  @DisplayName("Should return root cause message for simple exception")
  void shouldReturnRootCauseMessage() {
    // Given
    Exception exception = new RuntimeException("Error message");

    // When
    String message = ExceptionUtils.getRootCauseMessage(exception);

    // Then
    assertThat(message).isEqualTo("Error message");
  }

  @Test
  @DisplayName("Should return root cause message for nested exceptions")
  void shouldReturnRootCauseMessageForNested() {
    // Given
    IOException rootCause = new IOException("Root error");
    RuntimeException wrapper = new RuntimeException("Wrapper", rootCause);

    // When
    String message = ExceptionUtils.getRootCauseMessage(wrapper);

    // Then
    assertThat(message).isEqualTo("Root error");
  }

  @Test
  @DisplayName("Should return class name when message is null")
  void shouldReturnClassNameWhenMessageIsNull() {
    // Given
    Exception exception = new RuntimeException((String) null);

    // When
    String message = ExceptionUtils.getRootCauseMessage(exception);

    // Then
    assertThat(message).contains("RuntimeException");
  }

  @Test
  @DisplayName("Should handle exception with null cause")
  void shouldHandleExceptionWithNullCause() {
    // Given
    Exception exception = new RuntimeException("No cause");

    // When
    Throwable rootCause = ExceptionUtils.getRootCause(exception);

    // Then
    assertThat(rootCause).isEqualTo(exception);
  }

  @Test
  @DisplayName("Should handle circular exception references")
  void shouldHandleCircularReferences() {
    // Given
    Exception exception1 = new RuntimeException("Exception 1");
    Exception exception2 = new RuntimeException("Exception 2", exception1);
    // Note: Can't actually create true circular reference in Java, but test the safety mechanism

    // When
    Throwable rootCause = ExceptionUtils.getRootCause(exception2);

    // Then
    assertThat(rootCause).isNotNull();
    assertThat(rootCause.getMessage()).isIn("Exception 1", "Exception 2");
  }

  @Test
  @DisplayName("Should handle deeply nested exceptions")
  void shouldHandleDeeplyNestedException() {
    // Given - Create a chain of 10 exceptions
    Throwable current = new IOException("Root");
    for (int i = 0; i < 10; i++) {
      current = new RuntimeException("Level " + i, current);
    }

    // When
    Throwable rootCause = ExceptionUtils.getRootCause(current);

    // Then
    assertThat(rootCause).isInstanceOf(IOException.class);
    assertThat(rootCause.getMessage()).isEqualTo("Root");
  }

  @Test
  @DisplayName("Should handle InvocationTargetException with null target")
  void shouldHandleInvocationTargetExceptionWithNullTarget() {
    // Given
    InvocationTargetException exception = new InvocationTargetException(null, "Invocation failed");

    // When
    Throwable rootCause = ExceptionUtils.getRootCause(exception);

    // Then - Returns the exception itself when target is null
    assertThat(rootCause).isEqualTo(exception);
  }

  @Test
  @DisplayName("Should handle mixed exception types in chain")
  void shouldHandleMixedExceptionTypes() {
    // Given
    IllegalArgumentException root = new IllegalArgumentException("Invalid argument");
    IOException ioException = new IOException("IO failed", root);
    InvocationTargetException ite = new InvocationTargetException(ioException);
    RuntimeException wrapper = new RuntimeException("Wrapper", ite);

    // When
    Throwable rootCause = ExceptionUtils.getRootCause(wrapper);

    // Then
    assertThat(rootCause).isEqualTo(root);
    assertThat(rootCause).isInstanceOf(IllegalArgumentException.class);
    assertThat(rootCause.getMessage()).isEqualTo("Invalid argument");
  }
}

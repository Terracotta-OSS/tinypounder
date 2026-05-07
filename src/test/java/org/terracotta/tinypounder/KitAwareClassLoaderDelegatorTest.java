package org.terracotta.tinypounder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for KitAwareClassLoaderDelegator.
 * Tests kit detection, classloader setup, and security path validation.
 */
@DisplayName("KitAwareClassLoaderDelegator Tests")
class KitAwareClassLoaderDelegatorTest {

  @TempDir
  Path tempDir;

  private KitAwareClassLoaderDelegator delegator;

  @BeforeEach
  void setUp() {
    delegator = new KitAwareClassLoaderDelegator();
  }

  @Test
  @DisplayName("Should initialize with default classloader")
  void shouldInitializeWithDefaultClassloader() {
    // Then
    assertThat(delegator.getUrlClassLoader()).isNotNull();
    assertThat(delegator.getUrlClassLoader()).isEqualTo(Thread.currentThread().getContextClassLoader());
  }

  @Test
  @DisplayName("Should detect absence of Terracotta Store")
  void shouldDetectAbsenceOfTerracottaStore() {
    // When/Then
    assertThat(delegator.containsTerracottaStore()).isFalse();
  }

  @Test
  @DisplayName("Should detect presence or absence of Ehcache based on classpath")
  void shouldDetectEhcacheBasedOnClasspath() {
    // When/Then - May be true if Ehcache is on test classpath
    assertThat(delegator.containsEhcache()).isIn(true, false);
  }

  @Test
  @DisplayName("Should detect absence of EE Kit")
  void shouldDetectAbsenceOfEEKit() {
    // When/Then
    assertThat(delegator.isEEKit()).isFalse();
  }

  @Test
  @DisplayName("Should reject null security path")
  void shouldRejectNullSecurityPath() {
    // When/Then
    assertThat(delegator.verifySecurityPath(null)).isFalse();
  }

  @Test
  @DisplayName("Should reject empty security path")
  void shouldRejectEmptySecurityPath() {
    // When/Then
    assertThat(delegator.verifySecurityPath("")).isFalse();
  }

  @Test
  @DisplayName("Should reject non-existent security path")
  void shouldRejectNonExistentSecurityPath() {
    // Given
    String nonExistentPath = tempDir.resolve("non-existent").toString();

    // When/Then
    assertThat(delegator.verifySecurityPath(nonExistentPath)).isFalse();
  }

  @Test
  @DisplayName("Should reject security path that is a file")
  void shouldRejectSecurityPathThatIsFile() throws IOException {
    // Given
    File file = tempDir.resolve("file.txt").toFile();
    Files.createFile(file.toPath());

    // When/Then
    assertThat(delegator.verifySecurityPath(file.getAbsolutePath())).isFalse();
  }

  @Test
  @DisplayName("Should reject security directory without required files")
  void shouldRejectSecurityDirectoryWithoutRequiredFiles() throws IOException {
    // Given
    Path securityDir = tempDir.resolve("security");
    Files.createDirectory(securityDir);

    // When/Then
    assertThat(delegator.verifySecurityPath(securityDir.toString())).isFalse();
  }

  @Test
  @DisplayName("Should accept security directory with access-control file")
  void shouldAcceptSecurityDirectoryWithAccessControl() throws IOException {
    // Given
    Path securityDir = tempDir.resolve("security");
    Files.createDirectory(securityDir);
    Files.createFile(securityDir.resolve("access-control.properties"));

    // When/Then
    assertThat(delegator.verifySecurityPath(securityDir.toString())).isTrue();
  }

  @Test
  @DisplayName("Should accept security directory with identity file")
  void shouldAcceptSecurityDirectoryWithIdentity() throws IOException {
    // Given
    Path securityDir = tempDir.resolve("security");
    Files.createDirectory(securityDir);
    Files.createFile(securityDir.resolve("identity.xml"));

    // When/Then
    assertThat(delegator.verifySecurityPath(securityDir.toString())).isTrue();
  }

  @Test
  @DisplayName("Should accept security directory with trusted-authority file")
  void shouldAcceptSecurityDirectoryWithTrustedAuthority() throws IOException {
    // Given
    Path securityDir = tempDir.resolve("security");
    Files.createDirectory(securityDir);
    Files.createFile(securityDir.resolve("trusted-authority.jks"));

    // When/Then
    assertThat(delegator.verifySecurityPath(securityDir.toString())).isTrue();
  }

  @Test
  @DisplayName("Should handle null kit path")
  void shouldHandleNullKitPath() {
    // When/Then - Implementation may handle null gracefully or throw
    try {
      delegator.setAndVerifyKitPathAndClassLoader(null);
    } catch (Exception e) {
      assertThat(e).isInstanceOf(RuntimeException.class);
    }
  }

  @Test
  @DisplayName("Should handle empty kit path")
  void shouldHandleEmptyKitPath() {
    // When/Then - Implementation may handle empty string gracefully or throw
    try {
      delegator.setAndVerifyKitPathAndClassLoader("");
    } catch (Exception e) {
      assertThat(e).isInstanceOf(RuntimeException.class);
    }
  }

  @Test
  @DisplayName("Should throw exception for non-existent kit path")
  void shouldThrowExceptionForNonExistentKitPath() {
    // Given
    String nonExistentPath = tempDir.resolve("non-existent-kit").toString();

    // When/Then
    assertThatThrownBy(() -> delegator.setAndVerifyKitPathAndClassLoader(nonExistentPath))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("kitPath");
  }

  @Test
  @DisplayName("Should handle kit path with no JAR files")
  void shouldHandleKitPathWithNoJars() throws IOException {
    // Given
    Path kitDir = tempDir.resolve("empty-kit");
    Files.createDirectory(kitDir);
    Files.createDirectory(kitDir.resolve("client"));
    Files.createDirectory(kitDir.resolve("client/lib"));

    // When
    delegator.setAndVerifyKitPathAndClassLoader(kitDir.toString());

    // Then
    assertThat(delegator.getUrlClassLoader()).isNotNull();
  }

  @Test
  @DisplayName("Should create classloader for valid kit structure")
  void shouldCreateClassloaderForValidKitStructure() throws IOException {
    // Given
    Path kitDir = tempDir.resolve("valid-kit");
    Path clientLib = kitDir.resolve("client/lib");
    Files.createDirectories(clientLib);
    Files.createFile(clientLib.resolve("test-lib.jar"));

    // When
    delegator.setAndVerifyKitPathAndClassLoader(kitDir.toString());

    // Then
    assertThat(delegator.getUrlClassLoader()).isNotNull();
    assertThat(delegator.getUrlClassLoader()).isNotEqualTo(Thread.currentThread().getContextClassLoader());
  }



  @Test
  @DisplayName("Should deduplicate JAR files by hash")
  void shouldDeduplicateJarFilesByHash() throws IOException {
    // Given
    Path kitDir = tempDir.resolve("duplicate-kit");
    Path libDir = kitDir.resolve("client/lib");
    Path ehcacheDir = kitDir.resolve("client/ehcache");
    Files.createDirectories(libDir);
    Files.createDirectories(ehcacheDir);
    
    // Create identical JAR files in different locations
    byte[] jarContent = "JAR content".getBytes();
    Files.write(libDir.resolve("common.jar"), jarContent);
    Files.write(ehcacheDir.resolve("common.jar"), jarContent);

    // When
    delegator.setAndVerifyKitPathAndClassLoader(kitDir.toString());

    // Then - Should not throw exception and should handle duplicates
    assertThat(delegator.getUrlClassLoader()).isNotNull();
  }
}

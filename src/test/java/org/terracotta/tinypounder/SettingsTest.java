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

/**
 * Unit tests for Settings class which manages application configuration persistence.
 * Uses temporary directory to avoid interfering with real user configuration.
 */
@DisplayName("Settings Configuration Tests")
class SettingsTest {

  @TempDir
  Path tempDir;

  private Settings settings;
  private File configFile;

  @BeforeEach
  void setUp() {
    // Use temporary config file for testing to avoid deleting real user config
    configFile = tempDir.resolve("config.properties").toFile();
    settings = new Settings(configFile);
  }

  @Test
  @DisplayName("Should create config file on save")
  void shouldCreateConfigFileOnSave() {
    // When
    settings.save();

    // Then
    assertThat(configFile).exists();
  }

  @Test
  @DisplayName("Should save and load kit path")
  void shouldSaveAndLoadKitPath() throws IOException {
    // Given
    File tempKit = Files.createTempDirectory("test-kit").toFile();
    tempKit.deleteOnExit();
    
    // When
    settings.setKitPath(tempKit.getAbsolutePath());
    settings.save();
    
    Settings loadedSettings = new Settings(configFile);
    loadedSettings.load();

    // Then
    assertThat(loadedSettings.getKitPath()).isEqualTo(tempKit.getAbsolutePath());
    
    tempKit.delete();
  }

  @Test
  @DisplayName("Should save and load license path")
  void shouldSaveAndLoadLicensePath() throws IOException {
    // Given
    File tempLicense = File.createTempFile("license", ".xml");
    tempLicense.deleteOnExit();

    // When
    settings.setLicensePath(tempLicense.getAbsolutePath());
    settings.save();
    
    Settings loadedSettings = new Settings(configFile);
    loadedSettings.load();

    // Then
    assertThat(loadedSettings.getLicensePath()).isPresent();
    assertThat(loadedSettings.getLicensePath().get()).isEqualTo(tempLicense.getAbsolutePath());
    
    tempLicense.delete();
  }

  @Test
  @DisplayName("Should save and load stripe count")
  void shouldSaveAndLoadStripeCount() {
    // Given
    int stripeCount = 3;

    // When
    settings.setStripeCount(stripeCount);
    settings.save();
    
    Settings loadedSettings = new Settings(configFile);
    loadedSettings.load();

    // Then
    assertThat(loadedSettings.getStripeCount()).isEqualTo(stripeCount);
  }

  @Test
  @DisplayName("Should save and load server count")
  void shouldSaveAndLoadServerCount() {
    // Given
    int serverCount = 4;

    // When
    settings.setServerCount(serverCount);
    settings.save();
    
    Settings loadedSettings = new Settings(configFile);
    loadedSettings.load();

    // Then
    assertThat(loadedSettings.getServerCount()).isEqualTo(serverCount);
  }

  @Test
  @DisplayName("Should save and load offheap count")
  void shouldSaveAndLoadOffheapCount() {
    // Given
    int offheapCount = 3;

    // When
    settings.setOffheapCount(offheapCount);
    settings.save();
    
    Settings loadedSettings = new Settings(configFile);
    loadedSettings.load();

    // Then
    assertThat(loadedSettings.getOffheapCount()).isEqualTo(offheapCount);
  }

  @Test
  @DisplayName("Should save and load data root count")
  void shouldSaveAndLoadDataRootCount() {
    // Given
    int dataRootCount = 5;

    // When
    settings.setDataRootCount(dataRootCount);
    settings.save();
    
    Settings loadedSettings = new Settings(configFile);
    loadedSettings.load();

    // Then
    assertThat(loadedSettings.getDataRootCount()).isEqualTo(dataRootCount);
  }

  @Test
  @DisplayName("Should save and load security paths")
  void shouldSaveAndLoadSecurityPaths() throws IOException {
    // Given
    File serverSecDir = Files.createTempDirectory("server-sec").toFile();
    File clientSecDir = Files.createTempDirectory("client-sec").toFile();
    serverSecDir.deleteOnExit();
    clientSecDir.deleteOnExit();

    // When
    settings.setServerSecurityPath(serverSecDir.getAbsolutePath());
    settings.setClientSecurityPath(clientSecDir.getAbsolutePath());
    settings.save();
    
    Settings loadedSettings = new Settings(configFile);
    loadedSettings.load();

    // Then
    assertThat(loadedSettings.getServerSecurityPath()).isEqualTo(serverSecDir.getAbsolutePath());
    assertThat(loadedSettings.getClientSecurityPath()).isEqualTo(clientSecDir.getAbsolutePath());
    
    serverSecDir.delete();
    clientSecDir.delete();
  }

  @Test
  @DisplayName("Should save and load failover priority")
  void shouldSaveAndLoadFailoverPriority() {
    // Given
    String failoverPriority = "consistency:3";

    // When
    settings.setFailoverPriority(failoverPriority);
    settings.save();
    
    Settings loadedSettings = new Settings(configFile);
    loadedSettings.load();

    // Then
    assertThat(loadedSettings.getFailoverPriority()).isEqualTo(failoverPriority);
    assertThat(loadedSettings.getFailoverPriorityMode()).isEqualTo("consistency");
    assertThat(loadedSettings.getVoterCount()).isEqualTo("3");
  }

  @Test
  @DisplayName("Should save and load reconnect window")
  void shouldSaveAndLoadReconnectWindow() {
    // Given
    int reconnectWindow = 60;

    // When
    settings.setReconnectWindow(reconnectWindow);
    settings.save();
    
    Settings loadedSettings = new Settings(configFile);
    loadedSettings.load();

    // Then
    assertThat(loadedSettings.getReconnectWindow()).isEqualTo(reconnectWindow);
  }

  @Test
  @DisplayName("Should use default values on first load")
  void shouldUseDefaultValuesOnFirstLoad() {
    // When
    settings.load();

    // Then
    assertThat(settings.getStripeCount()).isEqualTo(2);
    assertThat(settings.getServerCount()).isEqualTo(2);
    assertThat(settings.getOffheapCount()).isEqualTo(2);
    assertThat(settings.getDataRootCount()).isEqualTo(2);
    assertThat(settings.getReconnectWindow()).isEqualTo(120);
    assertThat(settings.getFailoverPriority()).isEqualTo("availability");
  }

  @Test
  @DisplayName("Should parse failover priority without voter count")
  void shouldParseFailoverPriorityWithoutVoterCount() {
    // Given
    settings.setFailoverPriority("availability");

    // When/Then
    assertThat(settings.getFailoverPriorityMode()).isEqualTo("availability");
    assertThat(settings.getVoterCount()).isEqualTo("1");
  }

  @Test
  @DisplayName("Should load saved paths without validation")
  void shouldLoadSavedPathsWithoutValidation() {
    // Given
    settings.setKitPath("/non/existent/path");
    settings.setLicensePath("/non/existent/license.xml");
    settings.save();

    // When
    Settings loadedSettings = new Settings(configFile);
    loadedSettings.load();

    // Then - Settings.load() does not validate if paths exist, it just loads saved values
    // Path validation happens at usage time (e.g., in API handlers)
    assertThat(loadedSettings.getKitPath()).isEqualTo("/non/existent/path");
    assertThat(loadedSettings.getLicensePath()).hasValue("/non/existent/license.xml");
  }

  @Test
  @DisplayName("Should save and load server security enabled flag")
  void shouldSaveAndLoadServerSecurityEnabled() {
    // Given
    settings.setServerSecurityEnabled(true);

    // When
    settings.save();
    Settings loadedSettings = new Settings(configFile);
    loadedSettings.load();

    // Then
    assertThat(loadedSettings.isServerSecurityEnabled()).isTrue();
  }

  @Test
  @DisplayName("Should save and load client security enabled flag")
  void shouldSaveAndLoadClientSecurityEnabled() {
    // Given
    settings.setClientSecurityEnabled(true);

    // When
    settings.save();
    Settings loadedSettings = new Settings(configFile);
    loadedSettings.load();

    // Then
    assertThat(loadedSettings.isClientSecurityEnabled()).isTrue();
  }

  @Test
  @DisplayName("Should default security enabled flags to false")
  void shouldDefaultSecurityEnabledFlagsToFalse() {
    // When
    settings.load();

    // Then
    assertThat(settings.isServerSecurityEnabled()).isFalse();
    assertThat(settings.isClientSecurityEnabled()).isFalse();
  }

  @Test
  @DisplayName("Should preserve security paths when enabled flag is false")
  void shouldPreserveSecurityPathsWhenDisabled() throws IOException {
    // Given
    File serverSecDir = Files.createTempDirectory("server-sec").toFile();
    File clientSecDir = Files.createTempDirectory("client-sec").toFile();
    serverSecDir.deleteOnExit();
    clientSecDir.deleteOnExit();

    // When - Set paths but keep enabled flags false
    settings.setServerSecurityPath(serverSecDir.getAbsolutePath());
    settings.setClientSecurityPath(clientSecDir.getAbsolutePath());
    settings.setServerSecurityEnabled(false);
    settings.setClientSecurityEnabled(false);
    settings.save();
    
    Settings loadedSettings = new Settings(configFile);
    loadedSettings.load();

    // Then - Paths should be preserved even though security is disabled
    assertThat(loadedSettings.getServerSecurityPath()).isEqualTo(serverSecDir.getAbsolutePath());
    assertThat(loadedSettings.getClientSecurityPath()).isEqualTo(clientSecDir.getAbsolutePath());
    assertThat(loadedSettings.isServerSecurityEnabled()).isFalse();
    assertThat(loadedSettings.isClientSecurityEnabled()).isFalse();
    
    serverSecDir.delete();
    clientSecDir.delete();
  }

  @Test
  @DisplayName("Should independently manage server and client security flags")
  void shouldIndependentlyManageSecurityFlags() {
    // Given
    settings.setServerSecurityEnabled(true);
    settings.setClientSecurityEnabled(false);

    // When
    settings.save();
    Settings loadedSettings = new Settings(configFile);
    loadedSettings.load();

    // Then
    assertThat(loadedSettings.isServerSecurityEnabled()).isTrue();
    assertThat(loadedSettings.isClientSecurityEnabled()).isFalse();
  }

  @Test
  @DisplayName("Should normalize paths with trailing slashes")
  void shouldNormalizePathsWithTrailingSlashes() throws IOException {
    // Given
    File tempDir = Files.createTempDirectory("test-path").toFile();
    tempDir.deleteOnExit();
    String pathWithSlash = tempDir.getAbsolutePath() + "/";
    String pathWithBackslash = tempDir.getAbsolutePath() + "\\";

    // When
    settings.setKitPath(pathWithSlash);
    settings.setServerSecurityPath(pathWithBackslash);
    settings.setClientSecurityPath(pathWithSlash + "/");
    settings.setBaseLocation(pathWithBackslash + "\\");

    // Then - All trailing separators should be removed
    assertThat(settings.getKitPath()).isEqualTo(tempDir.getAbsolutePath());
    assertThat(settings.getServerSecurityPath()).isEqualTo(tempDir.getAbsolutePath());
    assertThat(settings.getClientSecurityPath()).isEqualTo(tempDir.getAbsolutePath());
    assertThat(settings.getBaseLocation()).isEqualTo(tempDir.getAbsolutePath());
    
    tempDir.delete();
  }

  @Test
  @DisplayName("Should normalize and persist paths with trailing separators")
  void shouldNormalizeAndPersistPathsWithTrailingSeparators() throws IOException {
    // Given
    File tempDir = Files.createTempDirectory("test-path").toFile();
    tempDir.deleteOnExit();
    String pathWithMultipleSlashes = tempDir.getAbsolutePath() + "///";

    // When
    settings.setKitPath(pathWithMultipleSlashes);
    settings.save();
    
    Settings loadedSettings = new Settings(configFile);
    loadedSettings.load();

    // Then - Path should be normalized (no trailing slashes)
    assertThat(loadedSettings.getKitPath()).isEqualTo(tempDir.getAbsolutePath());
    assertThat(loadedSettings.getKitPath()).doesNotEndWith("/");
    assertThat(loadedSettings.getKitPath()).doesNotEndWith("\\");
    
    tempDir.delete();
  }

  @Test
  @DisplayName("Should handle empty and null paths correctly")
  void shouldHandleEmptyAndNullPathsCorrectly() {
    // When - Set empty strings
    settings.setKitPath("");
    settings.setServerSecurityPath("");
    settings.setClientSecurityPath("");

    // Then - Empty strings should remain empty (not null)
    assertThat(settings.getKitPath()).isEmpty();
    assertThat(settings.getServerSecurityPath()).isEmpty();
    assertThat(settings.getClientSecurityPath()).isEmpty();

    // When - Set null
    settings.setKitPath(null);
    settings.setServerSecurityPath(null);
    settings.setClientSecurityPath(null);

    // Then - Nulls should remain null
    assertThat(settings.getKitPath()).isNull();
    assertThat(settings.getServerSecurityPath()).isNull();
    assertThat(settings.getClientSecurityPath()).isNull();
  }
}

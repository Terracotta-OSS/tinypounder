package org.terracotta.tinypounder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.Properties;

/**
 * Settings management for TinyPounder application.
 * Persists configuration to ~/.tinypounder/config.properties
 * 
 * @author Mathieu Carbou
 */
public class Settings {

  private static final String AVAILABILITY = "availability";
  
  private String kitPath;
  private String serverSecurityPath;
  private String clientSecurityPath;
  private String licensePath;
  private String baseLocation;
  private String failoverPriority;
  private boolean serverSecurityEnabled;
  private boolean clientSecurityEnabled;
  private int offheapCount;
  private int dataRootCount;
  private int serverCount;
  private int stripeCount;
  private int reconnectWindow;

  private final File config;

  /**
   * Default constructor - uses ~/.tinypounder/config.properties
   */
  public Settings() {
    this(new File(System.getProperty("user.home"), ".tinypounder/config.properties"));
  }

  /**
   * Constructor for testing - allows custom config file location
   * @param configFile the config file to use
   */
  Settings(File configFile) {
    this.config = configFile;
    // Initialize with defaults
    this.baseLocation = System.getProperty("user.home") + "/terracotta/MyCluster";
    this.failoverPriority = AVAILABILITY;
    this.serverSecurityEnabled = false;
    this.clientSecurityEnabled = false;
    this.offheapCount = 2;
    this.dataRootCount = 2;
    this.serverCount = 2;
    this.stripeCount = 2;
    this.reconnectWindow = 120;
  }

  /**
   * Normalize a path by trimming trailing separators.
   */
  private static String normalizePath(String path) {
    if (path == null || path.isEmpty()) {
      return path;
    }
    // Trim trailing slashes and backslashes
    while (path.endsWith("/") || path.endsWith("\\")) {
      path = path.substring(0, path.length() - 1);
    }
    return path;
  }

  /**
   * Load settings from config file and system properties.
   * System properties take precedence over saved config.
   */
  public synchronized void load() {
    config.getParentFile().mkdirs();
    Properties properties = new Properties();
    if (config.exists()) {
      try (InputStreamReader reader = new InputStreamReader(new FileInputStream(config), "UTF-8")) {
        properties.load(reader);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    
    // Load from system properties first, then from config file
    // Note: We don't validate if paths exist here - just load the saved values
    // Validation happens when the user tries to use them (e.g., in API handlers)
    serverSecurityPath = normalizePath(System.getProperty("serverSecurityPath", properties.getProperty("serverSecurityPath")));
    clientSecurityPath = normalizePath(System.getProperty("clientSecurityPath", properties.getProperty("clientSecurityPath")));
    kitPath = normalizePath(System.getProperty("kitPath", properties.getProperty("kitPath")));
    licensePath = normalizePath(System.getProperty("licensePath", properties.getProperty("licensePath")));
    
    baseLocation = normalizePath(System.getProperty("baseLocation", properties.getProperty("baseLocation")));
    if (baseLocation == null || baseLocation.trim().isEmpty()) {
      baseLocation = System.getProperty("user.home") + "/terracotta/MyCluster";
    }
    
    failoverPriority = System.getProperty("failoverPriority", properties.getProperty("failoverPriority"));
    if (failoverPriority == null) {
      failoverPriority = AVAILABILITY;
    }
    
    serverSecurityEnabled = Boolean.parseBoolean(
        System.getProperty("serverSecurityEnabled", properties.getProperty("serverSecurityEnabled", "false")));
    
    clientSecurityEnabled = Boolean.parseBoolean(
        System.getProperty("clientSecurityEnabled", properties.getProperty("clientSecurityEnabled", "false")));
    
    offheapCount = Integer.parseInt(System.getProperty("offheapCount", properties.getProperty("offheapCount", "0")));
    if (offheapCount < 1) {
      offheapCount = 2;
    }
    
    dataRootCount = Integer.parseInt(System.getProperty("dataRootCount", properties.getProperty("dataRootCount", "0")));
    if (dataRootCount < 1) {
      dataRootCount = 2;
    }
    
    serverCount = Integer.parseInt(System.getProperty("serverCount", properties.getProperty("serverCount", "0")));
    if (serverCount < 1) {
      serverCount = 2;
    }
    
    stripeCount = Integer.parseInt(System.getProperty("stripeCount", properties.getProperty("stripeCount", "0")));
    if (stripeCount < 1) {
      stripeCount = 2;
    }
    
    reconnectWindow = Integer.parseInt(System.getProperty("reconnectWindow", properties.getProperty("reconnectWindow", "0")));
    if (reconnectWindow < 5) {
      reconnectWindow = 120;
    }
  }

  /**
   * Save current settings to config file.
   */
  public synchronized void save() {
    // Ensure parent directory exists
    if (!config.getParentFile().exists()) {
      config.getParentFile().mkdirs();
    }
    
    Properties properties = new Properties();
    // Always save all properties to ensure consistent round-trip behavior
    if (kitPath != null) {
      properties.setProperty("kitPath", kitPath);
    }
    if (licensePath != null) {
      properties.setProperty("licensePath", licensePath);
    }
    if (serverSecurityPath != null) {
      properties.setProperty("serverSecurityPath", serverSecurityPath);
    }
    if (clientSecurityPath != null) {
      properties.setProperty("clientSecurityPath", clientSecurityPath);
    }
    if (baseLocation != null) {
      properties.setProperty("baseLocation", baseLocation);
    }
    if (failoverPriority != null) {
      properties.setProperty("failoverPriority", failoverPriority);
    }
    properties.setProperty("serverSecurityEnabled", String.valueOf(serverSecurityEnabled));
    properties.setProperty("clientSecurityEnabled", String.valueOf(clientSecurityEnabled));
    properties.setProperty("offheapCount", String.valueOf(offheapCount));
    properties.setProperty("dataRootCount", String.valueOf(dataRootCount));
    properties.setProperty("serverCount", String.valueOf(serverCount));
    properties.setProperty("stripeCount", String.valueOf(stripeCount));
    properties.setProperty("reconnectWindow", String.valueOf(reconnectWindow));
    
    try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(config), "UTF-8")) {
      properties.store(writer, "AUTO-GENERATED FILE - TinyPounder Settings");
      System.out.println("Settings saved to: " + config.getAbsolutePath());
    } catch (IOException e) {
      System.err.println("FATAL: Failed to save settings to: " + config.getAbsolutePath());
      System.err.println("Error: " + e.getMessage());
      e.printStackTrace();
      throw new UncheckedIOException("Failed to save settings to " + config.getAbsolutePath(), e);
    }
  }

  public File getConfigFile() {
    return config;
  }

  public String getServerSecurityPath() {
    return serverSecurityPath;
  }

  public void setServerSecurityPath(String serverSecurityPath) {
    this.serverSecurityPath = normalizePath(serverSecurityPath);
  }

  public String getClientSecurityPath() {
    return clientSecurityPath;
  }

  public void setClientSecurityPath(String clientSecurityPath) {
    this.clientSecurityPath = normalizePath(clientSecurityPath);
  }

  public String getKitPath() {
    return kitPath;
  }

  public void setKitPath(String kitPath) {
    this.kitPath = normalizePath(kitPath);
  }

  public Optional<String> getLicensePath() {
    return Optional.ofNullable(licensePath);
  }

  public void setLicensePath(String licensePath) {
    this.licensePath = normalizePath(licensePath);
  }

  public int getOffheapCount() {
    return offheapCount;
  }

  public int getDataRootCount() {
    return dataRootCount;
  }

  public int getServerCount() {
    return serverCount;
  }

  public int getStripeCount() {
    return stripeCount;
  }

  public void setOffheapCount(int offheapCount) {
    this.offheapCount = offheapCount;
  }

  public void setDataRootCount(int dataRootCount) {
    this.dataRootCount = dataRootCount;
  }

  public void setStripeCount(int stripeCount) {
    this.stripeCount = stripeCount;
  }

  public void setServerCount(int serverCount) {
    this.serverCount = serverCount;
  }

  public int getReconnectWindow() {
    return reconnectWindow;
  }

  public void setReconnectWindow(int reconnectWindow) {
    this.reconnectWindow = reconnectWindow;
  }

  public String getFailoverPriority() {
    return failoverPriority;
  }

  public String getFailoverPriorityMode() {
    final int i = failoverPriority.indexOf(":");
    return failoverPriority.substring(0, i == -1 ? failoverPriority.length() : i);
  }

  public String getVoterCount() {
    final int i = failoverPriority.indexOf(":");
    return i == -1 ? "1" : failoverPriority.substring(i + 1);
  }

  public void setFailoverPriority(String failoverPriority) {
    this.failoverPriority = failoverPriority;
  }

  public String getBaseLocation() {
    return baseLocation;
  }

  public void setBaseLocation(String baseLocation) {
    this.baseLocation = normalizePath(baseLocation);
  }

  public boolean isServerSecurityEnabled() {
    return serverSecurityEnabled;
  }

  public void setServerSecurityEnabled(boolean serverSecurityEnabled) {
    this.serverSecurityEnabled = serverSecurityEnabled;
  }

  public boolean isClientSecurityEnabled() {
    return clientSecurityEnabled;
  }

  public void setClientSecurityEnabled(boolean clientSecurityEnabled) {
    this.clientSecurityEnabled = clientSecurityEnabled;
  }
}

/*
 * Copyright Terracotta, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terracotta.tinypounder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates Terracotta server configuration files in both XML and properties formats.
 */
public class TerracottaConfigGenerator {

  private static final Logger LOGGER = LoggerFactory.getLogger(TerracottaConfigGenerator.class);

  private final Settings settings;
  private final boolean isEE;
  private final boolean isDynamicConfig;
  private final List<Map<String, Object>> offheapResources;
  private final List<Map<String, Object>> datarootPaths;
  private final List<Map<String, Object>> servers;
  private final Boolean platformPersistence;
  private final Boolean platformBackup;

  public TerracottaConfigGenerator(Settings settings, boolean isEE, boolean isDynamicConfig) {
    this(settings, isEE, isDynamicConfig, null, null, null, null, null);
  }

  public TerracottaConfigGenerator(Settings settings, boolean isEE, boolean isDynamicConfig,
                                   List<Map<String, Object>> offheapResources,
                                   List<Map<String, Object>> datarootPaths) {
    this(settings, isEE, isDynamicConfig, offheapResources, datarootPaths, null, null, null);
  }

  public TerracottaConfigGenerator(Settings settings, boolean isEE, boolean isDynamicConfig,
                                   List<Map<String, Object>> offheapResources,
                                   List<Map<String, Object>> datarootPaths,
                                   List<Map<String, Object>> servers,
                                   Boolean platformPersistence,
                                   Boolean platformBackup) {
    this.settings = settings;
    this.isEE = isEE;
    this.isDynamicConfig = isDynamicConfig;
    this.offheapResources = offheapResources;
    this.datarootPaths = datarootPaths;
    this.servers = servers;
    this.platformPersistence = platformPersistence;
    this.platformBackup = platformBackup;
  }

  /**
   * Generate configuration files and return a map of stripe names to file locations.
   */
  public Map<String, File> generateConfigs() throws IOException {
    return generateConfigs(false);
  }

  /**
   * Generate configuration files and return a map of stripe names to file locations.
   * 
   * @param skipIfExists if true, skip generation if config files already exist
   */
  public Map<String, File> generateConfigs(boolean skipIfExists) throws IOException {
    Map<String, File> configFiles = new HashMap<>();
    
    if (isDynamicConfig) {
      // Generate single properties file for dynamic config
      String filename = "cluster.properties";
      File location = new File(settings.getKitPath(), filename);
      
      // Skip if file exists and skipIfExists is true
      if (skipIfExists && location.exists()) {
        LOGGER.info("Skipping generation - config file already exists: {}", location.getAbsolutePath());
        // All stripes use the same properties file
        for (int stripe = 1; stripe <= settings.getStripeCount(); stripe++) {
          configFiles.put("stripe-" + stripe, location);
        }
        return configFiles;
      }
      
      String properties = generatePropertiesFile();
      Files.write(location.toPath(), properties.getBytes(StandardCharsets.UTF_8));
      
      // All stripes use the same properties file
      for (int stripe = 1; stripe <= settings.getStripeCount(); stripe++) {
        configFiles.put("stripe-" + stripe, location);
      }
      
      LOGGER.info("Generated dynamic config file: {}", location.getAbsolutePath());
    } else {
      // Generate XML configs for each stripe
      for (int stripe = 1; stripe <= settings.getStripeCount(); stripe++) {
        String filename = "tc-config-stripe-" + stripe + ".xml";
        File location = new File(settings.getKitPath(), filename);
        
        // Skip if file exists and skipIfExists is true
        if (skipIfExists && location.exists()) {
          LOGGER.info("Skipping generation - config file already exists: {}", location.getAbsolutePath());
          configFiles.put("stripe-" + stripe, location);
          continue;
        }
        
        String xml = generateXMLForStripe(stripe);
        Files.write(location.toPath(), xml.getBytes(StandardCharsets.UTF_8));
        configFiles.put("stripe-" + stripe, location);
        
        LOGGER.info("Generated XML config file: {}", location.getAbsolutePath());
      }
    }
    
    return configFiles;
  }

  /**
   * Generate XML configuration for a specific stripe.
   */
  private String generateXMLForStripe(int stripeNumber) {
    StringBuilder sb = new StringBuilder();
    
    // XML header
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>").append(System.lineSeparator());
    sb.append(System.lineSeparator());
    
    if (isEE) {
      sb.append("<tc-config xmlns=\"http://www.terracotta.org/config\"").append(System.lineSeparator());
      sb.append("           xmlns:ohr=\"http://www.terracotta.org/config/offheap-resource\"").append(System.lineSeparator());
      sb.append("           xmlns:backup=\"http://www.terracottatech.com/config/backup-restore\"").append(System.lineSeparator());
      sb.append("           xmlns:data=\"http://www.terracottatech.com/config/data-roots\">").append(System.lineSeparator());
    } else {
      sb.append("<tc-config xmlns=\"http://www.terracotta.org/config\"").append(System.lineSeparator());
      sb.append("           xmlns:ohr=\"http://www.terracotta.org/config/offheap-resource\">").append(System.lineSeparator());
    }
    sb.append(System.lineSeparator());
    sb.append("  <plugins>").append(System.lineSeparator());
    sb.append(System.lineSeparator());
    
    // Offheap resources
    if (settings.getOffheapCount() > 0) {
      sb.append("    <config>").append(System.lineSeparator());
      sb.append("      <ohr:offheap-resources>").append(System.lineSeparator());
      
      if (offheapResources != null && !offheapResources.isEmpty()) {
        // Use values from UI
        LOGGER.info("Using {} offheap resources from UI", offheapResources.size());
        for (Map<String, Object> resource : offheapResources) {
          String name = (String) resource.get("name");
          Integer size = (Integer) resource.get("size");
          LOGGER.info("Adding offheap resource: name={}, size={}", name, size);
          sb.append("        <ohr:resource name=\"").append(name).append("\" unit=\"MB\">")
            .append(size).append("</ohr:resource>").append(System.lineSeparator());
        }
      } else {
        // Fallback to defaults
        for (int i = 1; i <= settings.getOffheapCount(); i++) {
          sb.append("        <ohr:resource name=\"offheap-").append(i).append("\" unit=\"MB\">256</ohr:resource>").append(System.lineSeparator());
        }
      }
      
      sb.append("      </ohr:offheap-resources>").append(System.lineSeparator());
      sb.append("    </config>").append(System.lineSeparator());
      sb.append(System.lineSeparator());
    }
    
    // Enterprise Edition features
    if (isEE) {
      // Data roots - only include if platform persistence is enabled or there are data roots
      boolean includePlatformPersistence = platformPersistence == null || platformPersistence;
      boolean includeDataRoots = (datarootPaths != null && !datarootPaths.isEmpty()) || settings.getDataRootCount() > 0;
      
      if (includePlatformPersistence || includeDataRoots) {
        sb.append("    <config>").append(System.lineSeparator());
        sb.append("      <data:data-directories>").append(System.lineSeparator());
        
        // Platform persistence - only if enabled
        if (includePlatformPersistence) {
          String baseLocation = getBaseLocation();
          sb.append("        <data:directory name=\"PLATFORM\" use-for-platform=\"true\">")
            .append(baseLocation).append("/data/platform</data:directory>").append(System.lineSeparator());
        }
        
        // Data roots
        if (datarootPaths != null && !datarootPaths.isEmpty()) {
          // Use values from UI
          for (Map<String, Object> dataroot : datarootPaths) {
            String id = (String) dataroot.get("id");
            String location = (String) dataroot.get("location");
            sb.append("        <data:directory name=\"").append(id).append("\" use-for-platform=\"false\">")
              .append(location).append("</data:directory>").append(System.lineSeparator());
          }
        } else if (includeDataRoots) {
          // Fallback to defaults
          String baseLocation = getBaseLocation();
          for (int i = 1; i <= settings.getDataRootCount(); i++) {
            sb.append("        <data:directory name=\"dataroot-").append(i).append("\" use-for-platform=\"false\">")
              .append(baseLocation).append("/data/dataroot-").append(i).append("</data:directory>").append(System.lineSeparator());
          }
        }
        
        sb.append("      </data:data-directories>").append(System.lineSeparator());
        sb.append("    </config>").append(System.lineSeparator());
        sb.append(System.lineSeparator());
      }
      
      // Backup - only if enabled
      boolean includeBackup = platformBackup == null || platformBackup;
      if (includeBackup) {
        String baseLocation = getBaseLocation();
        sb.append("    <service>").append(System.lineSeparator());
        sb.append("      <backup:backup-restore>").append(System.lineSeparator());
        sb.append("        <backup:backup-location path=\"").append(baseLocation).append("/data/backup\" />").append(System.lineSeparator());
        sb.append("      </backup:backup-restore>").append(System.lineSeparator());
        sb.append("    </service>").append(System.lineSeparator());
        sb.append(System.lineSeparator());
      }
      
      // Security - only if enabled
      if (settings.isServerSecurityEnabled() && 
          settings.getServerSecurityPath() != null && 
          !settings.getServerSecurityPath().isEmpty()) {
        sb.append("    <service xmlns:security=\"http://www.terracottatech.com/config/security\">").append(System.lineSeparator());
        sb.append("      <security:security>").append(System.lineSeparator());
        sb.append("        <security:security-root-directory>").append(settings.getServerSecurityPath()).append("</security:security-root-directory>").append(System.lineSeparator());
        sb.append("        <security:ssl-tls/>").append(System.lineSeparator());
        sb.append("        <security:authentication>").append(System.lineSeparator());
        sb.append("          <security:file/>").append(System.lineSeparator());
        sb.append("        </security:authentication>").append(System.lineSeparator());
        sb.append("      </security:security>").append(System.lineSeparator());
        sb.append("    </service>").append(System.lineSeparator());
        sb.append(System.lineSeparator());
      }
    }
    
    sb.append("  </plugins>").append(System.lineSeparator());
    sb.append(System.lineSeparator());
    
    // Servers
    sb.append("  <servers>").append(System.lineSeparator());
    sb.append(System.lineSeparator());
    
    if (servers != null && !servers.isEmpty()) {
      // Use server configurations from UI
      // Filter servers for this stripe
      for (Map<String, Object> server : servers) {
        String serverName = (String) server.get("name");
        // Check if this server belongs to current stripe
        if (serverName != null && serverName.startsWith("stripe-" + stripeNumber + "-")) {
          String logs = (String) server.get("logs");
          String host = (String) server.get("host");
          Integer port = (Integer) server.get("port");
          Integer groupPort = (Integer) server.get("groupPort");
          
          sb.append("    <server host=\"").append(host).append("\" name=\"").append(serverName).append("\">").append(System.lineSeparator());
          sb.append("      <logs>").append(logs).append("</logs>").append(System.lineSeparator());
          sb.append("      <tsa-port>").append(port).append("</tsa-port>").append(System.lineSeparator());
          sb.append("      <tsa-group-port>").append(groupPort).append("</tsa-group-port>").append(System.lineSeparator());
          sb.append("    </server>").append(System.lineSeparator());
          sb.append(System.lineSeparator());
        }
      }
    } else {
      // Fallback to defaults
      String baseLocation = getBaseLocation();
      for (int server = 1; server <= settings.getServerCount(); server++) {
        String serverName = "stripe-" + stripeNumber + "-server-" + server;
        int clientPort = 9410 + (stripeNumber - 1) * 10 + (server - 1);
        int groupPort = 9530 + (stripeNumber - 1) * 10 + (server - 1);
        
        sb.append("    <server host=\"localhost\" name=\"").append(serverName).append("\">").append(System.lineSeparator());
        sb.append("      <logs>").append(baseLocation).append("/logs/").append(serverName).append("</logs>").append(System.lineSeparator());
        sb.append("      <tsa-port>").append(clientPort).append("</tsa-port>").append(System.lineSeparator());
        sb.append("      <tsa-group-port>").append(groupPort).append("</tsa-group-port>").append(System.lineSeparator());
        sb.append("    </server>").append(System.lineSeparator());
        sb.append(System.lineSeparator());
      }
    }
    
    // Reconnect window
    sb.append("    <client-reconnect-window>").append(settings.getReconnectWindow()).append("</client-reconnect-window>").append(System.lineSeparator());
    sb.append(System.lineSeparator());
    sb.append("  </servers>").append(System.lineSeparator());
    sb.append(System.lineSeparator());
    
    // Failover priority
    if ("consistency".equals(settings.getFailoverPriorityMode())) {
      sb.append("  <failover-priority>").append(System.lineSeparator());
      sb.append("    <consistency>").append(System.lineSeparator());
      sb.append("      <voter count=\"").append(settings.getVoterCount()).append("\"/>").append(System.lineSeparator());
      sb.append("    </consistency>").append(System.lineSeparator());
      sb.append("  </failover-priority>").append(System.lineSeparator());
    } else {
      sb.append("  <failover-priority>").append(System.lineSeparator());
      sb.append("    <availability/>").append(System.lineSeparator());
      sb.append("  </failover-priority>").append(System.lineSeparator());
    }
    sb.append(System.lineSeparator());
    
    sb.append("</tc-config>").append(System.lineSeparator());
    
    return sb.toString();
  }

  /**
   * Get the base location for logs and data.
   */
  private String getBaseLocation() {
    return settings.getBaseLocation();
  }

  /**
   * Generate properties file for dynamic config.
   */
  private String generatePropertiesFile() {
    StringBuilder sb = new StringBuilder();
    
    // Security settings (EE only) - only if enabled
    if (isEE && settings.isServerSecurityEnabled() && 
        settings.getServerSecurityPath() != null && 
        !settings.getServerSecurityPath().isEmpty()) {
      String securityRootDir = settings.getServerSecurityPath();
      if (securityRootDir.endsWith("-ldap")) {
        sb.append("authc=ldap").append(System.lineSeparator());
      } else if (securityRootDir.endsWith("-file")) {
        sb.append("authc=file").append(System.lineSeparator());
      } else {
        sb.append("authc=certificate").append(System.lineSeparator());
      }
      sb.append("ssl-tls=true").append(System.lineSeparator());
    }
    
    // Offheap resources
    if (settings.getOffheapCount() > 0) {
      List<String> offheapList = new ArrayList<>();
      
      if (offheapResources != null && !offheapResources.isEmpty()) {
        // Use values from UI
        LOGGER.info("Using {} offheap resources from UI for properties file", offheapResources.size());
        for (Map<String, Object> resource : offheapResources) {
          String name = (String) resource.get("name");
          Integer size = (Integer) resource.get("size");
          LOGGER.info("Adding offheap resource to properties: name={}, size={}", name, size);
          offheapList.add(name + ":" + size + "MB");
        }
      } else {
        // Fallback to defaults
        for (int i = 1; i <= settings.getOffheapCount(); i++) {
          offheapList.add("offheap-" + i + ":256MB");
        }
      }
      
      sb.append("offheap-resources=").append(String.join(",", offheapList)).append(System.lineSeparator());
    }
    
    // Client reconnect window
    sb.append("client-reconnect-window=").append(settings.getReconnectWindow()).append("s").append(System.lineSeparator());
    
    // Failover priority
    if ("consistency".equals(settings.getFailoverPriorityMode())) {
      sb.append("failover-priority=consistency:").append(settings.getVoterCount()).append(System.lineSeparator());
    } else {
      sb.append("failover-priority=availability").append(System.lineSeparator());
    }
    
    // Stripes and servers
    String baseLocation = getBaseLocation();
    
    // Build server map from UI data if available
    Map<String, Map<String, Object>> serverMap = new HashMap<>();
    if (servers != null && !servers.isEmpty()) {
      for (Map<String, Object> server : servers) {
        String name = (String) server.get("name");
        serverMap.put(name, server);
      }
    }
    
    for (int stripe = 1; stripe <= settings.getStripeCount(); stripe++) {
      sb.append(String.format("stripe.%d.stripe-name=stripe-%d", stripe, stripe)).append(System.lineSeparator());
      
      for (int server = 1; server <= settings.getServerCount(); server++) {
        String serverName = "stripe-" + stripe + "-server-" + server;
        String nodePrefix = "stripe." + stripe + ".node." + server + ".";
        
        // Use server config from UI if available
        Map<String, Object> serverConfig = serverMap.get(serverName);
        if (serverConfig != null) {
          String logs = (String) serverConfig.get("logs");
          String host = (String) serverConfig.get("host");
          Integer port = (Integer) serverConfig.get("port");
          Integer groupPort = (Integer) serverConfig.get("groupPort");
          
          sb.append(nodePrefix).append("name=").append(serverName).append(System.lineSeparator());
          sb.append(nodePrefix).append("hostname=").append(host).append(System.lineSeparator());
          sb.append(nodePrefix).append("port=").append(port).append(System.lineSeparator());
          sb.append(nodePrefix).append("group-port=").append(groupPort).append(System.lineSeparator());
          sb.append(nodePrefix).append("log-dir=").append(logs).append(System.lineSeparator());
        } else {
          // Fallback to defaults
          int clientPort = 9410 + (stripe - 1) * 10 + (server - 1);
          int groupPort = 9530 + (stripe - 1) * 10 + (server - 1);
          
          sb.append(nodePrefix).append("name=").append(serverName).append(System.lineSeparator());
          sb.append(nodePrefix).append("hostname=localhost").append(System.lineSeparator());
          sb.append(nodePrefix).append("port=").append(clientPort).append(System.lineSeparator());
          sb.append(nodePrefix).append("group-port=").append(groupPort).append(System.lineSeparator());
          sb.append(nodePrefix).append("log-dir=").append(baseLocation).append("/logs/").append(serverName).append(System.lineSeparator());
        }
        
        // TC properties for testing
        sb.append(nodePrefix).append("tc-properties=logging.maxBackups:10,logging.maxLogFileSize:256").append(System.lineSeparator());
        sb.append(nodePrefix).append("logger-overrides=org.terracotta.management:INFO,com.terracottatech.management:INFO").append(System.lineSeparator());
        
        // Enterprise Edition features
        if (isEE) {
          // Backup directory - only if enabled
          boolean includeBackup = platformBackup == null || platformBackup;
          if (includeBackup) {
            sb.append(nodePrefix).append("backup-dir=").append(baseLocation).append("/data/backup").append(System.lineSeparator());
          }
          
          // Security - only if enabled
          if (settings.isServerSecurityEnabled() && 
              settings.getServerSecurityPath() != null && 
              !settings.getServerSecurityPath().isEmpty()) {
            sb.append(nodePrefix).append("audit-log-dir=").append(baseLocation).append("/security-audit-log").append(System.lineSeparator());
            sb.append(nodePrefix).append("security-dir=").append(settings.getServerSecurityPath()).append(System.lineSeparator());
          }
          
          // Platform persistence - only if enabled
          boolean includePlatformPersistence = platformPersistence == null || platformPersistence;
          if (includePlatformPersistence) {
            sb.append(nodePrefix).append("metadata-dir=").append(baseLocation).append("/data/platform").append(System.lineSeparator());
          }
          
          // Data roots
          List<String> dataDirList = new ArrayList<>();
          if (datarootPaths != null && !datarootPaths.isEmpty()) {
            // Use values from UI
            for (Map<String, Object> dataroot : datarootPaths) {
              String id = (String) dataroot.get("id");
              String location = (String) dataroot.get("location");
              dataDirList.add(id + ":" + location);
            }
          } else {
            // Fallback to defaults
            for (int i = 1; i <= settings.getDataRootCount(); i++) {
              dataDirList.add("dataroot-" + i + ":" + baseLocation + "/data/dataroot-" + i);
            }
          }
          if (!dataDirList.isEmpty()) {
            sb.append(nodePrefix).append("data-dirs=").append(String.join(",", dataDirList)).append(System.lineSeparator());
          }
        }
      }
    }
    
    return sb.toString();
  }

  /**
   * Get preview of the first generated config file.
   */
  public String getConfigPreview() {
    try {
      if (isDynamicConfig) {
        return generatePropertiesFile();
      } else {
        return generateXMLForStripe(1);
      }
    } catch (Exception e) {
      LOGGER.error("Failed to generate config preview", e);
      return "Error generating config preview: " + e.getMessage();
    }
  }
}

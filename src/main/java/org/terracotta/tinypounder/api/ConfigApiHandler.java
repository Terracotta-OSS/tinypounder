package org.terracotta.tinypounder.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terracotta.tinypounder.KitAwareClassLoaderDelegator;
import org.terracotta.tinypounder.Settings;
import org.terracotta.tinypounder.TerracottaConfigGenerator;
import org.terracotta.tinypounder.model.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API handler for server configuration operations.
 * Endpoints:
 * - POST /api/config/base-location - Set base location
 * - DELETE /api/config/base-location - Delete base location folder
 * - POST /api/config/offheap - Update offheap configuration
 * - POST /api/config/dataroots - Update data roots
 * - POST /api/config/failover - Update failover priority
 * - POST /api/config/servers - Update server configuration
 * - POST /api/config/generate - Generate config files
 * - GET /api/config/preview - Get config file preview
 * - GET /api/config/current - Get current configuration
 * 
 * Note: Security settings are now managed via /api/security endpoints
 */
public class ConfigApiHandler implements ApiHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConfigApiHandler.class);
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public ApiResponse<?> handle(String method, String[] pathParts, HttpServletRequest request, Object... args) 
      throws Exception {
    
    if (args.length < 2) {
      return ApiResponse.error("Invalid arguments");
    }
    
    Settings settings = (Settings) args[0];
    KitAwareClassLoaderDelegator kitClassLoader = (KitAwareClassLoaderDelegator) args[1];
    
    if (pathParts.length < 2) {
      return ApiResponse.error("Invalid path");
    }
    
    String action = pathParts[1];
    
    switch (method) {
      case "POST":
        return handlePost(action, request, settings, kitClassLoader);
      case "DELETE":
        return handleDelete(action, request, settings);
      case "GET":
        return handleGet(action, request, settings, kitClassLoader);
      default:
        return ApiResponse.error("Method not allowed: " + method);
    }
  }

  private ApiResponse<?> handlePost(String action, HttpServletRequest request, 
                                     Settings settings, KitAwareClassLoaderDelegator kitClassLoader) 
      throws Exception {
    
    // Read body for all POST actions
    Map<String, Object> body = readJsonBody(request);
    
    switch (action) {
      case "base-location":
        String baseLocation = (String) body.get("baseLocation");
        LOGGER.info("Received base location update request: {}", baseLocation);
        if (baseLocation == null || baseLocation.trim().isEmpty()) {
          return ApiResponse.error("Base location is required");
        }
        
        File baseDir = new File(baseLocation);
        if (!baseDir.exists()) {
          baseDir.mkdirs();
          LOGGER.info("Created base location directory: {}", baseDir.getAbsolutePath());
        }
        
        settings.setBaseLocation(baseLocation);
        settings.save();
        LOGGER.info("Base location saved to settings: {}", baseLocation);
        return ApiResponse.success("Base location set successfully");
        
      case "offheap":
        Integer offheapCount = (Integer) body.get("offheapCount");
        if (offheapCount != null && offheapCount >= 1 && offheapCount <= 4) {
          settings.setOffheapCount(offheapCount);
          settings.save();
          return ApiResponse.success("Offheap count updated");
        }
        return ApiResponse.error("Invalid offheap count (must be 1-4)");
        
      case "dataroots":
        Integer dataRootCount = (Integer) body.get("dataRootCount");
        if (dataRootCount != null && dataRootCount >= 1 && dataRootCount <= 10) {
          settings.setDataRootCount(dataRootCount);
          settings.save();
          return ApiResponse.success("Data root count updated");
        }
        return ApiResponse.error("Invalid data root count (must be 1-10)");
        
      case "failover":
        String failoverPriority = (String) body.get("failoverPriority");
        if (failoverPriority != null) {
          settings.setFailoverPriority(failoverPriority);
          settings.save();
          return ApiResponse.success("Failover priority updated");
        }
        return ApiResponse.error("Failover priority is required");
        
      case "servers":
        Integer stripeCount = (Integer) body.get("stripeCount");
        Integer serverCount = (Integer) body.get("serverCount");
        Integer reconnectWindow = (Integer) body.get("reconnectWindow");
        
        if (stripeCount != null && stripeCount >= 1 && stripeCount <= 8) {
          settings.setStripeCount(stripeCount);
        }
        if (serverCount != null && serverCount >= 1 && serverCount <= 4) {
          settings.setServerCount(serverCount);
        }
        if (reconnectWindow != null && reconnectWindow >= 5 && reconnectWindow <= 300) {
          settings.setReconnectWindow(reconnectWindow);
        }
        settings.save();
        return ApiResponse.success("Server configuration updated");
        
      case "generate":
        try {
          // Extract all configuration parameters from request body if provided
          List<Map<String, Object>> offheapResources = body != null ? 
            (List<Map<String, Object>>) body.get("offheapResources") : null;
          List<Map<String, Object>> datarootPaths = body != null ? 
            (List<Map<String, Object>>) body.get("datarootPaths") : null;
          List<Map<String, Object>> servers = body != null ? 
            (List<Map<String, Object>>) body.get("servers") : null;
          Boolean platformPersistence = body != null ? 
            (Boolean) body.get("platformPersistence") : null;
          Boolean platformBackup = body != null ? 
            (Boolean) body.get("platformBackup") : null;
          
          LOGGER.info("Generate config request - offheapResources: {}, datarootPaths: {}, servers: {}, platformPersistence: {}, platformBackup: {}", 
            offheapResources, datarootPaths, servers, platformPersistence, platformBackup);
          
          boolean isDynamicConfig = kitClassLoader.isDynamicConfig(settings.getKitPath());
          TerracottaConfigGenerator generator = new TerracottaConfigGenerator(
            settings, kitClassLoader.isEEKit(), isDynamicConfig, 
            offheapResources, datarootPaths, servers, platformPersistence, platformBackup);
          Map<String, java.io.File> configFiles = generator.generateConfigs(false); // Always regenerate when explicitly requested
          
          // Store generated config files in session to avoid regeneration
          request.getSession().setAttribute("tinypounder.configFiles", configFiles);
          
          String configType = isDynamicConfig ? "cluster.properties" : "tc-config-stripe-*.xml";
          return ApiResponse.success("Config files generated successfully: " + configType);
        } catch (Exception e) {
          LOGGER.error("Failed to generate config files", e);
          return ApiResponse.error("Failed to generate config files: " + e.getMessage());
        }
        
      default:
        return ApiResponse.error("Unknown action: " + action);
    }
  }

  private ApiResponse<?> handleDelete(String action, HttpServletRequest request, Settings settings) 
      throws Exception {
    
    switch (action) {
      case "base-location":
        Map<String, Object> body = readJsonBody(request);
        String baseLocation = (String) body.get("baseLocation");
        
        if (baseLocation == null || baseLocation.trim().isEmpty()) {
          return ApiResponse.error("Base location is required");
        }
        
        File baseDir = new File(baseLocation);
        if (!baseDir.exists()) {
          return ApiResponse.error("Base location does not exist");
        }
        
        // Verify it has logs/ and data/ subdirectories for safety
        if (!new File(baseDir, "logs").exists() && !new File(baseDir, "data").exists()) {
          return ApiResponse.error("Base location does not appear to be a TinyPounder directory");
        }
        
        try {
          Path rootPath = baseDir.toPath();
          Files.walk(rootPath, FileVisitOption.FOLLOW_LINKS)
              .sorted(Comparator.reverseOrder())
              .map(Path::toFile)
              .forEach(File::delete);
          return ApiResponse.success("Base location deleted successfully");
        } catch (IOException e) {
          LOGGER.error("Failed to delete base location", e);
          return ApiResponse.error("Failed to delete base location: " + e.getMessage());
        }
        
      default:
        return ApiResponse.error("Unknown action: " + action);
    }
  }

  private ApiResponse<?> handleGet(String action, HttpServletRequest request, Settings settings, KitAwareClassLoaderDelegator kitClassLoader) {
    switch (action) {
      case "current":
        Map<String, Object> config = new HashMap<>();
        config.put("kitPath", settings.getKitPath());
        config.put("licensePath", settings.getLicensePath().orElse(null));
        config.put("baseLocation", settings.getBaseLocation());
        config.put("serverSecurityPath", settings.getServerSecurityPath());
        config.put("clientSecurityPath", settings.getClientSecurityPath());
        config.put("offheapCount", settings.getOffheapCount());
        config.put("dataRootCount", settings.getDataRootCount());
        config.put("serverCount", settings.getServerCount());
        config.put("stripeCount", settings.getStripeCount());
        config.put("reconnectWindow", settings.getReconnectWindow());
        config.put("failoverPriority", settings.getFailoverPriority());
        config.put("failoverPriorityMode", settings.getFailoverPriorityMode());
        config.put("voterCount", settings.getVoterCount());
        
        return ApiResponse.success(config);
        
      case "preview":
        try {
          // Check if config files were already generated in this session
          @SuppressWarnings("unchecked")
          Map<String, java.io.File> configFiles = (Map<String, java.io.File>) 
            request.getSession().getAttribute("tinypounder.configFiles");
          
          if (configFiles != null && !configFiles.isEmpty()) {
            // Read from the first generated config file
            java.io.File firstConfig = configFiles.values().iterator().next();
            if (firstConfig != null && firstConfig.exists()) {
              try {
                String preview = new String(java.nio.file.Files.readAllBytes(firstConfig.toPath()), 
                  java.nio.charset.StandardCharsets.UTF_8);
                LOGGER.info("Using generated config file for preview: {}", firstConfig.getName());
                return ApiResponse.success((Object) preview, "Config preview from generated file");
              } catch (java.io.IOException e) {
                LOGGER.warn("Failed to read generated config file, falling back to regeneration", e);
              }
            }
          }
          
          // Fallback: generate preview without UI values (for initial display before generation)
          boolean isDynamicConfig = kitClassLoader.isDynamicConfig(settings.getKitPath());
          TerracottaConfigGenerator generator = new TerracottaConfigGenerator(settings, kitClassLoader.isEEKit(), isDynamicConfig);
          String preview = generator.getConfigPreview();
          LOGGER.info("Generated config preview from defaults, length: {}", preview != null ? preview.length() : "null");
          return ApiResponse.success((Object) preview, "Config preview generated");
        } catch (Exception e) {
          LOGGER.error("Failed to generate config preview", e);
          return ApiResponse.error("Failed to generate config preview: " + e.getMessage());
        }
        
      default:
        return ApiResponse.error("Unknown action: " + action);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> readJsonBody(HttpServletRequest request) throws Exception {
    try {
      // Check if there's actually content to read
      if (request.getContentLength() <= 0) {
        return new HashMap<>();
      }
      return objectMapper.readValue(request.getInputStream(), Map.class);
    } catch (IOException e) {
      // Return empty map if no body or invalid JSON
      LOGGER.debug("No JSON body in request", e);
      return new HashMap<>();
    }
  }
}

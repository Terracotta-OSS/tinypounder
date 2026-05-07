package org.terracotta.tinypounder.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terracotta.tinypounder.KitAwareClassLoaderDelegator;
import org.terracotta.tinypounder.Settings;
import org.terracotta.tinypounder.model.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * API handler for kit-related operations.
 * Endpoints:
 * - POST /api/kit/path - Set and validate kit path
 * - POST /api/kit/license - Set license path
 * - GET /api/kit/info - Get kit type and capabilities
 */
public class KitApiHandler implements ApiHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(KitApiHandler.class);

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
      case "GET":
        return handleGet(action, settings, kitClassLoader);
      default:
        return ApiResponse.error("Method not allowed: " + method);
    }
  }

  private ApiResponse<?> handlePost(String action, HttpServletRequest request, 
                                     Settings settings, KitAwareClassLoaderDelegator kitClassLoader) 
      throws Exception {
    
    Map<String, String> body = readJsonBody(request);
    
    switch (action) {
      case "path":
        String kitPath = body.get("kitPath");
        if (kitPath == null || kitPath.trim().isEmpty()) {
          return ApiResponse.error("Kit path is required");
        }
        
        File kitDir = new File(kitPath);
        if (!kitDir.exists() || !kitDir.isDirectory()) {
          return ApiResponse.error("Kit path does not exist or is not a directory");
        }
        
        try {
          kitClassLoader.setAndVerifyKitPathAndClassLoader(kitPath);
          settings.setKitPath(kitPath);
          settings.save();
          
          Map<String, Object> result = new HashMap<>();
          result.put("kitPath", kitPath);
          result.put("isEE", kitClassLoader.isEEKit());
          result.put("hasEhcache", kitClassLoader.containsEhcache());
          result.put("hasTCStore", kitClassLoader.containsTerracottaStore());
          
          return ApiResponse.success(result, "Kit path set successfully");
        } catch (Exception e) {
          LOGGER.error("Failed to set kit path", e);
          return ApiResponse.error("Failed to set kit path: " + e.getMessage());
        }
        
      case "license":
        String licensePath = body.get("licensePath");
        if (licensePath == null || licensePath.trim().isEmpty()) {
          return ApiResponse.error("License path is required");
        }
        
        File licenseFile = new File(licensePath);
        if (!licenseFile.exists() || !licenseFile.isFile()) {
          return ApiResponse.error("License file does not exist");
        }
        
        settings.setLicensePath(licensePath);
        settings.save();
        
        return ApiResponse.success("License path set successfully");
        
      default:
        return ApiResponse.error("Unknown action: " + action);
    }
  }

  private ApiResponse<?> handleGet(String action, Settings settings, 
                                    KitAwareClassLoaderDelegator kitClassLoader) {
    
    switch (action) {
      case "info":
        Map<String, Object> info = new HashMap<>();
        info.put("kitPath", settings.getKitPath());
        info.put("licensePath", settings.getLicensePath().orElse(null));
        info.put("isEE", kitClassLoader.isEEKit());
        info.put("hasEhcache", kitClassLoader.containsEhcache());
        info.put("hasTCStore", kitClassLoader.containsTerracottaStore());
        
        return ApiResponse.success(info);
        
      default:
        return ApiResponse.error("Unknown action: " + action);
    }
  }

  private Map<String, String> readJsonBody(HttpServletRequest request) throws Exception {
    StringBuilder sb = new StringBuilder();
    try (BufferedReader reader = request.getReader()) {
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line);
      }
    }
    
    // Simple JSON parsing for key-value pairs
    Map<String, String> result = new HashMap<>();
    String json = sb.toString().trim();
    if (json.startsWith("{") && json.endsWith("}")) {
      json = json.substring(1, json.length() - 1);
      String[] pairs = json.split(",");
      for (String pair : pairs) {
        String[] kv = pair.split(":", 2);
        if (kv.length == 2) {
          String key = kv[0].trim().replaceAll("\"", "");
          String value = kv[1].trim().replaceAll("\"", "");
          result.put(key, value);
        }
      }
    }
    return result;
  }
}

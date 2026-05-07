package org.terracotta.tinypounder.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terracotta.tinypounder.KitAwareClassLoaderDelegator;
import org.terracotta.tinypounder.Settings;
import org.terracotta.tinypounder.model.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * API handler for security-related operations.
 * Endpoints:
 * - POST /api/security/client - Set client security configuration
 * - GET /api/security/client - Get client security configuration
 */
public class SecurityApiHandler implements ApiHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(SecurityApiHandler.class);
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
      case "GET":
        return handleGet(action, settings);
      default:
        return ApiResponse.error("Method not allowed: " + method);
    }
  }

  private ApiResponse<?> handlePost(String action, HttpServletRequest request, 
                                     Settings settings, KitAwareClassLoaderDelegator kitClassLoader) 
      throws Exception {
    
    Map<String, Object> body = readJsonBody(request);
    
    switch (action) {
      case "server":
        Boolean serverEnabled = (Boolean) body.get("enabled");
        String serverSecurityPath = (String) body.get("serverSecurityPath");
        
        settings.setServerSecurityEnabled(serverEnabled != null && serverEnabled);
        
        if (serverSecurityPath != null && !serverSecurityPath.trim().isEmpty()) {
          if (!kitClassLoader.verifySecurityPath(serverSecurityPath)) {
            return ApiResponse.error("Invalid server security path");
          }
          settings.setServerSecurityPath(serverSecurityPath);
        }
        
        settings.save();
        return ApiResponse.success("Server security configuration saved");
        
      case "client":
        Boolean clientEnabled = (Boolean) body.get("enabled");
        String clientSecurityPath = (String) body.get("clientSecurityPath");
        
        settings.setClientSecurityEnabled(clientEnabled != null && clientEnabled);
        
        if (clientSecurityPath != null && !clientSecurityPath.trim().isEmpty()) {
          if (!kitClassLoader.verifySecurityPath(clientSecurityPath)) {
            return ApiResponse.error("Invalid client security path");
          }
          settings.setClientSecurityPath(clientSecurityPath);
        }
        
        settings.save();
        return ApiResponse.success("Client security configuration saved");
        
      default:
        return ApiResponse.error("Unknown action: " + action);
    }
  }

  private ApiResponse<?> handleGet(String action, Settings settings) {
    switch (action) {
      case "server":
        Map<String, Object> serverConfig = new HashMap<>();
        serverConfig.put("enabled", settings.isServerSecurityEnabled());
        serverConfig.put("serverSecurityPath", 
            settings.getServerSecurityPath() != null ? settings.getServerSecurityPath() : "");
        return ApiResponse.success(serverConfig);
        
      case "client":
        Map<String, Object> clientConfig = new HashMap<>();
        clientConfig.put("enabled", settings.isClientSecurityEnabled());
        clientConfig.put("clientSecurityPath", 
            settings.getClientSecurityPath() != null ? settings.getClientSecurityPath() : "");
        return ApiResponse.success(clientConfig);
        
      default:
        return ApiResponse.error("Unknown action: " + action);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> readJsonBody(HttpServletRequest request) throws Exception {
    return objectMapper.readValue(request.getInputStream(), Map.class);
  }
}

package org.terracotta.tinypounder.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terracotta.tinypounder.CacheManagerBusiness;
import org.terracotta.tinypounder.KitAwareClassLoaderDelegator;
import org.terracotta.tinypounder.Settings;
import org.terracotta.tinypounder.model.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * API handler for cache manager operations.
 * Endpoints:
 * - POST /api/cachemanager/init - Initialize cache manager
 * - POST /api/cachemanager/close - Close cache manager
 * - POST /api/cachemanager/destroy - Destroy cache manager
 * - GET /api/cachemanager/status - Get cache manager status
 * - GET /api/cachemanager/config - Get cache manager configuration
 */
public class CacheManagerApiHandler implements ApiHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(CacheManagerApiHandler.class);
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public ApiResponse<?> handle(String method, String[] pathParts, HttpServletRequest request, Object... args) 
      throws Exception {
    
    if (args.length < 2) {
      return ApiResponse.error("Invalid arguments");
    }
    
    CacheManagerBusiness cacheManagerBusiness = (CacheManagerBusiness) args[0];
    KitAwareClassLoaderDelegator kitClassLoader = (KitAwareClassLoaderDelegator) args[1];
    
    if (pathParts.length < 2) {
      return ApiResponse.error("Invalid path");
    }
    
    String action = pathParts[1];
    
    switch (method) {
      case "POST":
        return handlePost(action, request, cacheManagerBusiness, kitClassLoader);
      case "GET":
        return handleGet(action, cacheManagerBusiness, request);
      default:
        return ApiResponse.error("Method not allowed: " + method);
    }
  }

  private ApiResponse<?> handlePost(String action, HttpServletRequest request,
                                     CacheManagerBusiness cacheManagerBusiness,
                                     KitAwareClassLoaderDelegator kitClassLoader) throws Exception {
    
    Map<String, Object> body = readJsonBody(request);
    
    switch (action) {
      case "init":
        String terracottaUrl = (String) body.get("terracottaUrl");
        String clusterTierManagerName = (String) body.get("clusterTierManagerName");
        String diskPersistenceLocation = (String) body.get("diskPersistenceLocation");
        String offheapResourceName = (String) body.get("offheapResourceName");
        String serverDiskResourceName = (String) body.get("serverDiskResourceName");
        String clientSecurityPath = (String) body.get("clientSecurityPath");
        
        try {
          cacheManagerBusiness.initializeCacheManager(
              terracottaUrl,
              clusterTierManagerName,
              diskPersistenceLocation,
              offheapResourceName,
              serverDiskResourceName,
              clientSecurityPath
          );
          
          Map<String, Object> result = new HashMap<>();
          result.put("status", cacheManagerBusiness.getStatus());
          result.put("config", cacheManagerBusiness.retrieveHumanReadableConfiguration());
          
          return ApiResponse.success(result, "Cache manager initialized successfully");
        } catch (Exception e) {
          LOGGER.error("Failed to initialize cache manager", e);
          
          // Find the root cause (deepest cause in the chain)
          Throwable rootCause = e;
          while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
          }
          
          // Build error message with root cause first, then full chain
          StringBuilder errorMsg = new StringBuilder();
          errorMsg.append(rootCause.getMessage());
          
          // Add full cause chain for context (up to 5 levels)
          if (rootCause != e) {
            errorMsg.append("\n\nFull error chain:");
            Throwable cause = e;
            int depth = 0;
            while (cause != null && depth < 5) {
              errorMsg.append("\n").append(depth + 1).append(". ").append(cause.getClass().getSimpleName())
                      .append(": ").append(cause.getMessage());
              cause = cause.getCause();
              depth++;
            }
          }
          
          return ApiResponse.error("Failed to initialize cache manager: " + errorMsg.toString());
        }
        
      case "close":
        try {
          cacheManagerBusiness.close();
          return ApiResponse.success("Cache manager closed successfully");
        } catch (Exception e) {
          LOGGER.error("Failed to close cache manager", e);
          return ApiResponse.error("Failed to close cache manager: " + e.getMessage());
        }
        
      case "destroy":
        try {
          cacheManagerBusiness.destroy();
          return ApiResponse.success("Cache manager destroyed successfully");
        } catch (Exception e) {
          LOGGER.error("Failed to destroy cache manager", e);
          return ApiResponse.error("Failed to destroy cache manager: " + e.getMessage());
        }
        
      default:
        return ApiResponse.error("Unknown action: " + action);
    }
  }

  private ApiResponse<?> handleGet(String action, CacheManagerBusiness cacheManagerBusiness,
                                    HttpServletRequest request) {
    switch (action) {
      case "status":
        String currentStatus = cacheManagerBusiness.getStatus();
        
        Map<String, Object> status = new HashMap<>();
        status.put("status", currentStatus);
        status.put("alive", cacheManagerBusiness.isCacheManagerAlive());
        return ApiResponse.success(status);
        
      case "config":
        if (!cacheManagerBusiness.isCacheManagerAlive()) {
          return ApiResponse.error("Cache manager is not alive");
        }
        
        Map<String, Object> config = new HashMap<>();
        config.put("config", cacheManagerBusiness.retrieveHumanReadableConfiguration());
        return ApiResponse.success(config);
        
      default:
        return ApiResponse.error("Unknown action: " + action);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> readJsonBody(HttpServletRequest request) throws Exception {
    return objectMapper.readValue(request.getInputStream(), Map.class);
  }
}

package org.terracotta.tinypounder.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terracotta.tinypounder.CacheConfiguration;
import org.terracotta.tinypounder.CacheManagerBusiness;
import org.terracotta.tinypounder.model.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API handler for cache operations.
 * Endpoints:
 * - POST /api/cache/create - Create cache
 * - DELETE /api/cache/{name} - Remove cache
 * - POST /api/cache/{name}/clear - Clear cache
 * - POST /api/cache/{name}/destroy - Destroy cache
 * - POST /api/cache/{name}/pound - Update pounding intensity
 * - GET /api/cache/list - List all caches
 */
public class CacheApiHandler implements ApiHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(CacheApiHandler.class);
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public ApiResponse<?> handle(String method, String[] pathParts, HttpServletRequest request, Object... args) 
      throws Exception {
    
    if (args.length < 1) {
      return ApiResponse.error("Invalid arguments");
    }
    
    CacheManagerBusiness cacheManagerBusiness = (CacheManagerBusiness) args[0];
    
    if (pathParts.length < 2) {
      return ApiResponse.error("Invalid path");
    }
    
    switch (method) {
      case "POST":
        return handlePost(pathParts, request, cacheManagerBusiness);
      case "DELETE":
        return handleDelete(pathParts, cacheManagerBusiness);
      case "GET":
        return handleGet(pathParts, cacheManagerBusiness);
      default:
        return ApiResponse.error("Method not allowed: " + method);
    }
  }

  private ApiResponse<?> handlePost(String[] pathParts, HttpServletRequest request,
                                     CacheManagerBusiness cacheManagerBusiness) throws Exception {
    
    Map<String, Object> body = readJsonBody(request);
    
    if (pathParts.length == 2 && pathParts[1].equals("create")) {
      // POST /api/cache/create
      String cacheName = (String) body.get("cacheName");
      if (cacheName == null || cacheName.trim().isEmpty()) {
        return ApiResponse.error("Cache name is required");
      }
      
      // Parse cache configuration
      Long onHeapSize = getLong(body, "onHeapSize", 50L);
      String onHeapUnit = (String) body.getOrDefault("onHeapUnit", "ENTRIES");
      Long offHeapSize = getLong(body, "offHeapSize", 1L);
      String offHeapUnit = (String) body.getOrDefault("offHeapUnit", "MB");
      Long diskSize = getLong(body, "diskSize", 0L);
      String diskUnit = (String) body.getOrDefault("diskUnit", "MB");
      Integer clusteredDedicatedSize = getInteger(body, "clusteredDedicatedSize", 0);
      String clusteredDedicatedUnit = (String) body.getOrDefault("clusteredDedicatedUnit", "MB");
      String clusteredSharedPoolName = (String) body.get("clusteredSharedPoolName");
      String clusterTierType = (String) body.getOrDefault("clusterTierType", "NONE");
      
      CacheConfiguration.ClusterTierType tierType = CacheConfiguration.ClusterTierType.valueOf(clusterTierType);
      
      CacheConfiguration config = new CacheConfiguration(
          onHeapSize, onHeapUnit,
          offHeapSize, offHeapUnit,
          diskSize, diskUnit,
          clusteredDedicatedSize, clusteredDedicatedUnit,
          clusteredSharedPoolName, tierType
      );
      
      try {
        cacheManagerBusiness.createCache(cacheName, config);
        return ApiResponse.success("Cache created successfully");
      } catch (Exception e) {
        LOGGER.error("Failed to create cache", e);
        return ApiResponse.error("Failed to create cache: " + e.getMessage());
      }
      
    } else if (pathParts.length == 3) {
      String cacheName = pathParts[1];
      String action = pathParts[2];
      
      switch (action) {
        case "clear":
          try {
            cacheManagerBusiness.clearCache(cacheName);
            return ApiResponse.success("Cache cleared successfully");
          } catch (Exception e) {
            LOGGER.error("Failed to clear cache", e);
            return ApiResponse.error("Failed to clear cache: " + e.getMessage());
          }
          
        case "destroy":
          try {
            cacheManagerBusiness.destroyCache(cacheName);
            return ApiResponse.success("Cache destroyed successfully");
          } catch (Exception e) {
            LOGGER.error("Failed to destroy cache", e);
            return ApiResponse.error("Failed to destroy cache: " + e.getMessage());
          }
          
        case "pound":
          Integer intensity = getInteger(body, "intensity", 0);
          if (intensity < 0 || intensity > 11) {
            return ApiResponse.error("Pounding intensity must be between 0 and 11");
          }
          
          try {
            cacheManagerBusiness.updatePoundingIntensity(cacheName, intensity);
            return ApiResponse.success("Pounding intensity updated");
          } catch (Exception e) {
            LOGGER.error("Failed to update pounding intensity", e);
            return ApiResponse.error("Failed to update pounding intensity: " + e.getMessage());
          }
          
        default:
          return ApiResponse.error("Unknown action: " + action);
      }
    }
    
    return ApiResponse.error("Invalid path");
  }

  private ApiResponse<?> handleDelete(String[] pathParts, CacheManagerBusiness cacheManagerBusiness) {
    if (pathParts.length < 2) {
      return ApiResponse.error("Cache name is required");
    }
    
    String cacheName = pathParts[1];
    
    try {
      cacheManagerBusiness.removeCache(cacheName);
      return ApiResponse.success("Cache removed successfully");
    } catch (Exception e) {
      LOGGER.error("Failed to remove cache", e);
      return ApiResponse.error("Failed to remove cache: " + e.getMessage());
    }
  }

  private ApiResponse<?> handleGet(String[] pathParts, CacheManagerBusiness cacheManagerBusiness) {
    if (pathParts.length == 2 && pathParts[1].equals("list")) {
      // Check if cache manager is alive before listing caches
      if (!cacheManagerBusiness.isCacheManagerAlive()) {
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("caches", new ArrayList<>());
        return ApiResponse.success(responseData);
      }
      
      List<Map<String, Object>> caches = new ArrayList<>();
      
      for (String cacheName : cacheManagerBusiness.retrieveCacheNames()) {
        Map<String, Object> cacheInfo = new HashMap<>();
        cacheInfo.put("name", cacheName);
        cacheInfo.put("poundingIntensity", cacheManagerBusiness.retrievePoundingIntensity(cacheName));
        caches.add(cacheInfo);
      }
      
      Map<String, Object> responseData = new HashMap<>();
      responseData.put("caches", caches);
      return ApiResponse.success(responseData);
    }
    
    return ApiResponse.error("Invalid path");
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> readJsonBody(HttpServletRequest request) throws Exception {
    return objectMapper.readValue(request.getInputStream(), Map.class);
  }

  private Long getLong(Map<String, Object> map, String key, Long defaultValue) {
    Object value = map.get(key);
    // Java 21 pattern matching for instanceof
    if (value instanceof Number num) {
      return num.longValue();
    }
    return defaultValue;
  }

  private Integer getInteger(Map<String, Object> map, String key, Integer defaultValue) {
    Object value = map.get(key);
    // Java 21 pattern matching for instanceof
    if (value instanceof Number num) {
      return num.intValue();
    }
    return defaultValue;
  }
}

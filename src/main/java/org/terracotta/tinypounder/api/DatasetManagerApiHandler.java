package org.terracotta.tinypounder.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terracotta.tinypounder.DatasetManagerBusinessReflectionImpl;
import org.terracotta.tinypounder.KitAwareClassLoaderDelegator;
import org.terracotta.tinypounder.Settings;
import org.terracotta.tinypounder.model.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * API handler for dataset manager operations (Enterprise Edition only).
 * Endpoints:
 * - POST /api/datasetmanager/init - Initialize dataset manager
 * - POST /api/datasetmanager/close - Close dataset manager
 * - GET /api/datasetmanager/status - Get dataset manager status
 */
public class DatasetManagerApiHandler implements ApiHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(DatasetManagerApiHandler.class);
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public ApiResponse<?> handle(String method, String[] pathParts, HttpServletRequest request, Object... args) 
      throws Exception {
    
    if (args.length < 2) {
      return ApiResponse.error("Invalid arguments");
    }
    
    DatasetManagerBusinessReflectionImpl datasetManagerBusiness = (DatasetManagerBusinessReflectionImpl) args[0];
    KitAwareClassLoaderDelegator kitClassLoader = (KitAwareClassLoaderDelegator) args[1];
    
    if (!kitClassLoader.containsTerracottaStore()) {
      return ApiResponse.error("TCStore is not available in this kit");
    }
    
    if (pathParts.length < 2) {
      return ApiResponse.error("Invalid path");
    }
    
    String action = pathParts[1];
    
    switch (method) {
      case "POST":
        return handlePost(action, request, datasetManagerBusiness);
      case "GET":
        return handleGet(action, datasetManagerBusiness, request);
      default:
        return ApiResponse.error("Method not allowed: " + method);
    }
  }

  private ApiResponse<?> handlePost(String action, HttpServletRequest request,
                                     DatasetManagerBusinessReflectionImpl datasetManagerBusiness) throws Exception {
    
    Map<String, Object> body = readJsonBody(request);
    
    switch (action) {
      case "init":
        String terracottaUrl = (String) body.get("terracottaUrl");
        String clientSecurityPath = (String) body.get("clientSecurityPath");
        
        try {
          datasetManagerBusiness.initializeDatasetManager(terracottaUrl, clientSecurityPath);
          
          Map<String, Object> result = new HashMap<>();
          result.put("status", datasetManagerBusiness.getStatus());
          
          return ApiResponse.success(result, "Dataset manager initialized successfully");
        } catch (Exception e) {
          LOGGER.error("Failed to initialize dataset manager", e);
          return ApiResponse.error("Failed to initialize dataset manager: " + e.getMessage());
        }
        
      case "close":
        try {
          datasetManagerBusiness.close();
          return ApiResponse.success("Dataset manager closed successfully");
        } catch (Exception e) {
          LOGGER.error("Failed to close dataset manager", e);
          return ApiResponse.error("Failed to close dataset manager: " + e.getMessage());
        }
        
      default:
        return ApiResponse.error("Unknown action: " + action);
    }
  }

  private ApiResponse<?> handleGet(String action, DatasetManagerBusinessReflectionImpl datasetManagerBusiness,
                                    HttpServletRequest request) {
    switch (action) {
      case "status":
        String currentStatus = datasetManagerBusiness.getStatus();
        
        Map<String, Object> status = new HashMap<>();
        status.put("status", currentStatus);
        status.put("alive", datasetManagerBusiness.isDatasetManagerAlive());
        return ApiResponse.success(status);
        
      default:
        return ApiResponse.error("Unknown action: " + action);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> readJsonBody(HttpServletRequest request) throws Exception {
    return objectMapper.readValue(request.getInputStream(), Map.class);
  }
}

package org.terracotta.tinypounder.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terracotta.tinypounder.DatasetConfiguration;
import org.terracotta.tinypounder.DatasetManagerBusinessReflectionImpl;
import org.terracotta.tinypounder.model.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API handler for dataset operations (Enterprise Edition only).
 * Endpoints:
 * - POST /api/dataset/create - Create dataset
 * - DELETE /api/dataset/{name} - Remove dataset
 * - POST /api/dataset/{name}/destroy - Destroy dataset
 * - POST /api/dataset/{name}/pound - Update pounding intensity
 * - GET /api/dataset/list - List all datasets
 */
public class DatasetApiHandler implements ApiHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(DatasetApiHandler.class);
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public ApiResponse<?> handle(String method, String[] pathParts, HttpServletRequest request, Object... args) 
      throws Exception {
    
    if (args.length < 1) {
      return ApiResponse.error("Invalid arguments");
    }
    
    DatasetManagerBusinessReflectionImpl datasetManagerBusiness = (DatasetManagerBusinessReflectionImpl) args[0];
    
    if (pathParts.length < 2) {
      return ApiResponse.error("Invalid path");
    }
    
    switch (method) {
      case "POST":
        return handlePost(pathParts, request, datasetManagerBusiness);
      case "DELETE":
        return handleDelete(pathParts, datasetManagerBusiness);
      case "GET":
        return handleGet(pathParts, datasetManagerBusiness);
      default:
        return ApiResponse.error("Method not allowed: " + method);
    }
  }

  private ApiResponse<?> handlePost(String[] pathParts, HttpServletRequest request,
                                     DatasetManagerBusinessReflectionImpl datasetManagerBusiness) throws Exception {
    
    if (pathParts.length == 2 && pathParts[1].equals("create")) {
      Map<String, Object> body = readJsonBody(request);
      // POST /api/dataset/create
      String datasetName = (String) body.get("datasetName");
      if (datasetName == null || datasetName.trim().isEmpty()) {
        return ApiResponse.error("Dataset name is required");
      }
      
      // Parse dataset configuration
      String keyType = (String) body.getOrDefault("keyType", "STRING");
      String offheapResourceName = (String) body.getOrDefault("offheapResourceName", "offheap-1");
      String diskResourceName = (String) body.get("diskResourceName");
      Boolean useIndex = (Boolean) body.getOrDefault("useIndex", false);
      
      DatasetConfiguration config = new DatasetConfiguration(
          keyType,
          offheapResourceName,
          diskResourceName,
          useIndex != null ? useIndex : false
      );
      
      try {
        datasetManagerBusiness.createDataset(datasetName, config);
        return ApiResponse.success("Dataset created successfully");
      } catch (Exception e) {
        LOGGER.error("Failed to create dataset", e);
        return ApiResponse.error("Failed to create dataset: " + e.getMessage());
      }
      
    } else if (pathParts.length == 3) {
      String datasetName = pathParts[1];
      String action = pathParts[2];
      
      switch (action) {
        case "destroy":
          try {
            datasetManagerBusiness.destroyDataset(datasetName);
            return ApiResponse.success("Dataset destroyed successfully");
          } catch (Exception e) {
            LOGGER.error("Failed to destroy dataset", e);
            return ApiResponse.error("Failed to destroy dataset: " + e.getMessage());
          }
          
        case "pound":
          Map<String, Object> poundBody = readJsonBody(request);
          Integer intensity = getInteger(poundBody, "intensity", 0);
          if (intensity < 0 || intensity > 11) {
            return ApiResponse.error("Pounding intensity must be between 0 and 11");
          }
          
          try {
            // datasetName here is actually the instance name (e.g., "FooDS-1")
            // Update pounding intensity for this specific instance
            datasetManagerBusiness.updatePoundingIntensity(datasetName, intensity);
            return ApiResponse.success("Pounding intensity updated");
          } catch (Exception e) {
            LOGGER.error("Failed to update pounding intensity", e);
            return ApiResponse.error("Failed to update pounding intensity: " + e.getMessage());
          }
          
        case "createInstance":
          try {
            String instanceName = datasetManagerBusiness.createDatasetInstance(datasetName);
            Map<String, Object> result = new HashMap<>();
            result.put("instanceName", instanceName);
            return ApiResponse.success(result, "Dataset instance created successfully");
          } catch (Exception e) {
            LOGGER.error("Failed to create dataset instance", e);
            return ApiResponse.error("Failed to create dataset instance: " + e.getMessage());
          }
          
        case "closeInstance":
          Map<String, Object> closeBody = readJsonBody(request);
          String instanceName = (String) closeBody.get("instanceName");
          if (instanceName == null || instanceName.trim().isEmpty()) {
            return ApiResponse.error("Instance name is required");
          }
          try {
            datasetManagerBusiness.closeDatasetInstance(datasetName, instanceName);
            return ApiResponse.success("Dataset instance closed successfully");
          } catch (Exception e) {
            LOGGER.error("Failed to close dataset instance", e);
            return ApiResponse.error("Failed to close dataset instance: " + e.getMessage());
          }
          
        case "addCell":
          Map<String, Object> addCellBody = readJsonBody(request);
          String cellToAdd = (String) addCellBody.get("cell");
          if (cellToAdd == null || cellToAdd.trim().isEmpty()) {
            return ApiResponse.error("Cell value is required");
          }
          try {
            datasetManagerBusiness.addCustomCell(cellToAdd);
            return ApiResponse.success("Cell added successfully");
          } catch (Exception e) {
            LOGGER.error("Failed to add cell", e);
            return ApiResponse.error("Failed to add cell: " + e.getMessage());
          }
          
        case "removeCell":
          Map<String, Object> removeCellBody = readJsonBody(request);
          String cellToRemove = (String) removeCellBody.get("cell");
          if (cellToRemove == null || cellToRemove.trim().isEmpty()) {
            return ApiResponse.error("Cell value is required");
          }
          try {
            datasetManagerBusiness.removeCustomCell(cellToRemove);
            return ApiResponse.success("Cell removed successfully");
          } catch (Exception e) {
            LOGGER.error("Failed to remove cell", e);
            return ApiResponse.error("Failed to remove cell: " + e.getMessage());
          }
          
        default:
          return ApiResponse.error("Unknown action: " + action);
      }
    }
    
    return ApiResponse.error("Invalid path");
  }

  private ApiResponse<?> handleDelete(String[] pathParts, DatasetManagerBusinessReflectionImpl datasetManagerBusiness) {
    if (pathParts.length < 2) {
      return ApiResponse.error("Dataset name is required");
    }
    
    String datasetName = pathParts[1];
    
    try {
      datasetManagerBusiness.destroyDataset(datasetName);
      return ApiResponse.success("Dataset removed successfully");
    } catch (Exception e) {
      LOGGER.error("Failed to remove dataset", e);
      return ApiResponse.error("Failed to remove dataset: " + e.getMessage());
    }
  }

  private ApiResponse<?> handleGet(String[] pathParts, DatasetManagerBusinessReflectionImpl datasetManagerBusiness) {
    if (pathParts.length == 2 && pathParts[1].equals("list")) {
      List<Map<String, Object>> datasets = new ArrayList<>();
      
      for (String datasetName : datasetManagerBusiness.retrieveDatasetNames()) {
        Map<String, Object> datasetInfo = new HashMap<>();
        datasetInfo.put("name", datasetName);
        
        // Get instances for this dataset
        List<Map<String, Object>> instances = new ArrayList<>();
        for (String instanceName : datasetManagerBusiness.getDatasetInstanceNames(datasetName)) {
          Map<String, Object> instanceInfo = new HashMap<>();
          instanceInfo.put("name", instanceName);
          instanceInfo.put("poundingIntensity", datasetManagerBusiness.retrievePoundingIntensity(instanceName));
          instances.add(instanceInfo);
        }
        datasetInfo.put("instances", instances);
        datasets.add(datasetInfo);
      }
      
      Map<String, Object> responseData = new HashMap<>();
      responseData.put("datasets", datasets);
      return ApiResponse.success(responseData);
    }
    
    return ApiResponse.error("Invalid path");
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> readJsonBody(HttpServletRequest request) throws Exception {
    return objectMapper.readValue(request.getInputStream(), Map.class);
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

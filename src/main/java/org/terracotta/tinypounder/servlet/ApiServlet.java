package org.terracotta.tinypounder.servlet;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terracotta.tinypounder.CacheManagerBusiness;
import org.terracotta.tinypounder.DatasetManagerBusinessReflectionImpl;
import org.terracotta.tinypounder.KitAwareClassLoaderDelegator;
import org.terracotta.tinypounder.Settings;
import org.terracotta.tinypounder.TerracottaServerBusiness;
import org.terracotta.tinypounder.api.*;
import org.terracotta.tinypounder.model.ApiResponse;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Main API servlet that routes requests to appropriate handlers.
 * Handles all REST API endpoints under /api/*
 * Configured in web.xml with URL pattern /api/*
 */
public class ApiServlet extends HttpServlet {

  private static final Logger LOGGER = LoggerFactory.getLogger(ApiServlet.class);
  private final ObjectMapper objectMapper = new ObjectMapper();

  private KitApiHandler kitApiHandler;
  private ConfigApiHandler configApiHandler;
  private ServerApiHandler serverApiHandler;
  private ClusterApiHandler clusterApiHandler;
  private CacheManagerApiHandler cacheManagerApiHandler;
  private CacheApiHandler cacheApiHandler;
  private DatasetManagerApiHandler datasetManagerApiHandler;
  private DatasetApiHandler datasetApiHandler;
  private SecurityApiHandler securityApiHandler;

  @Override
  public void init() throws ServletException {
    super.init();
    
    // Initialize API handlers
    kitApiHandler = new KitApiHandler();
    configApiHandler = new ConfigApiHandler();
    serverApiHandler = new ServerApiHandler();
    clusterApiHandler = new ClusterApiHandler();
    cacheManagerApiHandler = new CacheManagerApiHandler();
    cacheApiHandler = new CacheApiHandler();
    datasetManagerApiHandler = new DatasetManagerApiHandler();
    datasetApiHandler = new DatasetApiHandler();
    securityApiHandler = new SecurityApiHandler();
    
    LOGGER.info("ApiServlet initialized");
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    handleRequest(req, resp, "GET");
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    handleRequest(req, resp, "POST");
  }

  @Override
  protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    handleRequest(req, resp, "PUT");
  }

  @Override
  protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    handleRequest(req, resp, "DELETE");
  }

  private void handleRequest(HttpServletRequest req, HttpServletResponse resp, String method) throws IOException {
    String pathInfo = req.getPathInfo();
    
    if (pathInfo == null || pathInfo.equals("/")) {
      sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid API path");
      return;
    }

    // Remove leading slash
    pathInfo = pathInfo.substring(1);
    String[] pathParts = pathInfo.split("/");
    
    if (pathParts.length == 0) {
      sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid API path");
      return;
    }

    String resource = pathParts[0];
    
    try {
      // Get application-scoped objects
      Settings settings = (Settings) getServletContext().getAttribute(TinyPounderContextListener.SETTINGS_ATTR);
      KitAwareClassLoaderDelegator kitClassLoader = (KitAwareClassLoaderDelegator) 
          getServletContext().getAttribute(TinyPounderContextListener.KIT_CLASSLOADER_ATTR);
      CacheManagerBusiness cacheManagerBusiness = (CacheManagerBusiness) 
          getServletContext().getAttribute(TinyPounderContextListener.CACHE_MANAGER_BUSINESS_ATTR);
      DatasetManagerBusinessReflectionImpl datasetManagerBusiness = (DatasetManagerBusinessReflectionImpl) 
          getServletContext().getAttribute(TinyPounderContextListener.DATASET_MANAGER_BUSINESS_ATTR);
      TerracottaServerBusiness serverBusiness = (TerracottaServerBusiness) 
          getServletContext().getAttribute(TinyPounderContextListener.SERVER_BUSINESS_ATTR);
      ScheduledExecutorService executorService = (ScheduledExecutorService) 
          getServletContext().getAttribute(TinyPounderContextListener.EXECUTOR_SERVICE_ATTR);

      HttpSession session = req.getSession(true);
      
      // Java 21 switch expression - more concise and type-safe
      ApiResponse<?> response = switch (resource) {
        case "kit" -> kitApiHandler.handle(method, pathParts, req, settings, kitClassLoader);
        case "config" -> configApiHandler.handle(method, pathParts, req, settings, kitClassLoader);
        case "servers" -> serverApiHandler.handle(method, pathParts, req, session, settings, serverBusiness, executorService);
        case "cluster" -> clusterApiHandler.handle(method, pathParts, req, session, settings, serverBusiness);
        case "cachemanager" -> cacheManagerApiHandler.handle(method, pathParts, req, cacheManagerBusiness, kitClassLoader);
        case "cache" -> cacheApiHandler.handle(method, pathParts, req, cacheManagerBusiness);
        case "datasetmanager" -> datasetManagerApiHandler.handle(method, pathParts, req, datasetManagerBusiness, kitClassLoader);
        case "dataset" -> datasetApiHandler.handle(method, pathParts, req, datasetManagerBusiness);
        case "security" -> securityApiHandler.handle(method, pathParts, req, settings, kitClassLoader);
        default -> {
          sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Unknown API resource: " + resource);
          yield null;
        }
      };

      if (response != null) {
        sendJson(resp, response);
      } else if (!"default".equals(resource)) {
        sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Handler returned null response");
      }

    } catch (Exception e) {
      LOGGER.error("Error handling API request: {} {}", method, pathInfo, e);
      sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
          "Internal server error: " + e.getMessage(), getStackTrace(e));
    }
  }

  private void sendJson(HttpServletResponse resp, Object data) throws IOException {
    resp.setContentType("application/json; charset=UTF-8");
    resp.setStatus(HttpServletResponse.SC_OK);
    objectMapper.writeValue(resp.getOutputStream(), data);
  }

  private void sendError(HttpServletResponse resp, int status, String error) throws IOException {
    sendError(resp, status, error, null);
  }

  private void sendError(HttpServletResponse resp, int status, String error, String details) throws IOException {
    resp.setContentType("application/json; charset=UTF-8");
    resp.setStatus(status);
    ApiResponse<?> errorResponse = ApiResponse.error(error, details);
    objectMapper.writeValue(resp.getOutputStream(), errorResponse);
  }

  private String getStackTrace(Throwable t) {
    StringBuilder sb = new StringBuilder();
    sb.append(t.getClass().getName()).append(": ").append(t.getMessage()).append("\n");
    for (StackTraceElement element : t.getStackTrace()) {
      sb.append("  at ").append(element.toString()).append("\n");
      if (sb.length() > 2000) { // Limit stack trace size
        sb.append("  ... (truncated)\n");
        break;
      }
    }
    if (t.getCause() != null && t.getCause() != t) {
      sb.append("Caused by: ");
      sb.append(getStackTrace(t.getCause()));
    }
    return sb.toString();
  }
}

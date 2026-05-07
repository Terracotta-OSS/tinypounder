package org.terracotta.tinypounder.api;

import org.terracotta.tinypounder.model.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Base interface for API handlers.
 * Each handler is responsible for processing requests for a specific resource type.
 */
public interface ApiHandler {
  
  /**
   * Handle an API request.
   * 
   * @param method HTTP method (GET, POST, PUT, DELETE)
   * @param pathParts Path components after /api/
   * @param request HTTP request
   * @param args Additional arguments specific to the handler
   * @return API response
   * @throws Exception if an error occurs
   */
  ApiResponse<?> handle(String method, String[] pathParts, HttpServletRequest request, Object... args) throws Exception;
}

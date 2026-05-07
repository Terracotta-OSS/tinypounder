package org.terracotta.tinypounder.servlet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Servlet for serving static resources (HTML, CSS, JS, images).
 * Configured in web.xml with specific URL patterns.
 */
public class StaticResourceServlet extends HttpServlet {

  private static final Logger LOGGER = LoggerFactory.getLogger(StaticResourceServlet.class);

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    String requestURI = req.getRequestURI();
    String contextPath = req.getContextPath();
    
    // Remove context path to get the actual path
    String path = requestURI.substring(contextPath.length());
    
    // If this is an API request, return 404 to let ApiServlet handle it
    if (path.startsWith("/api/")) {
      resp.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }
    
    // Default to index.html for root path
    if (path == null || path.equals("/") || path.isEmpty()) {
      path = "/index.html";
    }

    // Determine content type
    String contentType = getContentType(path);
    resp.setContentType(contentType);

    // Set caching headers for static resources
    if (!path.endsWith(".html")) {
      resp.setHeader("Cache-Control", "public, max-age=3600");
    } else {
      resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
    }

    // Try to load resource from webapp directory
    InputStream resourceStream = getServletContext().getResourceAsStream(path);
    
    if (resourceStream == null) {
      LOGGER.warn("Resource not found: {}", path);
      resp.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    try (InputStream in = resourceStream; OutputStream out = resp.getOutputStream()) {
      byte[] buffer = new byte[8192];
      int bytesRead;
      while ((bytesRead = in.read(buffer)) != -1) {
        out.write(buffer, 0, bytesRead);
      }
    } catch (IOException e) {
      LOGGER.error("Error serving resource: {}", path, e);
      throw e;
    }
  }

  private String getContentType(String path) {
    if (path.endsWith(".html")) {
      return "text/html; charset=UTF-8";
    } else if (path.endsWith(".css")) {
      return "text/css; charset=UTF-8";
    } else if (path.endsWith(".js")) {
      return "application/javascript; charset=UTF-8";
    } else if (path.endsWith(".json")) {
      return "application/json; charset=UTF-8";
    } else if (path.endsWith(".gif")) {
      return "image/gif";
    } else if (path.endsWith(".png")) {
      return "image/png";
    } else if (path.endsWith(".jpg") || path.endsWith(".jpeg")) {
      return "image/jpeg";
    } else if (path.endsWith(".svg")) {
      return "image/svg+xml";
    } else if (path.endsWith(".ico")) {
      return "image/x-icon";
    }
    return "application/octet-stream";
  }
}

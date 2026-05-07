package org.terracotta.tinypounder.servlet;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terracotta.tinypounder.RunningServer;

import jakarta.websocket.*;
import java.util.HashMap;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket endpoint for streaming console output in real-time.
 * Clients connect to /ws/console/{consoleName} to receive console updates.
 */
@ServerEndpoint("/ws/console/{consoleName}")
public class ConsoleWebSocketEndpoint {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConsoleWebSocketEndpoint.class);
  private static final ObjectMapper objectMapper = new ObjectMapper();
  
  // Map of console name to set of sessions listening to that console
  private static final Map<String, CopyOnWriteArraySet<Session>> consoleSessions = new ConcurrentHashMap<>();
  
  // Map of console name to running server (for accessing console output)
  private static final Map<String, RunningServer> runningServers = new ConcurrentHashMap<>();

  @OnOpen
  public void onOpen(Session session, @PathParam("consoleName") String consoleName) {
    LOGGER.info("WebSocket opened for console: {} (session: {})", consoleName, session.getId());
    
    // Set idle timeout to 10 minutes (600000 ms) to prevent premature disconnection
    session.setMaxIdleTimeout(600000);
    
    consoleSessions.computeIfAbsent(consoleName, k -> new CopyOnWriteArraySet<>()).add(session);
    
    // Send initial message
    try {
      Map<String, Object> message = new HashMap<>();
      message.put("type", "connected");
      message.put("consoleName", consoleName);
      message.put("message", "Connected to console: " + consoleName);
      session.getBasicRemote().sendText(objectMapper.writeValueAsString(message));
      
      // Send console history if server is running
      RunningServer server = runningServers.get(consoleName);
      if (server != null) {
        String[] history = server.getConsoleHistory();
        if (history.length > 0) {
          Map<String, Object> historyMessage = new HashMap<>();
          historyMessage.put("type", "console");
          historyMessage.put("lines", history);
          session.getBasicRemote().sendText(objectMapper.writeValueAsString(historyMessage));
          LOGGER.info("Sent {} lines of console history for: {}", history.length, consoleName);
        }
      } else {
        // Server not registered (discovered server after page refresh)
        // Load log file directly
        loadLogFileForDiscoveredServer(session, consoleName);
      }
    } catch (IOException e) {
      LOGGER.error("Error sending initial message", e);
    }
  }

  /**
   * Recursively search for a file with the given name starting from the specified directory.
   * Returns the first matching file found, or null if not found.
   */
  private java.io.File findLogFileRecursively(java.io.File directory, String fileName) {
    if (directory == null || !directory.exists() || !directory.isDirectory()) {
      return null;
    }
    
    java.io.File[] files = directory.listFiles();
    if (files == null) {
      return null;
    }
    
    // First check files in current directory
    for (java.io.File file : files) {
      if (file.isFile() && file.getName().equals(fileName)) {
        return file;
      }
    }
    
    // Then search subdirectories
    for (java.io.File file : files) {
      if (file.isDirectory()) {
        java.io.File found = findLogFileRecursively(file, fileName);
        if (found != null) {
          return found;
        }
      }
    }
    
    return null;
  }
  
  /**
   * Load log file for a discovered server (not registered as RunningServer).
   */
  private void loadLogFileForDiscoveredServer(Session session, String consoleName) {
    try {
      // Get base location from system property or default
      String baseLocation = System.getProperty("tinypounder.baseLocation", 
          System.getProperty("user.home") + "/terracotta/MyCluster");
      
      // Extract server name from console name (they should be the same)
      String serverName = consoleName;
      
      // First try the expected location
      java.io.File logFile = new java.io.File(baseLocation, "logs/" + serverName + "/terracotta.server.log");
      
      // If not found, search recursively under server's log directory
      if (!logFile.exists()) {
        java.io.File serverLogDir = new java.io.File(baseLocation, "logs/" + serverName);
        logFile = findLogFileRecursively(serverLogDir, "terracotta.server.log");
        
        if (logFile == null) {
          LOGGER.debug("Log file not found for discovered server {}", serverName);
          return;
        }
        LOGGER.info("Found log file for discovered server {} at: {}", serverName, logFile.getAbsolutePath());
      }
      
      LOGGER.info("Loading log file for discovered server {}: {}", serverName, logFile.getAbsolutePath());
      
      // Read last 500 lines from log file
      java.util.List<String> lines = new java.util.ArrayList<>();
      try (java.io.BufferedReader reader = new java.io.BufferedReader(
          new java.io.FileReader(logFile))) {
        
        String line;
        while ((line = reader.readLine()) != null) {
          lines.add(line);
          // Keep only last 500 lines in memory
          if (lines.size() > 500) {
            lines.remove(0);
          }
        }
      }
      
      if (!lines.isEmpty()) {
        LOGGER.info("Loaded {} lines from log file for discovered server {}", lines.size(), serverName);
        
        // Send lines to this session
        Map<String, Object> historyMessage = new HashMap<>();
        historyMessage.put("type", "console");
        historyMessage.put("lines", lines.toArray(new String[0]));
        session.getBasicRemote().sendText(objectMapper.writeValueAsString(historyMessage));
      }
      
    } catch (Exception e) {
      LOGGER.error("Failed to load log file for discovered server {}", consoleName, e);
    }
  }

  @OnClose
  public void onClose(Session session, @PathParam("consoleName") String consoleName) {
    LOGGER.info("WebSocket closed for console: {} (session: {})", consoleName, session.getId());
    
    CopyOnWriteArraySet<Session> sessions = consoleSessions.get(consoleName);
    if (sessions != null) {
      sessions.remove(session);
      if (sessions.isEmpty()) {
        consoleSessions.remove(consoleName);
      }
    }
  }

  @OnError
  public void onError(Session session, @PathParam("consoleName") String consoleName, Throwable error) {
    LOGGER.error("WebSocket error for console: {} (session: {})", consoleName, session.getId(), error);
  }

  @OnMessage
  public void onMessage(String message, Session session, @PathParam("consoleName") String consoleName) {
    LOGGER.debug("Received message from client for console {}: {}", consoleName, message);
    // Client messages are not expected in this implementation
  }

  /**
   * Register a running server so its console output can be streamed.
   */
  public static void registerRunningServer(String consoleName, RunningServer server) {
    runningServers.put(consoleName, server);
    LOGGER.info("Registered running server for console: {}", consoleName);
  }

  /**
   * Unregister a running server.
   */
  public static void unregisterRunningServer(String consoleName) {
    runningServers.remove(consoleName);
    LOGGER.info("Unregistered running server for console: {}", consoleName);
  }

  /**
   * Broadcast console lines to all connected clients for a specific console.
   */
  public static void broadcastConsoleLines(String consoleName, String[] lines) {
    CopyOnWriteArraySet<Session> sessions = consoleSessions.get(consoleName);
    if (sessions == null || sessions.isEmpty()) {
      return;
    }

    try {
      Map<String, Object> message = new HashMap<>();
      message.put("type", "console");
      message.put("consoleName", consoleName);
      message.put("lines", lines);
      String json = objectMapper.writeValueAsString(message);

      for (Session session : sessions) {
        if (session.isOpen()) {
          try {
            session.getBasicRemote().sendText(json);
          } catch (IOException e) {
            LOGGER.error("Error sending console lines to session: {}", session.getId(), e);
          }
        }
      }
    } catch (Exception e) {
      LOGGER.error("Error broadcasting console lines", e);
    }
  }

  /**
   * Send a status update for a server.
   */
  public static void broadcastServerStatus(String consoleName, String status, String pid) {
    CopyOnWriteArraySet<Session> sessions = consoleSessions.get(consoleName);
    if (sessions == null || sessions.isEmpty()) {
      return;
    }

    try {
      Map<String, Object> message = new HashMap<>();
      message.put("type", "status");
      message.put("consoleName", consoleName);
      message.put("status", status);
      message.put("pid", pid != null ? pid : "");
      String json = objectMapper.writeValueAsString(message);

      for (Session session : sessions) {
        if (session.isOpen()) {
          try {
            session.getBasicRemote().sendText(json);
          } catch (IOException e) {
            LOGGER.error("Error sending status to session: {}", session.getId(), e);
          }
        }
      }
    } catch (Exception e) {
      LOGGER.error("Error broadcasting server status", e);
    }
  }
}

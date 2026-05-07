package org.terracotta.tinypounder.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terracotta.tinypounder.ProcUtils;
import org.terracotta.tinypounder.RunningServer;
import org.terracotta.tinypounder.Settings;
import org.terracotta.tinypounder.TerracottaConfigGenerator;
import org.terracotta.tinypounder.TerracottaServerBusiness;
import org.terracotta.tinypounder.model.ApiResponse;
import org.terracotta.tinypounder.servlet.ConsoleWebSocketEndpoint;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

/**
 * API handler for server control operations.
 * Endpoints:
 * - POST /api/servers/start - Start server(s)
 * - POST /api/servers/kill - Kill server
 * - GET /api/servers/status - Get server status
 * - GET /api/servers/list - Get all servers and their states
 */
public class ServerApiHandler implements ApiHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(ServerApiHandler.class);
  private static final String RUNNING_SERVERS_ATTR = "tinypounder.runningServers";
  private static final String CONFIG_FILES_ATTR = "tinypounder.configFiles";
  private static final String REFRESH_TASKS_ATTR = "tinypounder.refreshTasks";
  
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public ApiResponse<?> handle(String method, String[] pathParts, HttpServletRequest request, Object... args) 
      throws Exception {
    
    if (args.length < 4) {
      return ApiResponse.error("Invalid arguments");
    }
    
    HttpSession session = (HttpSession) args[0];
    Settings settings = (Settings) args[1];
    TerracottaServerBusiness serverBusiness = (TerracottaServerBusiness) args[2];
    ScheduledExecutorService executorService = (ScheduledExecutorService) args[3];
    
    if (pathParts.length < 2) {
      return ApiResponse.error("Invalid path");
    }
    
    String action = pathParts[1];
    
    switch (method) {
      case "POST":
        return handlePost(action, request, session, settings, serverBusiness, executorService);
      case "GET":
        return handleGet(action, session, serverBusiness);
      default:
        return ApiResponse.error("Method not allowed: " + method);
    }
  }

  private ApiResponse<?> handlePost(String action, HttpServletRequest request, HttpSession session,
                                     Settings settings, TerracottaServerBusiness serverBusiness,
                                     ScheduledExecutorService executorService) throws Exception {
    
    Map<String, Object> body = readJsonBody(request);
    
    switch (action) {
      case "start":
        return handleServerStart(body, session, settings, executorService);
        
      case "kill":
        return handleServerKill(body, session);
        
      default:
        return ApiResponse.error("Unknown action: " + action);
    }
  }

  @SuppressWarnings("unchecked")
  private ApiResponse<?> handleServerStart(Map<String, Object> body, HttpSession session, Settings settings,
                                            ScheduledExecutorService executorService) {
    String serverName = (String) body.get("serverName");
    String stripeName = (String) body.get("stripeName");
    String hostname = (String) body.get("hostname");
    String clientPort = (String) body.get("clientPort");
    String clusterName = (String) body.get("clusterName");
    
    if (serverName == null || stripeName == null) {
      return ApiResponse.error("Server name and stripe name are required");
    }
    
    if (hostname == null) {
      hostname = "localhost";
    }
    
    if (clusterName == null) {
      clusterName = "MyCluster";
    }
    
    try {
      // Get or create running servers map
      Map<String, RunningServer> runningServers = (Map<String, RunningServer>) session.getAttribute(RUNNING_SERVERS_ATTR);
      if (runningServers == null) {
        runningServers = new ConcurrentHashMap<>();
        session.setAttribute(RUNNING_SERVERS_ATTR, runningServers);
      }
      
      // Check if server is already running
      // serverName is already in format "stripe-X-server-Y", so use it directly as key
      String key = serverName;
      if (runningServers.containsKey(key)) {
        return ApiResponse.error("Server is already running: " + serverName);
      }
      
      // Get or generate config files
      Map<String, File> configFiles = (Map<String, File>) session.getAttribute(CONFIG_FILES_ATTR);
      if (configFiles == null) {
        // Generate config files (skip if they already exist to preserve manual edits)
        LOGGER.info("Generating configuration files...");
        TerracottaConfigGenerator generator = new TerracottaConfigGenerator(
            settings,
            isEnterpriseEdition(settings),
            isDynamicConfig(settings)
        );
        configFiles = generator.generateConfigs(true); // skipIfExists=true
        session.setAttribute(CONFIG_FILES_ATTR, configFiles);
      }
      
      File stripeConfig = configFiles.get(stripeName);
      if (stripeConfig == null) {
        return ApiResponse.error("Configuration file not found for stripe: " + stripeName);
      }
      
      String kitPath = settings.getKitPath();
      if (kitPath == null || kitPath.trim().isEmpty()) {
        return ApiResponse.error("Kit path is not configured. Please set the kit path first.");
      }
      
      File workDir = new File(kitPath);
      final String consoleName = key;
      final String finalKey = key;
      final String finalServerName = serverName;
      final Map<String, RunningServer> finalRunningServers = runningServers;
      
      // Create running server instance with callbacks
      String baseLocation = settings.getBaseLocation();
      RunningServer runningServer = new RunningServer(
          workDir,
          clusterName,
          baseLocation,
          stripeConfig,
          stripeName,
          serverName,
          hostname,
          clientPort,
          consoleName,
          500, // max console lines
          () -> {
            // On termination callback
            // First, do a final console refresh to capture any remaining output
            RunningServer server = finalRunningServers.get(finalKey);
            if (server != null) {
              server.refreshConsole();
            }
            
            // Then clean up
            finalRunningServers.remove(finalKey);
            ConsoleWebSocketEndpoint.unregisterRunningServer(consoleName);
            ConsoleWebSocketEndpoint.broadcastServerStatus(consoleName, "TERMINATED", null);
            LOGGER.info("Server terminated: {}", finalServerName);
          },
          newState -> {
            // On state change callback
            RunningServer server = finalRunningServers.get(finalKey);
            String pid = server != null ? String.valueOf(server.getPid()) : "0";
            ConsoleWebSocketEndpoint.broadcastServerStatus(consoleName, newState, pid);
            LOGGER.info("Server {} state changed to: {}", finalServerName, newState);
          },
          newPID -> {
            // On PID callback
            ConsoleWebSocketEndpoint.broadcastServerStatus(consoleName, "STARTING", String.valueOf(newPID));
            LOGGER.info("Server {} PID: {}", finalServerName, newPID);
          }
      );
      
      // Register and start server
      runningServers.put(key, runningServer);
      ConsoleWebSocketEndpoint.registerRunningServer(consoleName, runningServer);
      
      LOGGER.info("Starting server: {} in stripe: {} (console: {})", serverName, stripeName, consoleName);
      runningServer.start();
      
      // Cancel any existing refresh task for this server
      @SuppressWarnings("unchecked")
      Map<String, java.util.concurrent.ScheduledFuture<?>> refreshTasks = 
          (Map<String, java.util.concurrent.ScheduledFuture<?>>) session.getAttribute(REFRESH_TASKS_ATTR);
      if (refreshTasks == null) {
        refreshTasks = new ConcurrentHashMap<>();
        session.setAttribute(REFRESH_TASKS_ATTR, refreshTasks);
      }
      
      java.util.concurrent.ScheduledFuture<?> existingTask = refreshTasks.get(finalKey);
      if (existingTask != null) {
        existingTask.cancel(false);
        LOGGER.info("Cancelled existing refresh task for: {}", finalKey);
      }
      
      // Schedule periodic console refresh
      java.util.concurrent.ScheduledFuture<?> refreshTask = executorService.scheduleAtFixedRate(
          () -> {
            RunningServer server = finalRunningServers.get(finalKey);
            if (server != null) {
              server.refreshConsole();
            }
          },
          1, 1, java.util.concurrent.TimeUnit.SECONDS
      );
      
      refreshTasks.put(finalKey, refreshTask);
      
      Map<String, Object> result = new HashMap<>();
      result.put("serverName", serverName);
      result.put("stripeName", stripeName);
      result.put("consoleName", consoleName);
      result.put("status", "STARTING");
      
      return ApiResponse.success(result);
      
    } catch (Exception e) {
      LOGGER.error("Failed to start server: {} in stripe: {}", serverName, stripeName, e);
      return ApiResponse.error("Failed to start server: " + e.getMessage());
    }
  }

  @SuppressWarnings("unchecked")
  private ApiResponse<?> handleServerKill(Map<String, Object> body, HttpSession session) {
    String serverName = (String) body.get("serverName");
    String stripeName = (String) body.get("stripeName");
    
    if (serverName == null || stripeName == null) {
      return ApiResponse.error("Server name and stripe name are required");
    }
    
    try {
      Map<String, RunningServer> runningServers = (Map<String, RunningServer>) session.getAttribute(RUNNING_SERVERS_ATTR);
      
      // serverName is already in format "stripe-X-server-Y", use it directly as key
      String key = serverName;
      RunningServer runningServer = (runningServers != null) ? runningServers.get(key) : null;
      
      if (runningServer != null) {
        // Normal case: server is registered in session
        LOGGER.info("Killing registered server: {} in stripe: {} (PID: {})", serverName, stripeName, runningServer.getPid());
        runningServer.kill();
        
        // Cancel the refresh task
        @SuppressWarnings("unchecked")
        Map<String, java.util.concurrent.ScheduledFuture<?>> refreshTasks = 
            (Map<String, java.util.concurrent.ScheduledFuture<?>>) session.getAttribute(REFRESH_TASKS_ATTR);
        if (refreshTasks != null) {
          java.util.concurrent.ScheduledFuture<?> task = refreshTasks.remove(key);
          if (task != null) {
            task.cancel(false);
            LOGGER.info("Cancelled refresh task for: {}", key);
          }
        }
        
        // Final console refresh and status broadcast
        runningServer.refreshConsole();
        ConsoleWebSocketEndpoint.broadcastServerStatus(runningServer.getConsoleName(), "TERMINATED", null);
        
        // Unregister from WebSocket endpoint
        ConsoleWebSocketEndpoint.unregisterRunningServer(runningServer.getConsoleName());
        
        Map<String, Object> result = new HashMap<>();
        result.put("serverName", serverName);
        result.put("stripeName", stripeName);
        result.put("consoleName", runningServer.getConsoleName());
        result.put("status", "TERMINATED");
        
        return ApiResponse.success(result);
        
      } else {
        // Discovered server case: try to find PID from log file or by port
        LOGGER.info("Attempting to kill discovered server: {} in stripe: {}", serverName, stripeName);
        
        Settings settings = new Settings();
        settings.load();
        String baseLocation = settings.getBaseLocation();
        
        // First try to get PID from log file
        ServerLogInfo logInfo = loadServerLogToConsole(baseLocation, serverName, serverName);
        long pid = logInfo.pid;
        
        // If PID not found in log, try to find it by port
        if (pid == 0) {
          LOGGER.info("PID not found in log file for {}, attempting to find by port", serverName);
          
          // Extract stripe and server numbers from serverName (format: stripe-X-server-Y)
          try {
            String[] parts = serverName.split("-");
            if (parts.length >= 4) {
              int stripeNum = Integer.parseInt(parts[1]);
              int serverNum = Integer.parseInt(parts[3]);
              
              // Calculate client port: 9410 + (stripe-1)*10 + (server-1)
              int clientPort = 9410 + (stripeNum - 1) * 10 + (serverNum - 1);
              
              pid = ProcUtils.findPidByPort(clientPort);
              
              if (pid > 0) {
                LOGGER.info("Found PID {} for {} by checking port {}", pid, serverName, clientPort);
              } else {
                LOGGER.warn("Could not find PID for {} on port {}", serverName, clientPort);
              }
            }
          } catch (Exception e) {
            LOGGER.warn("Failed to parse server name or find PID by port: {}", serverName, e);
          }
        }
        
        if (pid == 0) {
          return ApiResponse.error("Cannot kill server: PID not found for " + serverName + 
              ". Server may not be running or log file not yet created.");
        }
        
        LOGGER.info("Killing discovered server: {} (PID: {})", serverName, pid);
        ProcUtils.kill(pid);
        
        // Broadcast termination status for discovered server
        ConsoleWebSocketEndpoint.broadcastServerStatus(serverName, "TERMINATED", null);
        
        Map<String, Object> result = new HashMap<>();
        result.put("serverName", serverName);
        result.put("stripeName", stripeName);
        result.put("consoleName", serverName);
        result.put("status", "TERMINATED");
        
        return ApiResponse.success(result);
      }
      
    } catch (Exception e) {
      LOGGER.error("Failed to kill server: {} in stripe: {}", serverName, stripeName, e);
      return ApiResponse.error("Failed to kill server: " + e.getMessage());
    }
  }

  @SuppressWarnings("unchecked")
  private ApiResponse<?> handleGet(String action, HttpSession session, TerracottaServerBusiness serverBusiness) {
    switch (action) {
      case "list":
        return handleServerList(session);
        
      case "status":
        return handleServerStatus(session);
        
      default:
        return ApiResponse.error("Unknown action: " + action);
    }
  }

  @SuppressWarnings("unchecked")
  private ApiResponse<?> handleServerList(HttpSession session) {
    try {
      LOGGER.info("Listing servers - Session ID: {}, isNew: {}", session.getId(), session.isNew());
      
      Map<String, RunningServer> runningServers = (Map<String, RunningServer>) session.getAttribute(RUNNING_SERVERS_ATTR);
      
      LOGGER.info("Running servers map from session: {}", runningServers != null ? runningServers.size() + " servers" : "null");
      
      List<Map<String, Object>> serverList = new ArrayList<>();
      
      if (runningServers != null) {
        for (Map.Entry<String, RunningServer> entry : runningServers.entrySet()) {
          String key = entry.getKey();
          RunningServer server = entry.getValue();
          
          LOGGER.info("Found running server: key={}, name={}, pid={}", key, server.getServerName(), server.getPid());
          
          Map<String, Object> serverInfo = new HashMap<>();
          serverInfo.put("key", key);
          serverInfo.put("serverName", server.getServerName());
          serverInfo.put("consoleName", server.getConsoleName());
          serverInfo.put("pid", server.getPid());
          serverInfo.put("state", server.getServerState());
          serverInfo.put("running", true);
          
          serverList.add(serverInfo);
        }
      } else {
        // Session lost (e.g., after redeployment) - discover running servers by checking ports
        LOGGER.info("No session data found, attempting to discover running servers by checking ports");
        serverList = discoverRunningServers();
      }
      
      Map<String, Object> result = new HashMap<>();
      result.put("servers", serverList);
      result.put("count", serverList.size());
      
      LOGGER.info("Returning {} servers", serverList.size());
      
      return ApiResponse.success(result);
      
    } catch (Exception e) {
      LOGGER.error("Failed to list servers", e);
      return ApiResponse.error("Failed to list servers: " + e.getMessage());
    }
  }
  
  /**
   * Discover running servers by checking if their ports are listening
   */
  private List<Map<String, Object>> discoverRunningServers() {
    List<Map<String, Object>> serverList = new ArrayList<>();
    
    try {
      Settings settings = new Settings();
      settings.load();
      
      int stripeCount = settings.getStripeCount();
      int serverCount = settings.getServerCount();
      String baseLocation = settings.getBaseLocation();
      
      LOGGER.info("Checking for running servers: {} stripes, {} servers per stripe", stripeCount, serverCount);
      
      for (int stripe = 1; stripe <= stripeCount; stripe++) {
        for (int server = 1; server <= serverCount; server++) {
          // Calculate client port: 9410 + (stripe-1)*10 + (server-1)
          int clientPort = 9410 + (stripe - 1) * 10 + (server - 1);
          String hostname = "localhost";
          
          if (isPortListening(hostname, clientPort)) {
            String serverName = "stripe-" + stripe + "-server-" + server;
            String consoleName = "stripe-" + stripe + "-server-" + server;
            
            LOGGER.info("Discovered running server: {} on {}:{}", serverName, hostname, clientPort);
            
            // Load last 500 lines from server log and send to console, detect state and PID
            ServerLogInfo logInfo = loadServerLogToConsole(baseLocation, serverName, consoleName);
            
            // Start monitoring log file for discovered server
            startLogFileMonitoring(baseLocation, serverName, consoleName);
            
            Map<String, Object> serverInfo = new HashMap<>();
            serverInfo.put("key", serverName);
            serverInfo.put("serverName", serverName);
            serverInfo.put("consoleName", consoleName);
            serverInfo.put("pid", logInfo.pid);
            serverInfo.put("running", true);
            serverInfo.put("discovered", true);
            serverInfo.put("state", logInfo.state);
            
            serverList.add(serverInfo);
          }
        }
      }
      
      LOGGER.info("Discovered {} running servers", serverList.size());
      
    } catch (Exception e) {
      LOGGER.error("Failed to discover running servers", e);
    }
    
    return serverList;
  }
  
  /**
   * Start monitoring a log file for a discovered server.
   * Reads new lines periodically and broadcasts them via WebSocket.
   */
  private void startLogFileMonitoring(String baseLocation, String serverName, String consoleName) {
    try {
      // Find the log file
      File logFile = new File(baseLocation, "logs/" + serverName + "/terracotta.server.log");
      if (!logFile.exists()) {
        File serverLogDir = new File(baseLocation, "logs/" + serverName);
        logFile = findLogFileRecursively(serverLogDir, "terracotta.server.log");
        if (logFile == null) {
          LOGGER.debug("Cannot monitor log file for {}: file not found", serverName);
          return;
        }
      }
      
      final File finalLogFile = logFile;
      final long[] lastPosition = {finalLogFile.length()}; // Track last read position
      
      // Schedule periodic log file monitoring (every 1 second)
      java.util.concurrent.ScheduledExecutorService executor = 
          java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "LogMonitor-" + serverName);
            t.setDaemon(true);
            return t;
          });
      
      executor.scheduleAtFixedRate(() -> {
        try {
          long currentLength = finalLogFile.length();
          
          // Check if file has grown
          if (currentLength > lastPosition[0]) {
            java.util.List<String> newLines = new java.util.ArrayList<>();
            
            try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(finalLogFile, "r")) {
              raf.seek(lastPosition[0]);
              String line;
              while ((line = raf.readLine()) != null) {
                newLines.add(line);
              }
              lastPosition[0] = raf.getFilePointer();
            }
            
            if (!newLines.isEmpty()) {
              // Broadcast new lines to WebSocket clients
              String[] lineArray = newLines.toArray(new String[0]);
              ConsoleWebSocketEndpoint.broadcastConsoleLines(consoleName, lineArray);
              
              // Check for state changes in new lines
              String ACTIVE_PATTERN = "Becoming State[ ACTIVE-COORDINATOR ]";
              java.util.regex.Pattern STATE_PATTERN = java.util.regex.Pattern.compile("Moved to State\\[ (.+?) ]");
              
              for (String line : newLines) {
                if (line.contains(ACTIVE_PATTERN)) {
                  ConsoleWebSocketEndpoint.broadcastServerStatus(consoleName, "ACTIVE-COORDINATOR", null);
                } else if (line.contains("Moved to State[")) {
                  java.util.regex.Matcher m = STATE_PATTERN.matcher(line);
                  if (m.find()) {
                    ConsoleWebSocketEndpoint.broadcastServerStatus(consoleName, m.group(1), null);
                  }
                }
              }
            }
          } else if (currentLength < lastPosition[0]) {
            // File was truncated or rotated, reset position
            lastPosition[0] = 0;
          }
        } catch (Exception e) {
          LOGGER.error("Error monitoring log file for {}", serverName, e);
        }
      }, 1, 1, java.util.concurrent.TimeUnit.SECONDS);
      
      LOGGER.info("Started log file monitoring for discovered server: {}", serverName);
      
    } catch (Exception e) {
      LOGGER.error("Failed to start log file monitoring for {}", serverName, e);
    }
  }
  
  /**
   * Simple holder for server log information (state and PID).
   */
  private static class ServerLogInfo {
    final String state;
    final long pid;
    
    ServerLogInfo(String state, long pid) {
      this.state = state;
      this.pid = pid;
    }
  }
  
  /**
   * Recursively search for a file with the given name starting from the specified directory.
   * Returns the first matching file found, or null if not found.
   */
  private File findLogFileRecursively(File directory, String fileName) {
    if (directory == null || !directory.exists() || !directory.isDirectory()) {
      return null;
    }
    
    File[] files = directory.listFiles();
    if (files == null) {
      return null;
    }
    
    // First check files in current directory
    for (File file : files) {
      if (file.isFile() && file.getName().equals(fileName)) {
        return file;
      }
    }
    
    // Then search subdirectories
    for (File file : files) {
      if (file.isDirectory()) {
        File found = findLogFileRecursively(file, fileName);
        if (found != null) {
          return found;
        }
      }
    }
    
    return null;
  }

  /**
   * Load the last 500 lines from a server's log file and send to console WebSocket.
   * Returns the detected server state and PID based on log patterns.
   * If no log file exists, assumes server is in DIAGNOSTIC MODE.
   */
  private ServerLogInfo loadServerLogToConsole(String baseLocation, String serverName, String consoleName) {
    String detectedState = "UNKNOWN";
    long detectedPid = 0;
    
    try {
      // First try the expected location
      File logFile = new File(baseLocation, "logs/" + serverName + "/terracotta.server.log");
      
      // If not found, search recursively under server's log directory
      if (!logFile.exists()) {
        File serverLogDir = new File(baseLocation, "logs/" + serverName);
        logFile = findLogFileRecursively(serverLogDir, "terracotta.server.log");
        
        if (logFile == null) {
          LOGGER.info("Log file not found for {} - assuming DIAGNOSTIC MODE", serverName);
          return new ServerLogInfo("DIAGNOSTIC MODE", 0);
        }
        LOGGER.info("Found log file for {} at: {}", serverName, logFile.getAbsolutePath());
      }
      
      LOGGER.info("Loading log file for {}: {}", serverName, logFile.getAbsolutePath());
      
      // Patterns for state and PID detection
      String ACTIVE_PATTERN = "Becoming State[ ACTIVE-COORDINATOR ]";
      java.util.regex.Pattern STATE_PATTERN = java.util.regex.Pattern.compile("Moved to State\\[ (.+?) ]");
      java.util.regex.Pattern PID_PATTERN = java.util.regex.Pattern.compile("PID is ([0-9]+)");
      
      // Read last 500 lines from log file
      java.util.List<String> lines = new java.util.ArrayList<>();
      try (java.io.BufferedReader reader = new java.io.BufferedReader(
          new java.io.FileReader(logFile))) {
        
        String line;
        while ((line = reader.readLine()) != null) {
          lines.add(line);
          
          // Search for state patterns (keep most recent state)
          if (line.contains(ACTIVE_PATTERN)) {
            detectedState = "ACTIVE";
          } else if (line.contains(" - Started the server in diagnostic mode")) {
            detectedState = "DIAGNOSTIC MODE";
          } else if (line.contains("Moved to State[")) {
            java.util.regex.Matcher m = STATE_PATTERN.matcher(line);
            if (m.find()) {
              String state = m.group(1);
              // Normalize DIAGNOSTIC to DIAGNOSTIC MODE for UI consistency
              detectedState = "DIAGNOSTIC".equals(state) ? "DIAGNOSTIC MODE" : state;
            }
          } else if (line.contains("PID is ")) {
            java.util.regex.Matcher m = PID_PATTERN.matcher(line);
            if (m.find()) {
              try {
                detectedPid = Long.parseLong(m.group(1));
              } catch (NumberFormatException e) {
                LOGGER.warn("Failed to parse PID from line: {}", line);
              }
            }
          }
          
          // Keep only last 500 lines in memory
          if (lines.size() > 500) {
            lines.remove(0);
          }
        }
      }
      
      if (!lines.isEmpty()) {
        LOGGER.info("Loaded {} lines from log file for {} (detected state: {}, PID: {})", 
                    lines.size(), serverName, detectedState, detectedPid);
        
        // Send lines to console WebSocket
        String[] lineArray = lines.toArray(new String[0]);
        ConsoleWebSocketEndpoint.broadcastConsoleLines(consoleName, lineArray);
      }
      
    } catch (Exception e) {
      LOGGER.error("Failed to load log file for {}", serverName, e);
    }
    
    return new ServerLogInfo(detectedState, detectedPid);
  }
  
  /**
   * Check if a port is listening on the given host
   */
  private boolean isPortListening(String hostname, int port) {
    try (java.net.Socket socket = new java.net.Socket()) {
      socket.connect(new java.net.InetSocketAddress(hostname, port), 1000);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  @SuppressWarnings("unchecked")
  private ApiResponse<?> handleServerStatus(HttpSession session) {
    try {
      Map<String, RunningServer> runningServers = (Map<String, RunningServer>) session.getAttribute(RUNNING_SERVERS_ATTR);
      
      Map<String, Object> statusData = new HashMap<>();
      statusData.put("totalServers", runningServers != null ? runningServers.size() : 0);
      statusData.put("runningServers", runningServers != null ? runningServers.size() : 0);
      
      return ApiResponse.success(statusData);
      
    } catch (Exception e) {
      LOGGER.error("Failed to get server status", e);
      return ApiResponse.error("Failed to get server status: " + e.getMessage());
    }
  }

  private boolean isEnterpriseEdition(Settings settings) {
    // Check if kit path contains enterprise indicators
    String kitPath = settings.getKitPath();
    if (kitPath == null) {
      return false;
    }
    return kitPath.toLowerCase().contains("enterprise") || 
           kitPath.toLowerCase().contains("-ee-");
  }

  private boolean isDynamicConfig(Settings settings) {
    // Check if dynamic config is enabled by looking for config-tool in kit
    String kitPath = settings.getKitPath();
    if (kitPath == null) {
      return false;
    }
    File configTool = new File(kitPath, "tools/bin/config-tool.sh");
    return configTool.exists();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> readJsonBody(HttpServletRequest request) throws Exception {
    return objectMapper.readValue(request.getInputStream(), Map.class);
  }
}

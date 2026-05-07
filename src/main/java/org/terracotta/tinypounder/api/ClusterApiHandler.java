package org.terracotta.tinypounder.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terracotta.tinypounder.ProcUtils;
import org.terracotta.tinypounder.RunningServer;
import org.terracotta.tinypounder.Settings;
import org.terracotta.tinypounder.TerracottaServerBusiness;
import org.terracotta.tinypounder.model.ApiResponse;
import org.terracotta.tinypounder.servlet.ConsoleWebSocketEndpoint;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.String.join;

/**
 * API handler for cluster control operations.
 * Endpoints:
 * - POST /api/cluster/configure - Configure cluster
 * - POST /api/cluster/backup - Backup cluster
 * - POST /api/cluster/dump - Dump cluster
 * - POST /api/cluster/stop - Stop cluster
 * - GET /api/cluster/status - Get cluster status
 */
public class ClusterApiHandler implements ApiHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(ClusterApiHandler.class);
  private static final String RUNNING_SERVERS_ATTR = "tinypounder.runningServers";
  private static final String CONFIG_FILES_ATTR = "tinypounder.configFiles";
  
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public ApiResponse<?> handle(String method, String[] pathParts, HttpServletRequest request, Object... args) 
      throws Exception {
    
    if (args.length < 3) {
      return ApiResponse.error("Invalid arguments");
    }
    
    HttpSession session = (HttpSession) args[0];
    Settings settings = (Settings) args[1];
    TerracottaServerBusiness serverBusiness = (TerracottaServerBusiness) args[2];
    
    if (pathParts.length < 2) {
      return ApiResponse.error("Invalid path");
    }
    
    String action = pathParts[1];
    
    switch (method) {
      case "POST":
        return handlePost(action, request, session, settings, serverBusiness);
      case "GET":
        return handleGet(action, session, serverBusiness);
      default:
        return ApiResponse.error("Method not allowed: " + method);
    }
  }

  private ApiResponse<?> handlePost(String action, HttpServletRequest request, HttpSession session,
                                     Settings settings, TerracottaServerBusiness serverBusiness) throws Exception {
    
    Map<String, Object> body = readJsonBody(request);
    LOGGER.info("ClusterApiHandler.handlePost - action: {}, body: {}", action, body);
    
    switch (action) {
      case "configure":
        return handleClusterConfigure(body, session, settings);
        
      case "backup":
        return handleClusterBackup(body, settings);
        
      case "dump":
        return handleClusterDump(body, session, settings);
        
      case "stop":
        return handleClusterStop(body, session);
        
      default:
        return ApiResponse.error("Unknown action: " + action);
    }
  }

  @SuppressWarnings("unchecked")
  private ApiResponse<?> handleClusterConfigure(Map<String, Object> body, HttpSession session, Settings settings) {
    String clusterName = (String) body.get("clusterName");
    if (clusterName == null || clusterName.trim().isEmpty()) {
      return ApiResponse.error("Cluster name is required");
    }
    
    try {
      // Get config file from session first
      Map<String, File> configFiles = (Map<String, File>) session.getAttribute(CONFIG_FILES_ATTR);
      
      // If not in session, try to find existing config files in kit directory
      if (configFiles == null || configFiles.isEmpty()) {
        configFiles = findExistingConfigFiles(settings);
        
        if (configFiles == null || configFiles.isEmpty()) {
          // Return special error that UI can detect with errorCode in data field
          Map<String, Object> errorData = new HashMap<>();
          errorData.put("errorCode", "NO_CONFIG_FILES");
          errorData.put("message", "No configuration files found. Please generate configuration first.");
          return new ApiResponse<>(false, errorData, null, "No configuration files found", 
              "Please go to Server Configuration tab and generate configuration files first.");
        }
        
        // Store found config files in session for future use
        session.setAttribute(CONFIG_FILES_ATTR, configFiles);
        LOGGER.info("Found existing config files in kit directory: {}", configFiles.keySet());
      }
      
      // For dynamic config, use config-tool to activate
      File kitPath = new File(settings.getKitPath());
      File configTool = new File(kitPath, "tools/bin/config-tool." + (ProcUtils.isWindows() ? "bat" : "sh"));
      
      if (!configTool.exists()) {
        return ApiResponse.error("config-tool not found. This operation requires dynamic configuration support.");
      }
      
      // Get the cluster properties file
      File configFile = configFiles.values().iterator().next();
      
      // Build activate command
      StringBuilder commandBuilder = new StringBuilder();
      commandBuilder.append(configTool.getAbsolutePath());
      
      // Add security root directory if client security is enabled
      if (settings.isClientSecurityEnabled() && settings.getClientSecurityPath() != null) {
        commandBuilder.append(" -security-dir ").append(settings.getClientSecurityPath());
      }
      
      commandBuilder.append(" activate -f ").append(configFile.getAbsolutePath())
                    .append(" -n ").append(clusterName);
      
      String command = commandBuilder.toString();
      LOGGER.info("Activating cluster with command: {}", command);
      
      // Execute config-tool activate
      ProcessBuilder pb = new ProcessBuilder();
      if (ProcUtils.isWindows()) {
        pb.command("cmd.exe", "/c", command);
      } else {
        pb.command("sh", "-c", command);
      }
      pb.directory(kitPath);
      pb.redirectErrorStream(true);
      
      Process process = pb.start();
      
      // Capture output and send to main console
      StringBuilder output = new StringBuilder();
      List<String> consoleLines = new ArrayList<>();
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          output.append(line).append("\n");
          consoleLines.add(line);
          LOGGER.info("config-tool: {}", line);
          
          // Send to main console in batches of 10 lines or immediately if important
          if (consoleLines.size() >= 10 || line.contains("ERROR") || line.contains("SUCCESS")) {
            ConsoleWebSocketEndpoint.broadcastConsoleLines("main", consoleLines.toArray(new String[0]));
            consoleLines.clear();
          }
        }
        
        // Send any remaining lines
        if (!consoleLines.isEmpty()) {
          ConsoleWebSocketEndpoint.broadcastConsoleLines("main", consoleLines.toArray(new String[0]));
        }
      }
      
      int exitCode = process.waitFor();
      
      if (exitCode == 0) {
        Map<String, Object> result = new HashMap<>();
        result.put("clusterName", clusterName);
        result.put("status", "CONFIGURED");
        result.put("output", output.toString());
        return ApiResponse.success(result);
      } else {
        return ApiResponse.error("Cluster activation failed with exit code: " + exitCode, output.toString());
      }
      
    } catch (Exception e) {
      LOGGER.error("Failed to configure cluster: {}", clusterName, e);
      return ApiResponse.error("Failed to configure cluster: " + e.getMessage());
    }
  }

  /**
   * Find existing config files in the kit directory.
   * Returns null if no config files are found.
   */
  private Map<String, File> findExistingConfigFiles(Settings settings) {
    Map<String, File> configFiles = new HashMap<>();
    File kitPath = new File(settings.getKitPath());
    
    // Check for dynamic config (cluster.properties)
    File dynamicConfig = new File(kitPath, "cluster.properties");
    if (dynamicConfig.exists()) {
      LOGGER.info("Found existing dynamic config file: {}", dynamicConfig.getAbsolutePath());
      // All stripes use the same properties file
      for (int stripe = 1; stripe <= settings.getStripeCount(); stripe++) {
        configFiles.put("stripe-" + stripe, dynamicConfig);
      }
      return configFiles;
    }
    
    // Check for XML configs (tc-config-stripe-*.xml)
    for (int stripe = 1; stripe <= settings.getStripeCount(); stripe++) {
      File xmlConfig = new File(kitPath, "tc-config-stripe-" + stripe + ".xml");
      if (xmlConfig.exists()) {
        LOGGER.info("Found existing XML config file: {}", xmlConfig.getAbsolutePath());
        configFiles.put("stripe-" + stripe, xmlConfig);
      }
    }
    
    return configFiles.isEmpty() ? null : configFiles;
  }

  private ApiResponse<?> handleClusterBackup(Map<String, Object> body, Settings settings) {
    String clusterName = (String) body.get("clusterName");
    if (clusterName == null || clusterName.trim().isEmpty()) {
      return ApiResponse.error("Cluster name is required");
    }
    
    try {
      File kitPath = new File(settings.getKitPath());
      File backupTool = new File(kitPath, "tools/bin/cluster-tool." + (ProcUtils.isWindows() ? "bat" : "sh"));
      
      if (!backupTool.exists()) {
        return ApiResponse.error("cluster-tool not found. Backup requires enterprise edition.");
      }
      
      // Determine backup directory
      String backupDir = System.getProperty("user.home") + "/terracotta/" + clusterName + "/backup";
      
      // Build backup command
      StringBuilder commandBuilder = new StringBuilder();
      commandBuilder.append(backupTool.getAbsolutePath());
      
      // Add security root directory if client security is enabled
      if (settings.isClientSecurityEnabled() && settings.getClientSecurityPath() != null) {
        commandBuilder.append(" -security-dir ").append(settings.getClientSecurityPath());
      }
      
      commandBuilder.append(" backup -n ").append(clusterName)
                    .append(" -d ").append(backupDir);
      
      String command = commandBuilder.toString();
      LOGGER.info("Backing up cluster with command: {}", command);
      
      ProcessBuilder pb = new ProcessBuilder();
      if (ProcUtils.isWindows()) {
        pb.command("cmd.exe", "/c", command);
      } else {
        pb.command("sh", "-c", command);
      }
      pb.directory(kitPath);
      pb.redirectErrorStream(true);
      
      Process process = pb.start();
      
      StringBuilder output = new StringBuilder();
      List<String> consoleLines = new ArrayList<>();
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          output.append(line).append("\n");
          consoleLines.add(line);
          LOGGER.info("cluster-tool: {}", line);
          
          // Send to main console in batches of 10 lines or immediately if important
          if (consoleLines.size() >= 10 || line.contains("ERROR") || line.contains("SUCCESS")) {
            ConsoleWebSocketEndpoint.broadcastConsoleLines("main", consoleLines.toArray(new String[0]));
            consoleLines.clear();
          }
        }
        
        // Send any remaining lines
        if (!consoleLines.isEmpty()) {
          ConsoleWebSocketEndpoint.broadcastConsoleLines("main", consoleLines.toArray(new String[0]));
        }
      }
      
      int exitCode = process.waitFor();
      
      if (exitCode == 0) {
        Map<String, Object> result = new HashMap<>();
        result.put("clusterName", clusterName);
        result.put("backupDir", backupDir);
        result.put("output", output.toString());
        return ApiResponse.success(result);
      } else {
        return ApiResponse.error("Cluster backup failed with exit code: " + exitCode, output.toString());
      }
      
    } catch (Exception e) {
      LOGGER.error("Failed to backup cluster: {}", clusterName, e);
      return ApiResponse.error("Failed to backup cluster: " + e.getMessage());
    }
  }

  private ApiResponse<?> handleClusterDump(Map<String, Object> body, HttpSession session, Settings settings) {
    String clusterName = (String) body.get("clusterName");
    if (clusterName == null || clusterName.trim().isEmpty()) {
      return ApiResponse.error("Cluster name is required");
    }
    
    try {
      File kitPath = new File(settings.getKitPath());
      File clusterTool = new File(kitPath, "tools/bin/cluster-tool." + (ProcUtils.isWindows() ? "bat" : "sh"));
      
      if (!clusterTool.exists()) {
        return ApiResponse.error("cluster-tool not found.");
      }
      
      // Build dump command
      StringBuilder commandBuilder = new StringBuilder();
      commandBuilder.append(clusterTool.getAbsolutePath());
      
      // Add security root directory if client security is enabled
      if (settings.isClientSecurityEnabled() && settings.getClientSecurityPath() != null) {
        commandBuilder.append(" -security-dir ").append(settings.getClientSecurityPath());
      }
      
      commandBuilder.append(" dump -cluster-name ").append(clusterName);

      Map<String, RunningServer> runningServers = (Map<String, RunningServer>) session.getAttribute(RUNNING_SERVERS_ATTR);
      List<String> serverHostPortList = new ArrayList<>();
      if (runningServers != null && !runningServers.isEmpty()) {
        for (Map.Entry<String, RunningServer> entry : runningServers.entrySet()) {
          RunningServer server = entry.getValue();
          serverHostPortList.add(server.getNodeHostname() + ":" + server.getNodePort());
        }
        commandBuilder.append(" -connect-to " ).append(join(",", serverHostPortList));
      } else {
        return ApiResponse.error("There are no running servers in the cluster");
      }

      String command = commandBuilder.toString();
      LOGGER.info("Dumping cluster with command: {}", command);
      
      ProcessBuilder pb = new ProcessBuilder();
      if (ProcUtils.isWindows()) {
        pb.command("cmd.exe", "/c", command);
      } else {
        pb.command("sh", "-c", command);
      }
      pb.directory(kitPath);
      pb.redirectErrorStream(true);
      
      Process process = pb.start();
      
      StringBuilder output = new StringBuilder();
      List<String> consoleLines = new ArrayList<>();
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          output.append(line).append("\n");
          consoleLines.add(line);
          
          // Send to main console in batches of 10 lines or immediately if important
          if (consoleLines.size() >= 10 || line.contains("ERROR") || line.contains("SUCCESS")) {
            ConsoleWebSocketEndpoint.broadcastConsoleLines("main", consoleLines.toArray(new String[0]));
            consoleLines.clear();
          }
        }
        
        // Send any remaining lines
        if (!consoleLines.isEmpty()) {
          ConsoleWebSocketEndpoint.broadcastConsoleLines("main", consoleLines.toArray(new String[0]));
        }
      }
      
      int exitCode = process.waitFor();
      
      if (exitCode == 0) {
        Map<String, Object> result = new HashMap<>();
        result.put("clusterName", clusterName);
        result.put("dump", output.toString());
        return ApiResponse.success(result);
      } else {
        return ApiResponse.error("Cluster dump failed with exit code: " + exitCode, output.toString());
      }
      
    } catch (Exception e) {
      LOGGER.error("Failed to dump cluster: {}", clusterName, e);
      return ApiResponse.error("Failed to dump cluster: " + e.getMessage());
    }
  }

  @SuppressWarnings("unchecked")
  private ApiResponse<?> handleClusterStop(Map<String, Object> body, HttpSession session) {
    String clusterName = (String) body.get("clusterName");
    if (clusterName == null || clusterName.trim().isEmpty()) {
      return ApiResponse.error("Cluster name is required");
    }
    
    try {
      Map<String, RunningServer> runningServers = (Map<String, RunningServer>) session.getAttribute(RUNNING_SERVERS_ATTR);
      
      List<String> stoppedServers = new ArrayList<>();
      List<String> failedServers = new ArrayList<>();
      
      // First, try to stop servers from session (if any)
      if (runningServers != null && !runningServers.isEmpty()) {
        for (Map.Entry<String, RunningServer> entry : runningServers.entrySet()) {
          RunningServer server = entry.getValue();
          
          try {
            LOGGER.info("Stopping server from session: {} (PID: {})", server.getServerName(), server.getPid());
            server.kill();
            stoppedServers.add(server.getServerName());
          } catch (Exception e) {
            LOGGER.error("Failed to stop server: {}", server.getServerName(), e);
            failedServers.add(server.getServerName());
          }
        }
      }
      
      // Also discover and kill any running servers by port (handles discovered servers after refresh)
      Settings settings = new Settings();
      settings.load();
      
      int stripeCount = settings.getStripeCount();
      int serverCount = settings.getServerCount();
      
      for (int stripe = 1; stripe <= stripeCount; stripe++) {
        for (int server = 1; server <= serverCount; server++) {
          int clientPort = 9410 + (stripe - 1) * 10 + (server - 1);
          String hostname = "localhost";
          String serverName = "stripe-" + stripe + "-server-" + server;
          
          // Skip if already stopped from session
          if (stoppedServers.contains(serverName)) {
            continue;
          }
          
          if (isPortListening(hostname, clientPort)) {
            try {
              LOGGER.info("Discovered and stopping server: {} on {}:{}", serverName, hostname, clientPort);
              // Kill by finding process listening on port
              killServerByPort(clientPort);
              stoppedServers.add(serverName);
            } catch (Exception e) {
              LOGGER.error("Failed to stop discovered server: {}", serverName, e);
              failedServers.add(serverName);
            }
          }
        }
      }
      
      Map<String, Object> result = new HashMap<>();
      result.put("clusterName", clusterName);
      result.put("stoppedServers", stoppedServers);
      result.put("failedServers", failedServers);
      result.put("totalStopped", stoppedServers.size());
      result.put("totalFailed", failedServers.size());
      
      if (stoppedServers.isEmpty()) {
        return ApiResponse.error("No running servers found to stop");
      } else if (failedServers.isEmpty()) {
        return ApiResponse.success(result);
      } else {
        String errorDetails = "Failed servers: " + join(", ", failedServers);
        return ApiResponse.error("Some servers failed to stop", errorDetails);
      }
      
    } catch (Exception e) {
      LOGGER.error("Failed to stop cluster: {}", clusterName, e);
      return ApiResponse.error("Failed to stop cluster: " + e.getMessage());
    }
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
  
  /**
   * Kill process listening on the given port
   */
  private void killServerByPort(int port) throws Exception {
    String os = System.getProperty("os.name").toLowerCase();
    
    if (os.contains("win")) {
      // Windows: netstat -ano | findstr :<port> to get PID, then taskkill
      ProcessBuilder pb = new ProcessBuilder("cmd", "/c", 
          "for /f \"tokens=5\" %a in ('netstat -ano ^| findstr :" + port + "') do taskkill /F /PID %a");
      pb.start().waitFor();
    } else {
      // Unix/Mac: lsof -ti tcp:<port> to get PID, then kill
      ProcessBuilder pb = new ProcessBuilder("sh", "-c", 
          "lsof -ti tcp:" + port + " | xargs kill -9");
      pb.start().waitFor();
    }
  }

  @SuppressWarnings("unchecked")
  private ApiResponse<?> handleGet(String action, HttpSession session, TerracottaServerBusiness serverBusiness) {
    switch (action) {
      case "status":
        return handleClusterStatus(session);
        
      default:
        return ApiResponse.error("Unknown action: " + action);
    }
  }

  @SuppressWarnings("unchecked")
  private ApiResponse<?> handleClusterStatus(HttpSession session) {
    try {
      Map<String, RunningServer> runningServers = (Map<String, RunningServer>) session.getAttribute(RUNNING_SERVERS_ATTR);
      
      Map<String, Object> statusData = new HashMap<>();
      
      if (runningServers == null || runningServers.isEmpty()) {
        statusData.put("status", "STOPPED");
        statusData.put("totalServers", 0);
        statusData.put("runningServers", 0);
        statusData.put("stripes", new ArrayList<>());
      } else {
        // Group servers by stripe
        Map<String, List<Map<String, Object>>> stripeMap = new HashMap<>();
        
        for (Map.Entry<String, RunningServer> entry : runningServers.entrySet()) {
          String key = entry.getKey();
          RunningServer server = entry.getValue();
          
          // Extract stripe name from key (format: "stripe-X-server-Y")
          String stripeName = key.substring(0, key.lastIndexOf("-server-"));
          
          Map<String, Object> serverInfo = new HashMap<>();
          serverInfo.put("name", server.getServerName());
          serverInfo.put("pid", server.getPid());
          serverInfo.put("running", true);
          
          stripeMap.computeIfAbsent(stripeName, k -> new ArrayList<>()).add(serverInfo);
        }
        
        // Determine overall cluster status
        String clusterStatus = runningServers.size() > 0 ? "ACTIVE" : "STOPPED";
        
        statusData.put("status", clusterStatus);
        statusData.put("totalServers", runningServers.size());
        statusData.put("runningServers", runningServers.size());
        statusData.put("stripeCount", stripeMap.size());
        statusData.put("stripes", stripeMap);
      }
      
      return ApiResponse.success(statusData);
      
    } catch (Exception e) {
      LOGGER.error("Failed to get cluster status", e);
      return ApiResponse.error("Failed to get cluster status: " + e.getMessage());
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> readJsonBody(HttpServletRequest request) throws Exception {
    return objectMapper.readValue(request.getInputStream(), Map.class);
  }
}
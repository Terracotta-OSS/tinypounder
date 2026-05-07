package org.terracotta.tinypounder;

import org.terracotta.tinypounder.servlet.ConsoleWebSocketEndpoint;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Mathieu Carbou
 */
public class RunningServer {

  private static final String ACTIVE_PATTERN = "Becoming State[ ACTIVE-COORDINATOR ]";
  private static final Pattern PID_PATTERN = Pattern.compile("PID is ([0-9]*)");
  private static final Pattern PASSIVE_PATTERN = Pattern.compile("Moved to State\\[ (.+) ]");

  private final File workDir;
  private final String clusterName;
  private final String baseLocation;
  private final File stripeconfig;
  private final String stripeName;
  private final String serverName;
  private final String nodeHostname;
  private final String nodePort;
  private final String consoleName;
  private final ArrayBlockingQueue<String> lines;
  private final java.util.List<String> consoleHistory;
  private final Runnable onTerminated;
  private final Consumer<String> onState;
  private final Consumer<Long> onPID;
  private long pid;
  private String serverState;

  public RunningServer(File workDir, String clusterName, String baseLocation, File stripeconfig, String stripeName,
                String serverName, String nodeHostname, String nodePort, String consoleName, int maxLines,
                Runnable onTerminated, Consumer<String> onState, Consumer<Long> onPID) {
    this.workDir = workDir;
    this.clusterName = clusterName;
    this.baseLocation = baseLocation;
    this.stripeconfig = stripeconfig;
    this.stripeName = stripeName;
    this.serverName = serverName;
    this.nodeHostname = nodeHostname;
    this.nodePort = nodePort;
    this.consoleName = consoleName;
    this.lines = new ArrayBlockingQueue<>(maxLines);
    this.consoleHistory = new java.util.concurrent.CopyOnWriteArrayList<>();
    this.onTerminated = onTerminated;
    this.onState = onState;
    this.onPID = onPID;
  }

  public void kill() {
    try {
      ProcUtils.kill(pid);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  public void start() {
    String script = new File(workDir, "server/bin/start-tc-server." + (ProcUtils.isWindows() ? "bat" : "sh")).getAbsolutePath();
    String command;
    if (Paths.get(workDir.toPath().toString(), "tools", "bin", "config-tool.sh").toFile().exists()) {
      // Use baseLocation if provided, otherwise fall back to default
      String basePath = (baseLocation != null && !baseLocation.trim().isEmpty()) 
          ? baseLocation 
          : Paths.get(System.getProperty("user.home"), "terracotta", clusterName).toString();
      Path nodeRepoPath = Paths.get(basePath, "data", "config-db", stripeName, serverName);
      command = script
          + " -f " + stripeconfig.getAbsolutePath()
          + " -s " + nodeHostname
          + " -p " + nodePort
          + " -r " + nodeRepoPath;
    } else {
      command = script + " -f " + stripeconfig.getAbsolutePath() + " -n " + serverName;
    }

    ProcUtils.run(
      workDir,
      command,
      lines,
      newLine -> {
        if (newLine.contains(ACTIVE_PATTERN)) {
          onState.accept("ACTIVE");
        } else if (newLine.contains(" - Started the server in diagnostic mode")) {
          onState.accept("DIAGNOSTIC MODE");
        } else if (newLine.contains("INFO - Moved to State[")) {
          Matcher m = PASSIVE_PATTERN.matcher(newLine);
          if (m.find()) {
            String state = m.group(1);
            // Normalize DIAGNOSTIC to DIAGNOSTIC MODE for UI consistency
            String normalizedState = "DIAGNOSTIC".equals(state) ? "DIAGNOSTIC MODE" : state;
            onState.accept(normalizedState);
            serverState = normalizedState;
          }
        } else if (newLine.contains(" - PID is ")) {
          Matcher m = PID_PATTERN.matcher(newLine);
          if (m.find()) {
            try {
              pid = Long.parseLong(m.group(1));
              onPID.accept(pid);
            } catch (NumberFormatException e) {
              e.printStackTrace();
            }
          }
        }
      },
      onTerminated);
  }

  public void refreshConsole() {
    // Drain and broadcast new console lines via WebSocket
    int size = lines.size();
    if (size > 0) {
      java.util.List<String> lineList = new java.util.ArrayList<>(size);
      lines.drainTo(lineList);
      
      // Add to history for reconnection
      consoleHistory.addAll(lineList);
      
      String[] lineArray = lineList.toArray(new String[0]);
      ConsoleWebSocketEndpoint.broadcastConsoleLines(consoleName, lineArray);
    }
  }
  
  public String[] getConsoleHistory() {
    return consoleHistory.toArray(new String[0]);
  }
  
  public long getPid() {
    return pid;
  }
  
  public String getServerName() {
    return serverName;
  }
  
  public String getConsoleName() {
    return consoleName;
  }

  public String getServerState() {
    return serverState;
  }

  public String getNodeHostname() {
    return nodeHostname;
  }

  public String getNodePort() {
    return nodePort;
  }
}

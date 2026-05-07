package org.terracotta.tinypounder;

import org.terracotta.ipceventbus.proc.AnyProcess;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @author Mathieu Carbou
 */
public class ProcUtils {
  private static String OS = System.getProperty("os.name").toLowerCase();

  static AnyProcess run(File workDir, String command, Queue<String> consoleLines, Consumer<String> onNewLine, Runnable onTerminated) {
    final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    final OutputStream out = new OutputStream() {
      @Override
      public void write(int b) throws IOException {
        buffer.write(b);
        if (((byte) b) == ((byte) 10) || ((byte) b) == ((byte) 13)) { // \n or \r
          flushBuffer();
        }
      }
      
      private void flushBuffer() {
        if (buffer.size() > 0) {
          String newLine = new String(buffer.toByteArray(), StandardCharsets.UTF_8).trim();
          if (!newLine.isEmpty()) {
            while (!consoleLines.offer(newLine)) {
              consoleLines.poll();
            }
            onNewLine.accept(newLine);
          }
          buffer.reset();
        }
      }
      
      @Override
      public void flush() throws IOException {
        flushBuffer();
        super.flush();
      }
      
      @Override
      public void close() throws IOException {
        flushBuffer();
        super.close();
      }
    };

    consoleLines.clear();
    consoleLines.offer("Running:");
    consoleLines.offer(command);
    consoleLines.offer("...");

    AnyProcess process = AnyProcess.newBuilder()
        .command(ProcUtils.isWindows() ? new String[]{"cmd.exe", "/c", command} : new String[]{"/bin/bash", "-c", command})
        .workingDir(workDir)
        .pipeStderr(out)
        .pipeStdout(out)
        .env("JAVA_OPTS", "-Dcom.terracottatech.tools.clustertool.timeout=10000")
        .build();

    Thread waiter = new Thread(() -> {
      try {
        process.waitFor();
        // Flush any remaining buffered output
        try {
          out.flush();
          out.close();
        } catch (IOException e) {
          // Ignore
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      onTerminated.run();
    }, "Process (" + command + ")");
    waiter.setDaemon(true);
    waiter.start();

    return process;
  }

  public static boolean isWindows() {
    return OS.contains("win");
  }

  public static void kill(long pid) throws InterruptedException {
    if (isWindows()) {
      AnyProcess.newBuilder()
          .command("taskkill", "/F", "/T", "/pid", String.valueOf(pid))
          .redirectStderr()
          .recordStdout()
          .build()
          .waitFor();
    } else {
      boolean exited = AnyProcess.newBuilder()
          .command("kill", String.valueOf(pid))
          .redirectStderr()
          .recordStdout()
          .build()
          .waitFor(10, TimeUnit.SECONDS);
      if (!exited) {
        AnyProcess.newBuilder()
            .command("kill", "-9", String.valueOf(pid))
            .redirectStderr()
            .recordStdout()
            .build()
            .waitFor();
      }
    }
  }

  /**
   * Find the PID of a process listening on a specific port.
   * 
   * @param port the port number to check
   * @return the PID of the process listening on the port, or 0 if not found
   */
  public static long findPidByPort(int port) {
    try {
      if (isWindows()) {
        // Windows: netstat -ano | findstr :<port>
        AnyProcess process = AnyProcess.newBuilder()
            .command("cmd.exe", "/c", "netstat -ano | findstr :" + port)
            .recordStdout()
            .redirectStderr()
            .build();
        
        process.waitFor(5, TimeUnit.SECONDS);
        byte[] outputBytes = process.getRecordedStdout();
        String output = outputBytes != null ? new String(outputBytes, StandardCharsets.UTF_8) : "";
        
        // Parse output like: TCP    0.0.0.0:9410    0.0.0.0:0    LISTENING    12345
        if (!output.isEmpty()) {
          String[] lines = output.split("\n");
          for (String line : lines) {
            if (line.contains("LISTENING") || line.contains("ESTABLISHED")) {
              String[] parts = line.trim().split("\\s+");
              if (parts.length >= 5) {
                try {
                  return Long.parseLong(parts[parts.length - 1]);
                } catch (NumberFormatException e) {
                  // Continue to next line
                }
              }
            }
          }
        }
      } else {
        // Unix/Mac: lsof -ti :port
        AnyProcess process = AnyProcess.newBuilder()
            .command("lsof", "-ti", ":" + port)
            .recordStdout()
            .redirectStderr()
            .build();
        
        process.waitFor(5, TimeUnit.SECONDS);
        byte[] outputBytes = process.getRecordedStdout();
        String output = outputBytes != null ? new String(outputBytes, StandardCharsets.UTF_8) : "";
        
        if (!output.isEmpty()) {
          String pidStr = output.trim().split("\n")[0].trim();
          try {
            return Long.parseLong(pidStr);
          } catch (NumberFormatException e) {
            // PID not found
          }
        }
      }
    } catch (Exception e) {
      // Ignore errors and return 0
    }
    
    return 0;
  }
}

package org.terracotta.tinypounder;

import org.terracotta.ipceventbus.proc.AnyProcess;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.function.Consumer;

/**
 * @author Mathieu Carbou
 */
public class ProcUtils {
  private static String OS = System.getProperty("os.name").toLowerCase();

  static AnyProcess run(File workDir, String command, Queue<String> consoleLines, Consumer<String> onNewLine, Runnable onStop) {
    final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    final OutputStream out = new OutputStream() {
      @Override
      public void write(int b) throws IOException {
        buffer.write(b);
        if (((byte) b) == ((byte) 10)) {
          String newLine = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
          while (!consoleLines.offer(newLine)) {
            consoleLines.poll();
          }
          buffer.reset();
          onNewLine.accept(newLine);
        }
      }
    };

    consoleLines.clear();
    consoleLines.offer("Running:");
    consoleLines.offer(command);
    consoleLines.offer("...");

    AnyProcess process = AnyProcess.newBuilder()
        .command(ProcUtils.isWindows() ? new String[]{"cmd.exe", "/c", command} : new String[]{"/bin/bash", "-c", command})
        .workingDir(workDir)
        .redirectStderr()
        .pipeStdout(out)
        .build();

    Thread waiter = new Thread(() -> {
      try {
        process.waitFor();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      onStop.run();
    }, "Process (" + command + ")");
    waiter.setDaemon(true);
    waiter.start();

    return process;
  }

  static boolean isWindows() {
    return OS.contains("win");
  }

  static void kill(long pid) throws InterruptedException {
    if (isWindows()) {
      AnyProcess.newBuilder()
          .command("taskkill", "/F", "/t", "/pid", String.valueOf(pid))
          .redirectStderr()
          .recordStdout()
          .build()
          .waitFor();
    } else {
      AnyProcess.newBuilder()
          .command("kill", String.valueOf(pid))
          .redirectStderr()
          .recordStdout()
          .build()
          .waitFor();
    }
  }
}

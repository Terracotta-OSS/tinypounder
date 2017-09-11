/*
 * Copyright (c) 2011-2017 Software AG, Darmstadt, Germany and/or Software AG USA Inc., Reston, VA, USA, and/or its subsidiaries and/or its affiliates and/or their licensors.
 * Use, reproduction, transfer, publication or disclosure is prohibited except as specifically provided for in your License Agreement with Software AG.
 */

package org.terracotta.tinypounder;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.io.IOException;
import java.net.URI;

@Component
public class ApplicationReadyListener implements ApplicationListener<ApplicationReadyEvent> {

  private static final boolean IS_MAC = System.getProperty("os.name", "unknown").toLowerCase().contains("mac");

  @SuppressWarnings("unchecked")
  @Override
  public void onApplicationEvent(ApplicationReadyEvent event) {
    URI uri = URI.create("http://localhost:9490/");
    if (Desktop.isDesktopSupported()) {
      Desktop desktop = Desktop.getDesktop();
      if (desktop.isSupported(Desktop.Action.BROWSE)) {
        try {
          desktop.browse(uri);
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    } else if (IS_MAC) {
      openUrlInBrowser(uri.toString());
    }
  }

  private void openUrlInBrowser(String url) {
    Runtime runtime = Runtime.getRuntime();
    String[] args = {"osascript", "-e", "open location \"" + url + "\""};
    try {
      runtime.exec(args);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

}

package org.terracotta.tinypounder;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * @author Mathieu Carbou
 */
@Service
public class Settings {

  @Value("${kitPath}")
  private String kitPath;

  @Value("${serverSecurityPath}")
  private String serverSecurityPath;

  @Value("${clientSecurityPath}")
  private String clientSecurityPath;

  @Value("${licensePath}")
  private String licensePath;

  @Value("${failoverPriority}")
  private String failoverPriority;

  @Value("${offheapCount}")
  private int offheapCount;

  @Value("${dataRootCount}")
  private int dataRootCount;

  @Value("${serverCount}")
  private int serverCount;

  @Value("${stripeCount}")
  private int stripeCount;

  @Value("${reconnectWindow}")
  private int reconnectWindow;

  private final File config = new File(System.getProperty("user.home"), ".tinypounder/config.properties");

  @PostConstruct
  public void load() {
    config.getParentFile().mkdirs();
    Properties properties = new Properties();
    if (config.exists()) {
      try (InputStreamReader reader = new InputStreamReader(new FileInputStream(config), "UTF-8")) {
        properties.load(reader);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    // always prefer system props over saved config
    if (serverSecurityPath == null || serverSecurityPath.isEmpty()) {
      serverSecurityPath = properties.getProperty("serverSecurityPath");
      if (serverSecurityPath != null) {
        File folder = new File(serverSecurityPath);
        if (!folder.exists() || !folder.isDirectory()) {
          serverSecurityPath = null;
        }
      }
    }

    if (clientSecurityPath == null || clientSecurityPath.isEmpty()) {
      clientSecurityPath = properties.getProperty("clientSecurityPath");
      if (clientSecurityPath != null) {
        File folder = new File(clientSecurityPath);
        if (!folder.exists() || !folder.isDirectory()) {
          clientSecurityPath = null;
        }
      }
    }

    if (kitPath == null || kitPath.isEmpty()) {
      kitPath = properties.getProperty("kitPath");
      if (kitPath != null) {
        File folder = new File(kitPath);
        if (!folder.exists() || !folder.isDirectory()) {
          kitPath = null;
        }
      }
    }
    if (licensePath == null || licensePath.isEmpty()) {
      licensePath = properties.getProperty("licensePath");
      if (licensePath != null) {
        File file = new File(licensePath);
        if (!file.exists() || !file.isFile()) {
          licensePath = null;
        }
      }
    }
    if (failoverPriority == null || failoverPriority.isEmpty()) {
      failoverPriority = properties.getProperty("failoverPriority");
      if (failoverPriority == null) {
        failoverPriority = TinyPounderMainUI.AVAILABILITY;
      }
    }
    if (offheapCount < 1) {
      offheapCount = Integer.parseInt(properties.getProperty("offheapCount", "0"));
      if (offheapCount < 1) {
        offheapCount = 2;
      }
    }
    if (dataRootCount < 1) {
      dataRootCount = Integer.parseInt(properties.getProperty("dataRootCount", "0"));
      if (dataRootCount < 1) {
        dataRootCount = 2;
      }
    }
    if (serverCount < 1) {
      serverCount = Integer.parseInt(properties.getProperty("serverCount", "0"));
      if (serverCount < 1) {
        serverCount = 2;
      }
    }
    if (stripeCount < 1) {
      stripeCount = Integer.parseInt(properties.getProperty("stripeCount", "0"));
      if (stripeCount < 1) {
        stripeCount = 2;
      }
    }
    if (reconnectWindow < 5) {
      reconnectWindow = Integer.parseInt(properties.getProperty("reconnectWindow", "0"));
      if (reconnectWindow < 5) {
        reconnectWindow = 120;
      }
    }
  }

  @PreDestroy
  public void save() {
    Properties properties = new Properties();
    if (kitPath != null) {
      properties.setProperty("kitPath", kitPath);
    }
    if (failoverPriority != null) {
      properties.setProperty("failoverPriority", failoverPriority);
    }
    if (serverSecurityPath != null) {
      properties.setProperty("serverSecurityPath", serverSecurityPath);
    }
    if (clientSecurityPath != null) {
      properties.setProperty("clientSecurityPath", clientSecurityPath);
    }
    if (licensePath != null) {
      properties.setProperty("licensePath", licensePath);
    }
    if (offheapCount >= 1) {
      properties.setProperty("offheapCount", String.valueOf(offheapCount));
    }
    if (dataRootCount >= 1) {
      properties.setProperty("dataRootCount", String.valueOf(dataRootCount));
    }
    if (serverCount >= 1) {
      properties.setProperty("serverCount", String.valueOf(serverCount));
    }
    if (stripeCount >= 1) {
      properties.setProperty("stripeCount", String.valueOf(stripeCount));
    }
    if (reconnectWindow >= 5) {
      properties.setProperty("reconnectWindow", String.valueOf(reconnectWindow));
    }
    try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(config), "UTF-8")) {
      properties.store(writer, "AUTO-GENERATED FILE");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public String getServerSecurityPath() {
    return serverSecurityPath;
  }

  public void setServerSecurityPath(String serverSecurityPath) {
    this.serverSecurityPath = serverSecurityPath;
  }

  public String getClientSecurityPath() {
    return clientSecurityPath;
  }

  public void setClientSecurityPath(String clientSecurityPath) {
    this.clientSecurityPath = clientSecurityPath;
  }

  public String getKitPath() {
    return kitPath;
  }

  public void setKitPath(String kitPath) {
    this.kitPath = kitPath;
  }

  public String getLicensePath() {
    return licensePath;
  }

  public void setLicensePath(String licensePath) {
    this.licensePath = licensePath;
  }

  public int getOffheapCount() {
    return offheapCount;
  }

  public int getDataRootCount() {
    return dataRootCount;
  }

  public int getServerCount() {
    return serverCount;
  }

  public int getStripeCount() {
    return stripeCount;
  }

  public void setOffheapCount(int offheapCount) {
    this.offheapCount = offheapCount;
  }

  public void setDataRootCount(int dataRootCount) {
    this.dataRootCount = dataRootCount;
  }

  public void setStripeCount(int stripeCount) {
    this.stripeCount = stripeCount;
  }

  public void setServerCount(int serverCount) {
    this.serverCount = serverCount;
  }

  public int getReconnectWindow() {
    return reconnectWindow;
  }

  public void setReconnectWindow(int reconnectWindow) {
    this.reconnectWindow = reconnectWindow;
  }

  public String getFailoverPriority() {
    return failoverPriority;
  }

  public String getFailoverPriorityMode() {
    final int i = failoverPriority.indexOf(":");
    return failoverPriority.substring(0, i == -1 ? failoverPriority.length() : i);
  }

  public String getVoterCount() {
    final int i = failoverPriority.indexOf(":");
    return i == -1 ? "1" : failoverPriority.substring(i + 1);
  }

  public void setFailoverPriority(String failoverPriority) {
    this.failoverPriority = failoverPriority;
  }
}

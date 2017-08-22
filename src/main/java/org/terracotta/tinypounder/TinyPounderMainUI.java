/*
 * Copyright Terracotta, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terracotta.tinypounder;

import com.vaadin.annotations.PreserveOnRefresh;
import com.vaadin.annotations.Push;
import com.vaadin.annotations.StyleSheet;
import com.vaadin.annotations.Title;
import com.vaadin.data.HasValue;
import com.vaadin.data.provider.ListDataProvider;
import com.vaadin.server.Page;
import com.vaadin.server.VaadinRequest;
import com.vaadin.spring.annotation.SpringUI;
import com.vaadin.ui.AbstractComponent;
import com.vaadin.ui.Button;
import com.vaadin.ui.CheckBox;
import com.vaadin.ui.ComboBox;
import com.vaadin.ui.Component;
import com.vaadin.ui.FormLayout;
import com.vaadin.ui.GridLayout;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.Notification;
import com.vaadin.ui.PopupView;
import com.vaadin.ui.Slider;
import com.vaadin.ui.TabSheet;
import com.vaadin.ui.TextArea;
import com.vaadin.ui.TextField;
import com.vaadin.ui.UI;
import com.vaadin.ui.Upload;
import com.vaadin.ui.VerticalLayout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * The UI class
 * <p>
 * This class should never import anything from the Ehcache API
 */
@Title("Tiny Pounder")
@StyleSheet("tinypounder.css")
@Push
@SpringUI
@PreserveOnRefresh
public class TinyPounderMainUI extends UI {

  private static final int MIN_SERVER_GRID_COLS = 3;
  private static final int DATAROOT_PATH_COLUMN = 2;
  private static final File HOME = new File(System.getProperty("user.home"));

  @Autowired
  private CacheManagerBusiness cacheManagerBusiness;

  @Autowired
  private DatasetManagerBusinessReflectionImpl datasetManagerBusiness;

  @Autowired
  private KitAwareClassLoaderDelegator kitAwareClassLoaderDelegator;

  @Autowired
  private ScheduledExecutorService scheduledExecutorService;

  @Autowired
  private ApplicationContext appContext;

  @Value("${licensePath}")
  private String licensePath;

  private TabSheet mainLayout;
  private VerticalLayout cacheLayout;
  private VerticalLayout datasetLayout;

  private VerticalLayout cacheControls;
  private VerticalLayout datasetControls;
  private VerticalLayout cacheManagerControls;
  private VerticalLayout datasetManagerControls;
  private VerticalLayout voltronConfigLayout;
  private VerticalLayout voltronControlLayout;
  private VerticalLayout runtimeLayout;

  private Slider stripes;
  private Slider servers;
  private GridLayout offheapGrid;
  private Slider offheaps;
  private GridLayout serverGrid;
  private Slider reconnectWindow;
  private GridLayout dataRootGrid;
  private Slider dataRoots;
  private CheckBox platformPersistence;
  private CheckBox platformBackup;
  private TextArea tcConfigXml;
  private GridLayout kitPathLayout;
  private File temporaryUploadedLicenseFile;
  private TextField clusterNameTF;
  private Map<String, File> tcConfigLocationPerStripe = new ConcurrentHashMap<>();
  private GridLayout serverControls;
  private TabSheet consoles;
  private TextArea mainConsole;
  private Map<String, RunningServer> runningServers = new ConcurrentHashMap<>();
  private VerticalLayout kitControlsLayout;
  private ScheduledFuture<?> consoleRefresher;
  private Button kitPathBT;
  private Button generateTcConfig;

  @Override
  protected void init(VaadinRequest vaadinRequest) {
    setupLayout();
    addKitControls();
    updateKitControls();
    initVoltronConfigLayout();
    initVoltronControlLayout();
    initRuntimeLayout();
    updateServerGrid();

    // refresh consoles if any
    consoleRefresher = scheduledExecutorService.scheduleWithFixedDelay(
        () -> access(() -> runningServers.values().forEach(RunningServer::refreshConsole)),
        2, 2, TimeUnit.SECONDS);
  }

  private void updateKitControls() {
    if (kitAwareClassLoaderDelegator.isEEKit()) {
      if (kitPathLayout.getRows() == 1) {
        TextField path = new TextField();
        path.setWidth(100, Unit.PERCENTAGE);
        path.setValue(licensePath == null ? "" : licensePath);
        path.setPlaceholder("License location");
        Upload upload = new Upload();
        upload.setReceiver((Upload.Receiver) (filename, mimeType) -> {
          File tmpDir = new File(System.getProperty("java.io.tmpdir"));
          temporaryUploadedLicenseFile = new File(tmpDir, filename);
          temporaryUploadedLicenseFile.deleteOnExit();
          try {
            return new FileOutputStream(temporaryUploadedLicenseFile);
          } catch (FileNotFoundException e) {
            throw new UncheckedIOException(e);
          }
        });
        upload.setButtonCaption("Browse...");
        upload.setImmediateMode(true);
        upload.addSucceededListener((Upload.SucceededListener) event -> {
          licensePath = temporaryUploadedLicenseFile.getAbsolutePath();
          path.setValue(licensePath);
        });
        kitPathLayout.addComponent(path);
        kitPathLayout.addComponent(upload);
      }
    } else {
      if (kitPathLayout.getRows() == 2) {
        kitPathLayout.removeRow(1);
      }
    }
  }

  private void initRuntimeLayout() {
    if (datasetLayout == null && cacheLayout == null
        && (kitAwareClassLoaderDelegator.containsTerracottaStore() || kitAwareClassLoaderDelegator.containsEhcache())) {
      runtimeLayout = new VerticalLayout();
      mainLayout.addTab(runtimeLayout, "STEP 4: DATASETS & CACHES");

      initCacheLayout();
      initDatasetLayout();
    }
  }

  private void initDatasetLayout() {
    if (kitAwareClassLoaderDelegator.containsTerracottaStore() && datasetLayout == null) {
      datasetLayout = new VerticalLayout();
      datasetLayout.addStyleName("dataset-layout");
      runtimeLayout.addComponentsAndExpand(datasetLayout);
      addDatasetManagerControls();
    }
  }

  private void initCacheLayout() {
    if (kitAwareClassLoaderDelegator.containsEhcache() && cacheLayout == null) {
      cacheLayout = new VerticalLayout();
      cacheLayout.addStyleName("cache-layout");
      runtimeLayout.addComponentsAndExpand(cacheLayout);
      addCacheManagerControls();
    }
  }

  private void initVoltronConfigLayout() {
    if (kitAwareClassLoaderDelegator.getKitPath() != null) {
      if (voltronConfigLayout == null) {
        voltronConfigLayout = new VerticalLayout();
        voltronConfigLayout.addStyleName("voltron-config-layout");
        mainLayout.addTab(voltronConfigLayout, "STEP 2: SERVER CONFIGURATIONS");
      }
      addVoltronConfigControls();
    }
  }

  private void initVoltronControlLayout() {
    if (kitAwareClassLoaderDelegator.getKitPath() != null) {
      if (voltronControlLayout == null) {
        voltronControlLayout = new VerticalLayout();
        voltronControlLayout.addStyleName("voltron-control-layout");
        mainLayout.addTab(voltronControlLayout, "STEP 3: SERVER CONTROL");
      }
      addVoltronCommandsControls();
    }
  }

  private void addVoltronCommandsControls() {
    serverControls = new GridLayout();
    serverControls.setWidth(50, Unit.PERCENTAGE);
    voltronControlLayout.addComponentsAndExpand(serverControls);

    if (kitAwareClassLoaderDelegator.isEEKit()) {
      clusterNameTF = new TextField();
      clusterNameTF.setCaption("Cluster name");
      clusterNameTF.setValue("MyCluster");

      Button clusterStartBtn = new Button();
      clusterStartBtn.setCaption("Start all servers");
      clusterStartBtn.addStyleName("align-bottom");
      clusterStartBtn.addClickListener(event -> {
        for (Component child : serverControls) {
          if (child instanceof Button && "START".equals(child.getCaption()) && child.isEnabled()) {
            ((Button) child).click();
          }
        }
      });

      Button clusterConfigBtn = new Button();
      clusterConfigBtn.addStyleName("align-bottom");
      clusterConfigBtn.setCaption("Configure");
      clusterConfigBtn.setData("configure");
      clusterConfigBtn.addClickListener((Button.ClickListener) this::executeClusterToolCommand);

      Button clusterReConfigBtn = new Button();
      clusterReConfigBtn.addStyleName("align-bottom");
      clusterReConfigBtn.setCaption("Reconfigure");
      clusterReConfigBtn.setData("reconfigure");
      clusterReConfigBtn.addClickListener((Button.ClickListener) this::executeClusterToolCommand);

      Button clusterBackupBtn = new Button();
      clusterBackupBtn.addStyleName("align-bottom");
      clusterBackupBtn.setCaption("Backup");
      clusterBackupBtn.setData("backup");
      clusterBackupBtn.addClickListener((Button.ClickListener) this::executeClusterToolCommand);

      Button clusterDumpBtn = new Button();
      clusterDumpBtn.addStyleName("align-bottom");
      clusterDumpBtn.setCaption("Dump");
      clusterDumpBtn.setData("dump");
      clusterDumpBtn.addClickListener((Button.ClickListener) this::executeClusterToolCommand);

      Button clusterStopBtn = new Button();
      clusterStopBtn.addStyleName("align-bottom");
      clusterStopBtn.setCaption("Stop cluster");
      clusterStopBtn.setData("stop");
      clusterStopBtn.addClickListener((Button.ClickListener) this::executeClusterToolCommand);

      HorizontalLayout row1 = new HorizontalLayout();
      row1.addComponents(clusterNameTF, clusterStartBtn, clusterConfigBtn, clusterReConfigBtn, clusterBackupBtn, clusterDumpBtn, clusterStopBtn);

      voltronControlLayout.addComponentsAndExpand(row1);
    }

    consoles = new TabSheet();
    mainConsole = addConsole("Main", "main");
    voltronControlLayout.addComponentsAndExpand(consoles);
  }

  private void executeClusterToolCommand(Button.ClickEvent event) {
    String command = (String) event.getButton().getData();
    File workDir = new File(kitAwareClassLoaderDelegator.getKitPath());
    LinkedBlockingQueue<String> consoleLines = new LinkedBlockingQueue<>(); // no limit, get all the output
    String script = new File(workDir, "tools/cluster-tool/bin/cluster-tool." + (ProcUtils.isWindows() ? "bat" : "sh")).getAbsolutePath();
    String configs = tcConfigLocationPerStripe.values().stream().map(File::getAbsolutePath).collect(Collectors.joining(" "));
    List<String> hostPortList = getHostPortList();

    switch (command) {

      case "configure":
      case "reconfigure": {
        if (licensePath == null) {
          Notification.show("ERROR", "Please set a license file location!", Notification.Type.ERROR_MESSAGE);
          return;
        }
        ProcUtils.run(
            workDir,
            script + " " + command + "  -n " + clusterNameTF.getValue() + " -l " + licensePath + " " + configs,
            consoleLines,
            newLine -> {
            },
            () -> access(() -> updateMainConsole(consoleLines)));
        break;
      }

      case "dump":
      case "backup":
      case "stop": {
        ProcUtils.run(
            workDir,
            script + " " + command + " -n " + clusterNameTF.getValue() + " " + hostPortList.get(0),
            consoleLines,
            newLine -> access(() -> updateMainConsole(consoleLines)),
            () -> access(() -> consoles.setSelectedTab(mainConsole)));
        break;
      }

    }

    consoles.setSelectedTab(mainConsole);
    updateMainConsole(consoleLines);
  }

  private void updateMainConsole(Queue<String> consoleLines) {
    String text = String.join("\n", consoleLines);
    mainConsole.setValue(text);
    mainConsole.setCursorPosition(text.length());
  }

  private TextArea addConsole(String title, String key) {
    TextArea console = new TextArea();
    console.setData(key);
    console.setWidth(100, Unit.PERCENTAGE);
    console.setWordWrap(false);
    console.setStyleName("console");

    consoles.addTab(console, title);

    return console;
  }

  private void updateServerControls() {
    int nStripes = serverGrid.getRows() - 1;
    int nServersPerStripe = serverGrid.getColumns() - 1;

    serverControls.removeAllComponents();
    serverControls.setRows(nStripes * nServersPerStripe);
    serverControls.setColumns(5);

    for (int i = consoles.getComponentCount() - 1; i > 0; i--) {
      consoles.removeTab(consoles.getTab(i));
    }

    for (int stripeId = 1; stripeId < serverGrid.getRows(); stripeId++) {
      String stripeName = "stripe-" + stripeId;

      for (int serverId = 1; serverId < serverGrid.getColumns(); serverId++) {
        FormLayout form = (FormLayout) serverGrid.getComponent(serverId, stripeId);
        if (form != null) {
          TextField serverNameTF = (TextField) form.getComponent(0);
          String serverName = serverNameTF.getValue();
          serverControls.addComponent(new Label(serverName));

          Button startBT = new Button();
          startBT.setCaption("START");
          startBT.setData(serverName);
          serverControls.addComponent(startBT);

          Button stopBT = new Button();
          stopBT.setEnabled(false);
          stopBT.setCaption("STOP");
          stopBT.setData(serverName);
          serverControls.addComponent(stopBT);

          Label pid = new Label();
          serverControls.addComponent(pid);

          Label state = new Label();
          serverControls.addComponent(state);

          addConsole(serverName, stripeName + "-" + serverName);

          startBT.addClickListener((Button.ClickListener) event -> {
            startServer(stripeName, (String) event.getButton().getData(), startBT, stopBT, state, pid);
          });
          stopBT.addClickListener((Button.ClickListener) event -> {
            stopServer(stripeName, (String) event.getButton().getData(), stopBT);
          });
        }
      }
    }
  }

  private List<String> getHostPortList() {
    int nStripes = serverGrid.getRows() - 1;
    int nServersPerStripe = serverGrid.getColumns() - 1;
    List<String> servers = new ArrayList<>(nStripes * nServersPerStripe);
    for (int stripeId = 1; stripeId < serverGrid.getRows(); stripeId++) {
      for (int serverId = 1; serverId < serverGrid.getColumns(); serverId++) {
        FormLayout form = (FormLayout) serverGrid.getComponent(serverId, stripeId);
        if (form != null) {
          TextField clientPortTF = (TextField) form.getComponent(2);
          servers.add("localhost:" + clientPortTF.getValue());
        }
      }
    }
    return servers;
  }

  private void stopServer(String stripeName, String serverName, Button stopBT) {
    RunningServer runningServer = runningServers.get(stripeName + "-" + serverName);
    if (runningServer != null) {
      runningServer.stop();
      stopBT.setEnabled(false);
      runningServer.refreshConsole();
    }
  }

  private void startServer(String stripeName, String serverName, Button startBT, Button stopBT, Label stateLBL, Label pidLBL) {
    File stripeconfig = tcConfigLocationPerStripe.get(stripeName);
    if (stripeconfig == null) {
      generateXML();
      stripeconfig = tcConfigLocationPerStripe.get(stripeName);
    }

    File workDir = new File(kitAwareClassLoaderDelegator.getKitPath());
    String key = stripeName + "-" + serverName;
    TextArea console = getConsole(key);

    RunningServer runningServer = new RunningServer(
        workDir, stripeconfig, serverName, console, 500,
        () -> {
          runningServers.remove(key);
          access(() -> {
            stopBT.setEnabled(false);
            startBT.setEnabled(true);
            voltronConfigLayout.setEnabled(runningServers.isEmpty());
            kitPathBT.setEnabled(runningServers.isEmpty());
            pidLBL.setValue("");
            stateLBL.setValue("STOPPED");
          });
        },
        newState -> access(() -> stateLBL.setValue("STATE: " + newState)),
        newPID -> access(() -> pidLBL.setValue("PID: " + newPID))
    );

    if (runningServers.put(key, runningServer) != null) {
      Notification.show("ERROR", "Server is running: " + serverName, Notification.Type.ERROR_MESSAGE);
      return;
    }

    consoles.setSelectedTab(console);
    stateLBL.setValue("STARTING");
    runningServer.start();
    voltronConfigLayout.setEnabled(false);
    kitPathBT.setEnabled(false);
    startBT.setEnabled(false);
    stopBT.setEnabled(true);
    runningServer.refreshConsole();
  }

  private TextArea getConsole(String key) throws NoSuchElementException {
    for (Component console : consoles) {
      if (key.equals(((AbstractComponent) console).getData())) {
        return (TextArea) console;
      }
    }
    throw new NoSuchElementException("No console found for " + key);
  }

  private void addVoltronConfigControls() {
    VerticalLayout layout = new VerticalLayout();
    boolean ee = kitAwareClassLoaderDelegator.isEEKit();

    // offheap resources
    {
      int nOffheaps = 2;

      offheapGrid = new GridLayout(2, nOffheaps + 1);

      offheaps = new Slider(nOffheaps + " offheap resources", 1, 4);
      offheaps.setValue((double) nOffheaps);
      offheaps.addValueChangeListener((HasValue.ValueChangeListener<Double>) event -> {
        offheaps.setCaption(event.getValue().intValue() + " offheap resources");
        updateOffHeapGrid();
      });
      offheapGrid.addComponent(offheaps, 0, 0, 1, 0);

      updateOffHeapGrid();

      layout.addComponentsAndExpand(offheapGrid);
    }

    // ee stuff
    if (ee) {
      int nData = 2;

      dataRootGrid = new GridLayout(3, nData + 1);
      dataRootGrid.setWidth(100, Unit.PERCENTAGE);
      dataRootGrid.setColumnExpandRatio(2, 2);

      // data roots
      dataRoots = new Slider(nData + " data roots", 1, 10);
      dataRoots.setValue((double) nData);
      dataRoots.addValueChangeListener((HasValue.ValueChangeListener<Double>) event -> {
        dataRoots.setCaption(event.getValue().intValue() + " data roots");
        updateDataRootGrid();
      });
      dataRootGrid.addComponent(dataRoots);

      // platform persistence
      platformPersistence = new CheckBox("Platform Persistence", true);
      platformPersistence.addValueChangeListener(event -> platformPersistenceWanted(event.getValue()));
      dataRootGrid.addComponent(platformPersistence);

      // backup
      platformBackup = new CheckBox("Platform Backup", true);
      platformBackup.addValueChangeListener(event -> platformBackupWanted(event.getValue()));
      dataRootGrid.addComponent(platformBackup);

      updateDataRootGrid();
      platformBackupWanted(true);
      platformPersistenceWanted(true);

      layout.addComponentsAndExpand(dataRootGrid);
    }

    // stripe / server form
    {
      int nStripes = ee ? 2 : 1;
      int nServers = 2;
      int nReconWin = 120;

      serverGrid = new GridLayout(MIN_SERVER_GRID_COLS, nStripes + 1);
      serverGrid.setWidth(100, Unit.PERCENTAGE);

      stripes = new Slider(nStripes + " stripes", 1, ee ? 4 : 1);
      stripes.setValue((double) nStripes);
      stripes.addValueChangeListener((HasValue.ValueChangeListener<Double>) event -> {
        stripes.setCaption(event.getValue().intValue() + " stripes");
        updateServerGrid();
      });
      stripes.setReadOnly(!ee);
      serverGrid.addComponent(stripes);

      servers = new Slider(nServers + " servers per stripe", 1, 4);
      servers.setValue((double) nServers);
      servers.addValueChangeListener((HasValue.ValueChangeListener<Double>) event -> {
        servers.setCaption(event.getValue().intValue() + " servers per stripe");
        updateServerGrid();
      });
      serverGrid.addComponent(servers);

      reconnectWindow = new Slider("Reconnect window: " + nReconWin + " seconds", 5, 300);
      reconnectWindow.setValue((double) nReconWin);
      reconnectWindow.addValueChangeListener((HasValue.ValueChangeListener<Double>) event -> {
        reconnectWindow.setCaption("Reconnect window: " + event.getValue().intValue() + " seconds");
      });
      serverGrid.addComponent(reconnectWindow);

      layout.addComponentsAndExpand(serverGrid);
    }

    // XML file generation
    {
      generateTcConfig = new Button("Generate all tc-config.xml files");
      generateTcConfig.addStyleName("align-bottom");
      generateTcConfig.setWidth(100, Unit.PERCENTAGE);
      generateTcConfig.addClickListener((Button.ClickListener) event -> {
        generateXML();
        List<String> filenames = tcConfigLocationPerStripe.values().stream().map(File::getName).collect(Collectors.toList());
        Notification.show("Configurations saved:", "Location: " + kitAwareClassLoaderDelegator.getKitPath() + "\nFiles: " + filenames, Notification.Type.HUMANIZED_MESSAGE);
      });
      layout.addComponentsAndExpand(generateTcConfig);

      tcConfigXml = new TextArea();
      tcConfigXml.setWidth(100, Unit.PERCENTAGE);
      tcConfigXml.setWordWrap(false);
      tcConfigXml.setRows(50);
      tcConfigXml.setStyleName("tc-config-xml");
      layout.addComponentsAndExpand(tcConfigXml);
    }

    voltronConfigLayout.addComponentsAndExpand(layout);
  }

  private void generateXML() {
    boolean ee = kitAwareClassLoaderDelegator.isEEKit();

    tcConfigLocationPerStripe.clear();
    tcConfigXml.setValue("");

    for (int stripeRow = 1; stripeRow < serverGrid.getRows(); stripeRow++) {

      // starts xml
      StringBuilder sb;
      if (ee) {
        sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "\n" +
            "<tc-config xmlns=\"http://www.terracotta.org/config\" \n" +
            "           xmlns:ohr=\"http://www.terracotta.org/config/offheap-resource\"\n" +
            "           xmlns:backup=\"http://www.terracottatech.com/config/backup-restore\"\n" +
            "           xmlns:data=\"http://www.terracottatech.com/config/data-roots\">\n" +
            "\n" +
            "  <plugins>\n" +
            "\n");
      } else {
        sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "\n" +
            "<tc-config xmlns=\"http://www.terracotta.org/config\" \n" +
            "           xmlns:ohr=\"http://www.terracotta.org/config/offheap-resource\">\n" +
            "\n" +
            "  <plugins>\n" +
            "\n");
      }

      // offheaps
      if (offheapGrid.getRows() > 1) {
        sb.append("    <config>\n" +
            "      <ohr:offheap-resources>\n");
        for (int r = 1; r < offheapGrid.getRows(); r++) {
          TextField name = (TextField) offheapGrid.getComponent(0, r);
          TextField memory = (TextField) offheapGrid.getComponent(1, r);
          sb.append("        <ohr:resource name=\"" + name.getValue() + "\" unit=\"MB\">" + memory.getValue() + "</ohr:resource>\n");
        }
        sb.append("      </ohr:offheap-resources>\n" +
            "    </config>\n" +
            "\n");
      }

      if (ee) {
        // dataroots
        sb.append("    <config>\n" +
            "      <data:data-directories>\n");
        // platform persistece
        if (platformPersistence.getValue()) {
          TextField path = (TextField) dataRootGrid.getComponent(DATAROOT_PATH_COLUMN, getPersistenceRow());
          sb.append("        <data:directory name=\"PLATFORM\" use-for-platform=\"true\">" + path.getValue() + "</data:directory>\n");
        }

        // do not know why but .getComponent(x,y) does not work
//        for (int r = getDataRootFirstRow(); r < dataRootGrid.getRows(); r++) {
//          TextField name = (TextField) dataRootGrid.getComponent(DATAROOT_NAME_COLUMN, r);
//          TextField path = (TextField) dataRootGrid.getComponent(DATAROOT_PATH_COLUMN, r);
//          sb.append("        <data:directory name=\"" + name.getValue() + "\" use-for-platform=\"false\">" + path.getValue() + "</data:directory>\n");
//        }

        // workaround - iterate over all components
        List<Component> components = new ArrayList<>();
        dataRootGrid.iterator().forEachRemaining(components::add);
        // remove header
        components.remove(0);
        components.remove(0);
        components.remove(0);
        if (platformBackup.getValue()) {
          components.remove(0);
          components.remove(0);
        }
        if (platformPersistence.getValue()) {
          components.remove(0);
          components.remove(0);
        }
        for (int i = 0; i < components.size(); i += 2) {
          TextField name = (TextField) components.get(i);
          TextField path = (TextField) components.get(i + 1);
          sb.append("        <data:directory name=\"" + name.getValue() + "\" use-for-platform=\"false\">" + path.getValue() + "</data:directory>\n");
        }

        // end data roots    
        sb.append("      </data:data-directories>\n" +
            "    </config>\n" +
            "\n");

        // backup
        if (platformBackup.getValue()) {
          TextField path = (TextField) dataRootGrid.getComponent(DATAROOT_PATH_COLUMN, getBackupRow());
          sb.append("    <service>\n" +
              "      <backup:backup-restore>\n" +
              "        <backup:backup-location path=\"" + path.getValue() + "\" />\n" +
              "      </backup:backup-restore>\n" +
              "    </service>\n" +
              "\n");
        }
      }

      // servers
      sb.append("  </plugins>\n" +
          "\n" +
          "  <servers>\n" +
          "\n");

      for (int serverCol = 1; serverCol < serverGrid.getColumns(); serverCol++) {
        FormLayout form = (FormLayout) serverGrid.getComponent(serverCol, stripeRow);
        if (form != null) {
          TextField name = (TextField) form.getComponent(0);
          TextField logs = (TextField) form.getComponent(1);
          TextField clientPort = (TextField) form.getComponent(2);
          TextField groupPort = (TextField) form.getComponent(3);
          sb.append("    <server host=\"localhost\" name=\"" + name.getValue() + "\">\n" +
              "      <logs>" + logs.getValue() + "</logs>\n" +
              "      <tsa-port>" + clientPort.getValue() + "</tsa-port>\n" +
              "      <tsa-group-port>" + groupPort.getValue() + "</tsa-group-port>\n" +
              "    </server>\n\n");
        }
      }

      // reconnect window
      sb.append("    <client-reconnect-window>" + reconnectWindow.getValue().intValue() + "</client-reconnect-window>\n\n");

      // ends XML
      sb.append("  </servers>\n\n" +
          "</tc-config>");

      String xml = sb.toString();

      tcConfigXml.setValue(tcConfigXml.getValue() + xml + "\n\n");

      String filename = "tc-config-stripe-" + stripeRow + ".xml";
      File location = new File(kitAwareClassLoaderDelegator.getKitPath(), filename);
      tcConfigLocationPerStripe.put("stripe-" + stripeRow, location);

      try {
        Files.write(location.toPath(), xml.getBytes("UTF-8"));
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  private void updateOffHeapGrid() {
    int nRows = offheaps.getValue().intValue() + 1;
    // removes rows
    for (int r = offheapGrid.getRows(); r > nRows; r--) {
      offheapGrid.removeRow(r - 1);
    }
    // set new row limit
    offheapGrid.setRows(nRows);
    // add new rows
    for (int r = 1; r < nRows; r++) {
      if (offheapGrid.getComponent(0, r) == null) {
        TextField name = new TextField();
        name.setPlaceholder("Name");
        name.setValue("offheap-" + r);
        TextField memory = new TextField();
        memory.setPlaceholder("Size (MB)");
        memory.setValue("256");
        offheapGrid.addComponent(name, 0, r);
        offheapGrid.addComponent(memory, 1, r);
      }
    }
  }

  private void updateDataRootGrid() {
    int header = getDataRootFirstRow();
    int nRows = dataRoots.getValue().intValue() + header;
    // removes rows
    for (int r = dataRootGrid.getRows(); r > nRows; r--) {
      dataRootGrid.removeRow(r - 1);
    }
    // set new row limit
    dataRootGrid.setRows(nRows);
    // add new rows
    for (int r = header; r < nRows; r++) {
      if (dataRootGrid.getComponent(0, r) == null) {
        TextField id = new TextField();
        id.setPlaceholder("ID");
        id.setValue("dataroot-" + (r - header + 1));
        TextField path = new TextField();
        path.setPlaceholder("Location");
        path.setValue(new File(HOME, "terracotta/cluster/data/dataroot-" + (r - header + 1)).getAbsolutePath());
        path.setWidth(100, Unit.PERCENTAGE);
        dataRootGrid.addComponent(id, 0, r, 1, r);
        dataRootGrid.addComponent(path, DATAROOT_PATH_COLUMN, r);
      }
    }
  }

  private void platformBackupWanted(boolean wanted) {
    int row = getBackupRow();
    if (wanted) {
      dataRootGrid.insertRow(row);
      TextField id = new TextField();
      id.setPlaceholder("ID");
      id.setValue("BACKUP");
      id.setReadOnly(true);
      TextField path = new TextField();
      path.setPlaceholder("Location");
      path.setValue(new File(HOME, "terracotta/cluster/data/backup").getAbsolutePath());
      path.setWidth(100, Unit.PERCENTAGE);
      dataRootGrid.addComponent(id, 0, row, 1, row);
      dataRootGrid.addComponent(path, DATAROOT_PATH_COLUMN, row);
    } else {
      dataRootGrid.removeRow(row);
    }
  }

  private void platformPersistenceWanted(boolean wanted) {
    int row = getPersistenceRow();
    if (wanted) {
      dataRootGrid.insertRow(row);
      TextField id = new TextField();
      id.setPlaceholder("ID");
      id.setValue("PLATFORM");
      id.setReadOnly(true);
      TextField path = new TextField();
      path.setPlaceholder("Location");
      path.setValue(new File(HOME, "terracotta/cluster/data/platform").getAbsolutePath());
      path.setWidth(100, Unit.PERCENTAGE);
      dataRootGrid.addComponent(id, 0, row, 1, row);
      dataRootGrid.addComponent(path, DATAROOT_PATH_COLUMN, row);
    } else {
      dataRootGrid.removeRow(row);
    }
  }

  private void updateServerGrid() {
    if (kitAwareClassLoaderDelegator.getKitPath() != null) {
      int nRows = stripes.getValue().intValue() + 1;
      int nCols = servers.getValue().intValue() + 1;
      // removes rows and columns
      for (int r = serverGrid.getRows(); r > nRows; r--) {
        serverGrid.removeRow(r - 1);
      }
      for (int r = 1; r < nRows; r++) {
        for (int c = serverGrid.getColumns(); c > nCols; c--) {
          serverGrid.removeComponent(c - 1, r);
        }
      }
      // set limits
      serverGrid.setRows(nRows);
      serverGrid.setColumns(Math.max(nCols, MIN_SERVER_GRID_COLS));
      // add new rows and cols
      for (int r = 1; r < nRows; r++) {
        for (int c = 0; c < nCols; c++) {
          if (serverGrid.getComponent(c, r) == null) {
            if (c == 0) {
              FormLayout form = new FormLayout();
              form.addComponents(
                  new Label("Server Name"),
                  new Label("Logs location"),
                  new Label("Client port"),
                  new Label("Group port"));
              serverGrid.addComponent(form, c, r);
            } else {
              FormLayout form = new FormLayout();
              TextField name = new TextField();
              name.setPlaceholder("Name");
              name.setValue("stripe-" + r + "-server-" + c);
              name.addValueChangeListener(event -> updateServerControls());
              TextField logs = new TextField();
              logs.setPlaceholder("Location");
              logs.setValue(new File(HOME, "terracotta/cluster/logs/" + name.getValue()).getAbsolutePath());
              TextField clientPort = new TextField();
              clientPort.setPlaceholder("Client port");
              clientPort.setValue("" + (9410 + (r - 1) * 10 + (c - 1)));
              TextField groupPort = new TextField();
              groupPort.setPlaceholder("Group port");
              groupPort.setValue("" + (9430 + (r - 1) * 10 + (c - 1)));
              form.addComponents(name, logs, clientPort, groupPort);
              serverGrid.addComponent(form, c, r);
            }
          }
        }
      }

      updateServerControls();
    }
  }

  private void addCacheControls() {

    List<String> cacheNames = new ArrayList<>(cacheManagerBusiness.retrieveCacheNames());
    cacheControls = new VerticalLayout();
    VerticalLayout cacheList = new VerticalLayout();
    HorizontalLayout cacheCreation = new HorizontalLayout();
    cacheControls.addComponentsAndExpand(cacheList, cacheCreation);

    TextField cacheNameField = new TextField();
    cacheNameField.setPlaceholder("a cache name");
    cacheNameField.addStyleName("align-bottom");
    cacheCreation.addComponent(cacheNameField);

    List<Long> onHeapValues = Arrays.asList(0L, 1L, 10L, 100L, 1000L);
    ComboBox<Long> onHeapSizeComboBox = new ComboBox<>("OnHeap size", onHeapValues);
    onHeapSizeComboBox.setEmptySelectionAllowed(false);
    onHeapSizeComboBox.setValue(onHeapValues.get(3));
    cacheCreation.addComponent(onHeapSizeComboBox);

    List<String> onHeapUnitValues = Arrays.asList("ENTRIES", "KB", "MB", "GB");
    ComboBox<String> onHeapUnitComboBox = new ComboBox<>("OnHeap unit", onHeapUnitValues);
    onHeapUnitComboBox.setValue(onHeapUnitValues.get(0));
    cacheCreation.addComponent(onHeapUnitComboBox);

    List<Long> offHeapValues = Arrays.asList(0L, 1L, 10L, 100L, 1000L);
    ComboBox<Long> offHeapSizeComboBox = new ComboBox<>("Offheap size", offHeapValues);
    offHeapSizeComboBox.setEmptySelectionAllowed(false);
    offHeapSizeComboBox.setValue(offHeapValues.get(1));
    cacheCreation.addComponent(offHeapSizeComboBox);

    List<String> offHeapUnitValues = Arrays.asList("KB", "MB", "GB");
    ComboBox<String> offHeapUnitComboBox = new ComboBox<>("OffHeap unit", offHeapUnitValues);
    offHeapUnitComboBox.setValue(offHeapUnitValues.get(1));
    cacheCreation.addComponent(offHeapUnitComboBox);


    List<Long> diskValues = Arrays.asList(0L, 1L, 10L, 100L, 1000L);
    ComboBox<Long> diskSizeComboBox = new ComboBox<>("Disk size", diskValues);
    diskSizeComboBox.setEmptySelectionAllowed(false);
    diskSizeComboBox.setValue(diskValues.get(0));
    cacheCreation.addComponent(diskSizeComboBox);

    List<String> diskUnitValues = Arrays.asList("KB", "MB", "GB");
    ComboBox<String> diskUnitComboBox = new ComboBox<>("Disk unit", diskUnitValues);
    diskUnitComboBox.setValue(diskUnitValues.get(1));
    cacheCreation.addComponent(diskUnitComboBox);

    List<String> clusteredValues = Arrays.asList("NONE", "shared", "dedicated 10MB", "dedicated 100MB");
    ComboBox<String> clusteredComboBox = new ComboBox<>("Clustered tier", clusteredValues);
    clusteredComboBox.setEmptySelectionAllowed(false);
    clusteredComboBox.setValue(clusteredValues.get(2));
    cacheCreation.addComponent(clusteredComboBox);


    Button addCacheButton = new Button("Add cache");
    addCacheButton.addStyleName("align-bottom");

    cacheCreation.addComponent(addCacheButton);
    ListDataProvider<String> listDataProvider = new ListDataProvider<>(cacheNames);
    addCacheButton.addClickListener(clickEvent -> {
      try {

        String clusteredComboBoxValue = clusteredComboBox.getValue();
        CacheConfiguration.ClusterTierType clusterTierType = CacheConfiguration.ClusterTierType.NONE;
        int clusteredDedicatedSize = 0;
        String clusteredDedicatedUnit = "MB";
        String clusteredSharedPoolName = null;
        if (clusteredComboBoxValue.equals("shared")) {
          clusterTierType = CacheConfiguration.ClusterTierType.SHARED;
          clusteredSharedPoolName = "resource-pool-a";
        } else if (clusteredComboBoxValue.contains("dedicated")) {
          clusterTierType = CacheConfiguration.ClusterTierType.DEDICATED;
          String fullsize = clusteredComboBoxValue.split(" ")[1];
          clusteredDedicatedSize = Integer.valueOf(fullsize.split("MB")[0]);
        }

        CacheConfiguration cacheConfiguration = new CacheConfiguration(
            onHeapSizeComboBox.getValue(),
            onHeapUnitComboBox.getValue(),
            offHeapSizeComboBox.getValue(),
            offHeapUnitComboBox.getValue(),
            diskSizeComboBox.getValue(),
            diskUnitComboBox.getValue(), clusteredDedicatedSize, clusteredDedicatedUnit, clusteredSharedPoolName, clusterTierType);

        cacheManagerBusiness.createCache(cacheNameField.getValue(), cacheConfiguration);
        cacheNames.add(cacheNameField.getValue());
        refreshCacheStuff(listDataProvider);
        cacheNameField.clear();
        Notification notification = new Notification("Cache added with success !",
            Notification.Type.TRAY_NOTIFICATION);
        notification.setStyleName("warning");
        notification.show(Page.getCurrent());
      } catch (RuntimeException e) {
        displayErrorNotification("Cache could not be added !", ExceptionUtils.getRootCauseMessage(e));
      }
    });

    for (String cacheName : cacheNames) {
      HorizontalLayout cacheInfo = new HorizontalLayout();
      Label cacheNameLabel = new Label(cacheName);

      Slider poundingSlider = new Slider();
      poundingSlider.setCaption("NOT POUNDING");
      poundingSlider.addStyleName("pounding-slider");
      if (cacheManagerBusiness.retrievePoundingIntensity(cacheName) > 0) {
        poundingSlider.setValue((double) cacheManagerBusiness.retrievePoundingIntensity(cacheName));
        updatePoundingCaption(poundingSlider, cacheManagerBusiness.retrievePoundingIntensity(cacheName));
      }
      poundingSlider.setMin(0);
      poundingSlider.setMax(11);
      poundingSlider.addValueChangeListener(event -> {
        int poundingIntensity = event.getValue().intValue();
        cacheManagerBusiness.updatePoundingIntensity(cacheName, poundingIntensity);
        updatePoundingCaption(poundingSlider, poundingIntensity);
      });


      Button removeCacheButton = new Button("Remove cache");
      removeCacheButton.addClickListener(event -> {
        try {
          cacheManagerBusiness.removeCache(cacheName);
          cacheNames.remove(cacheName);
          refreshCacheStuff(listDataProvider);
          Notification notification = new Notification("Cache removed with success !",
              Notification.Type.TRAY_NOTIFICATION);
          notification.setStyleName("warning");
          notification.show(Page.getCurrent());
        } catch (RuntimeException e) {
          displayErrorNotification("Cache could not be removed !", ExceptionUtils.getRootCauseMessage(e));
          refreshCacheStuff(listDataProvider);
        }
      });

      Button destroyCacheButton = new Button("Destroy cache");
      destroyCacheButton.addClickListener(event -> {
        try {
          cacheManagerBusiness.destroyCache(cacheName);
          cacheNames.remove(cacheName);
          refreshCacheStuff(listDataProvider);
          Notification notification = new Notification("Cache destroyed with success !",
              Notification.Type.TRAY_NOTIFICATION);
          notification.setStyleName("warning");
          notification.show(Page.getCurrent());
        } catch (Exception e) {
          displayErrorNotification("Cache could not be destroyed !", ExceptionUtils.getRootCauseMessage(e));
          refreshCacheStuff(listDataProvider);
        }
      });
      cacheInfo.addComponentsAndExpand(cacheNameLabel, poundingSlider, removeCacheButton, destroyCacheButton);
      cacheList.addComponent(cacheInfo);
    }


    cacheLayout.addComponent(cacheControls);

  }

  private void updatePoundingCaption(Slider poundingSlider, int poundingIntensity) {
    if (poundingIntensity == 0) {
      poundingSlider.setCaption("NOT POUNDING");
    } else if (poundingIntensity > 0 && poundingIntensity < 11) {
      poundingSlider.setCaption("POUNDING");
    } else {
      poundingSlider.setCaption("POUNDING HARD");
    }
  }

  private void displayErrorNotification(String caption, String message) {
    Notification notification = new Notification(caption,
        message,
        Notification.Type.TRAY_NOTIFICATION);
    notification.setStyleName("error");
    notification.show(Page.getCurrent());
  }

  private void setupLayout() {
    mainLayout = new TabSheet();
    setContent(mainLayout);
  }

  private void addCacheManagerControls() {

    cacheManagerControls = new VerticalLayout();
    HorizontalLayout currentCacheManagerControls = new HorizontalLayout();
    HorizontalLayout createCacheManagerClusteredControls = new HorizontalLayout();
    HorizontalLayout createCacheManagerDiskControls = new HorizontalLayout();
    HorizontalLayout createCacheManagerInitializeControls = new HorizontalLayout();


    TextArea cacheManagerConfigTextArea = new TextArea();
    cacheManagerConfigTextArea.setValue(cacheManagerBusiness.isCacheManagerAlive() ? cacheManagerBusiness.retrieveHumanReadableConfiguration() : "CacheManager configuration will be displayed here");
    cacheManagerConfigTextArea.setWidth(400, Unit.PERCENTAGE);
    cacheManagerConfigTextArea.setRows(15);
    CacheManagerConfigurationPopup cacheManagerConfigurationPopup = new CacheManagerConfigurationPopup(cacheManagerConfigTextArea);
    PopupView popupView = new PopupView(cacheManagerConfigurationPopup);
    popupView.setWidth("600px");
    popupView.setSizeFull();


    Label statusLabel = new Label();
    statusLabel.setValue(cacheManagerBusiness.getStatus());


    TextField terracottaUrlField = new TextField();
    terracottaUrlField.setValue("localhost:9410");
    terracottaUrlField.setCaption("Terracotta host:port");

    terracottaUrlField.addValueChangeListener(valueChangeEvent -> {
      boolean nowSeemsAvailable = TerracottaServerBusiness.seemsAvailable(valueChangeEvent.getValue());
      terracottaUrlField.setCaption("Terracotta host:port" + (nowSeemsAvailable ? " OPEN" : " CLOSED"));
    });

    TextField clusterTierManagerNameField = new TextField();
    clusterTierManagerNameField.setCaption("ClusterTierManager name");
    clusterTierManagerNameField.setValue("TinyPounderCM");

    TextField offHeapPersistenceLocationField = new TextField();
    CheckBox offHeapCheckBox = new CheckBox("offheap", true);
    offHeapCheckBox.addStyleName("shift-bottom-right-offheap");
    offHeapCheckBox.setEnabled(false);
    offHeapPersistenceLocationField.setCaption("offheap resource name");
    offHeapPersistenceLocationField.setValue("offheap-1");


    TextField serverDiskPersistenceLocationField = new TextField();
    CheckBox serverDiskCheckBox = new CheckBox("disk", true);
    serverDiskCheckBox.addStyleName("shift-bottom-right-disk");
    serverDiskCheckBox.setEnabled(false);
    serverDiskPersistenceLocationField.setCaption("disk resource name");
    serverDiskPersistenceLocationField.setValue("dataroot-1");


    CheckBox clusteredCheckBox = new CheckBox("Clustered", true);
    clusteredCheckBox.addStyleName("align-bottom");
    clusteredCheckBox.addValueChangeListener(valueChangeEvent -> {
      if (valueChangeEvent.getValue()) {
        terracottaUrlField.setEnabled(true);
        clusterTierManagerNameField.setEnabled(true);
        offHeapPersistenceLocationField.setEnabled(true);
        serverDiskPersistenceLocationField.setEnabled(true);
      } else {
        terracottaUrlField.setEnabled(false);
        clusterTierManagerNameField.setEnabled(false);
        offHeapPersistenceLocationField.setEnabled(false);
        serverDiskPersistenceLocationField.setEnabled(false);
      }
    });


    TextField diskPersistenceLocationField = new TextField();
    diskPersistenceLocationField.setCaption("Local disk folder");
    diskPersistenceLocationField.setValue("tinyPounderDiskPersistence");

    CheckBox diskCheckBox = new CheckBox("Local disk", true);
    diskCheckBox.addStyleName("align-bottom");
    diskCheckBox.addValueChangeListener(valueChangeEvent -> {
      if (valueChangeEvent.getValue()) {
        diskPersistenceLocationField.setEnabled(true);
      } else {
        diskPersistenceLocationField.setEnabled(false);
      }
    });

    Button createCacheManagerButton = new Button("Initialize CacheManager");
    createCacheManagerButton.addStyleName("align-bottom");

    createCacheManagerButton.addClickListener(event -> {
      try {
        cacheManagerBusiness.initializeCacheManager(!clusteredCheckBox.getValue() ? null : terracottaUrlField.getValue(), clusterTierManagerNameField.getValue(), !diskCheckBox.getValue() ? null : diskPersistenceLocationField.getValue(), offHeapPersistenceLocationField.getValue(), serverDiskPersistenceLocationField.getValue());
        cacheManagerConfigTextArea.setValue(cacheManagerBusiness.retrieveHumanReadableConfiguration());
        refreshCacheManagerControls();
      } catch (Exception e) {
        displayErrorNotification("CacheManager could not be initialized!", ExceptionUtils.getRootCauseMessage(e));
      }
    });

    Button closeCacheManager = new Button("Close CacheManager");
    closeCacheManager.addClickListener(event -> {
      try {
        cacheManagerBusiness.close();
        refreshCacheControls();
        refreshCacheManagerControls();
      } catch (Exception e) {
        displayErrorNotification("CacheManager could not be closed!", ExceptionUtils.getRootCauseMessage(e));
      }
    });
    Button destroyCacheManager = new Button("Destroy CacheManager");
    destroyCacheManager.addClickListener(event -> {
      try {
        cacheManagerBusiness.destroy();
        refreshCacheControls();
        refreshCacheManagerControls();
        Notification notification = new Notification("CacheManager destroyed with success !",
            Notification.Type.TRAY_NOTIFICATION);
        notification.setStyleName("warning");
        notification.show(Page.getCurrent());
      } catch (Exception e) {
        displayErrorNotification("CacheManager could not be destroyed!", ExceptionUtils.getRootCauseMessage(e));
      }
    });

    if (cacheManagerBusiness.getStatus().equals("UNINITIALIZED") || cacheManagerBusiness.getStatus().equals("NO CACHE MANAGER")) {
      closeCacheManager.setEnabled(false);
    } else {
      createCacheManagerButton.setEnabled(false);
    }

    if (cacheManagerBusiness.getStatus().equals("NO CACHE MANAGER")) {
      destroyCacheManager.setEnabled(false);
    }

    currentCacheManagerControls.addComponentsAndExpand(statusLabel, popupView, closeCacheManager, destroyCacheManager);
    createCacheManagerClusteredControls.addComponentsAndExpand(clusteredCheckBox, terracottaUrlField, clusterTierManagerNameField, offHeapCheckBox, offHeapPersistenceLocationField);
    if (kitAwareClassLoaderDelegator.isEEKit()) {
      createCacheManagerClusteredControls.addComponentsAndExpand(serverDiskCheckBox, serverDiskPersistenceLocationField);
    }
    createCacheManagerClusteredControls.addComponentsAndExpand(createCacheManagerButton);
    createCacheManagerDiskControls.addComponentsAndExpand(diskCheckBox, diskPersistenceLocationField);
    createCacheManagerInitializeControls.addComponentsAndExpand(createCacheManagerButton);
    cacheManagerControls.addComponentsAndExpand(currentCacheManagerControls);
    cacheLayout.addComponent(cacheManagerControls);

    if (cacheManagerBusiness.isCacheManagerAlive()) {
      addCacheControls();
    } else {
      cacheManagerControls.addComponentsAndExpand(createCacheManagerClusteredControls, createCacheManagerDiskControls, createCacheManagerInitializeControls);
    }

  }

  private void addKitControls() {

    kitControlsLayout = new VerticalLayout();
    kitPathLayout = new GridLayout(2, 1);
    kitPathLayout.setWidth(100, Unit.PERCENTAGE);
    kitPathLayout.setColumnExpandRatio(0, 2);

    Label info = new Label();
    if (kitAwareClassLoaderDelegator.getKitPath() != null) {
      info.setValue("Using " + (kitAwareClassLoaderDelegator.isEEKit() ? "Enterprise Kit" : "Open source Kit"));
    } else {
      info.setValue("Enter Kit location:");
    }
    TextField kitPath = new TextField();
    kitPath.setPlaceholder("Kit location");
    kitPath.setWidth("100%");
    kitPath.setValue(kitAwareClassLoaderDelegator.getKitPath() != null ? kitAwareClassLoaderDelegator.getKitPath() : "");
    kitPathBT = new Button("Update kit path");
    kitPathBT.setEnabled(false);
    kitPath.addValueChangeListener(event -> kitPathBT.setEnabled(true));
    kitPathLayout.addComponent(kitPath);
    kitPathLayout.addComponent(kitPathBT);

    kitPathBT.addClickListener(event -> {
      try {
        kitAwareClassLoaderDelegator.setKitPathAndUpdate(kitPath.getValue());
        info.setValue("Using " + (kitAwareClassLoaderDelegator.isEEKit() ? "Enterprise" : "Open source") + " Kit");
        if (voltronConfigLayout != null) {
          voltronConfigLayout.removeAllComponents();
        }
        if (voltronControlLayout != null) {
          voltronControlLayout.removeAllComponents();
        }
        updateKitControls();
        initVoltronConfigLayout();
        initVoltronControlLayout();
        initRuntimeLayout();
        updateServerGrid();
      } catch (Exception e) {
        if (e instanceof NoSuchFileException) {
          displayErrorNotification("Kit path could not update !", "Make sure the path points to a kit !");
        } else {
          displayErrorNotification("Kit path could not update !", ExceptionUtils.getRootCauseMessage(e));
        }

      }
    });

    kitControlsLayout.addComponent(info);
    kitControlsLayout.addComponent(kitPathLayout);

    Button exitBT = new Button("Close TinyPounder");
    exitBT.addClickListener(event -> new Thread(() -> {
      runningServers.values().forEach(RunningServer::stop);
      consoleRefresher.cancel(true);
      SpringApplication.exit(appContext);
    }).start());
    kitControlsLayout.addComponent(exitBT);

    mainLayout.addTab(kitControlsLayout, "STEP 1: KIT");
  }

  private void addDatasetManagerControls() {

    datasetManagerControls = new VerticalLayout();
    HorizontalLayout currentDatasetControls = new HorizontalLayout();
    HorizontalLayout createDatasetClusteredControls = new HorizontalLayout();
    HorizontalLayout createDatasetIndexesControls = new HorizontalLayout();
    HorizontalLayout createDatasetInitializeControls = new HorizontalLayout();

    Label statusLabel = new Label();
    statusLabel.setValue(datasetManagerBusiness.getStatus());


    TextField terracottaUrlField = new TextField();
    terracottaUrlField.setValue("localhost:9410");
    terracottaUrlField.setCaption("Terracotta host:port");

    terracottaUrlField.addValueChangeListener(valueChangeEvent -> {
      boolean nowSeemsAvailable = TerracottaServerBusiness.seemsAvailable(valueChangeEvent.getValue());
      terracottaUrlField.setCaption("Terracotta host:port" + (nowSeemsAvailable ? " OPEN" : " CLOSED"));
    });

    CheckBox clusteredCheckBox = new CheckBox("Clustered", true);
    clusteredCheckBox.setEnabled(false);
    clusteredCheckBox.addStyleName("align-bottom");

    Button initializeDatasetManager = new Button("Initialize DatasetManager");
    initializeDatasetManager.addStyleName("align-bottom");

    initializeDatasetManager.addClickListener(event -> {
      try {
        datasetManagerBusiness.initializeDatasetManager(!clusteredCheckBox.getValue() ? null : terracottaUrlField.getValue());
        refreshDatasetManagerControls();
      } catch (Exception e) {
        displayErrorNotification("DatasetManager could not be initialized!", ExceptionUtils.getRootCauseMessage(e));
      }
    });

    Button closeDatasetManager = new Button("Close DatasetManager");
    closeDatasetManager.addClickListener(event -> {
      try {
        datasetManagerBusiness.close();
        refreshDatasetControls();
        refreshDatasetManagerControls();
      } catch (Exception e) {
        displayErrorNotification("DatasetManager could not be closed!", ExceptionUtils.getRootCauseMessage(e));
      }
    });

    if (datasetManagerBusiness.getStatus().equals("CLOSED") || datasetManagerBusiness.getStatus().equals("NO DATASET MANAGER")) {
      closeDatasetManager.setEnabled(false);
    } else {
      initializeDatasetManager.setEnabled(false);
    }


    currentDatasetControls.addComponentsAndExpand(statusLabel, closeDatasetManager);
    createDatasetClusteredControls.addComponentsAndExpand(clusteredCheckBox, terracottaUrlField, initializeDatasetManager);
    createDatasetInitializeControls.addComponentsAndExpand(initializeDatasetManager);
    datasetManagerControls.addComponentsAndExpand(currentDatasetControls);
    datasetLayout.addComponent(datasetManagerControls);

    if (datasetManagerBusiness.isDatasetManagerAlive()) {
      addDatasetControls();
    } else {
      datasetManagerControls.addComponentsAndExpand(createDatasetClusteredControls, createDatasetIndexesControls, createDatasetInitializeControls);
    }

  }

  private void addDatasetControls() {
    List<String> datasetNames = new ArrayList<>(datasetManagerBusiness.retrieveDatasetNames());
    datasetControls = new VerticalLayout();
    VerticalLayout datasetListLayout = new VerticalLayout();
    HorizontalLayout datasetCreation = new HorizontalLayout();
    datasetControls.addComponentsAndExpand(datasetListLayout, datasetCreation);

    TextField datasetNameField = new TextField();
    datasetNameField.setPlaceholder("dataset name");
    datasetNameField.addStyleName("align-bottom");

    TextField offHeapPersistenceLocationField = new TextField();
    CheckBox offHeapCheckBox = new CheckBox("offheap", true);
    offHeapCheckBox.addStyleName("shift-bottom-right-offheap");
    offHeapCheckBox.addValueChangeListener(valueChangeEvent -> {
      if (valueChangeEvent.getValue()) {
        offHeapPersistenceLocationField.setEnabled(true);
      } else {
        offHeapPersistenceLocationField.setEnabled(false);
      }
    });
    offHeapPersistenceLocationField.setCaption("offheap resource name");
    offHeapPersistenceLocationField.setValue("offheap-1");


    TextField diskPersistenceLocationField = new TextField();
    CheckBox diskCheckBox = new CheckBox("disk", true);
    diskCheckBox.addStyleName("shift-bottom-right-disk");
    diskCheckBox.addValueChangeListener(valueChangeEvent -> {
      if (valueChangeEvent.getValue()) {
        diskPersistenceLocationField.setEnabled(true);
      } else {
        diskPersistenceLocationField.setEnabled(false);
      }
    });
    diskPersistenceLocationField.setCaption("disk resource name");
    diskPersistenceLocationField.setValue("dataroot-1");

    CheckBox indexCheckBox = new CheckBox("use index", true);
    indexCheckBox.addStyleName("shift-bottom-right-index");

    Button addDatasetButton = new Button("Add dataset");
    addDatasetButton.addStyleName("align-bottom");

    datasetCreation.addComponentsAndExpand(datasetNameField, offHeapCheckBox, offHeapPersistenceLocationField, diskCheckBox, diskPersistenceLocationField, indexCheckBox, addDatasetButton);
    ListDataProvider<String> listDataProvider = new ListDataProvider<>(datasetNames);
    addDatasetButton.addClickListener(clickEvent -> {
      try {
        DatasetConfiguration datasetConfiguration = new DatasetConfiguration(offHeapCheckBox.getValue() ? offHeapPersistenceLocationField.getValue() : null, diskCheckBox.getValue() ? diskPersistenceLocationField.getValue() : null, indexCheckBox.getValue());
        datasetManagerBusiness.createDataset(datasetNameField.getValue(), datasetConfiguration);
        datasetNames.add(datasetNameField.getValue());
        refreshDatasetStuff(listDataProvider);
        datasetNameField.clear();
        Notification notification = new Notification("Dataset added with success !",
            Notification.Type.TRAY_NOTIFICATION);
        notification.setStyleName("warning");
        notification.show(Page.getCurrent());
      } catch (RuntimeException e) {
        displayErrorNotification("Dataset could not be added !", ExceptionUtils.getRootCauseMessage(e));
      }
    });

    for (String datasetName : datasetNames) {
      HorizontalLayout datasetInfoLabel = new HorizontalLayout();
      Label datasetNameLabel = new Label(datasetName);

      Button addDatasetInstanceButton = new Button("Add dataset instance");
      addDatasetInstanceButton.addClickListener(event -> {
        try {
          String datasetInstanceName = datasetManagerBusiness.createDatasetInstance(datasetName);
          refreshDatasetStuff(listDataProvider);
          Notification notification = new Notification("Dataset instance " + datasetInstanceName + " created  with success !",
              Notification.Type.TRAY_NOTIFICATION);
          notification.setStyleName("warning");
          notification.show(Page.getCurrent());
        } catch (Exception e) {
          displayErrorNotification("Dataset instance could not be created !", ExceptionUtils.getRootCauseMessage(e));
          refreshDatasetStuff(listDataProvider);
        }
      });

      Button destroyDatasetButton = new Button("Destroy dataset");
      destroyDatasetButton.addClickListener(event -> {
        try {
          datasetManagerBusiness.destroyDataset(datasetName);
          datasetNames.remove(datasetName);
          refreshDatasetStuff(listDataProvider);
          Notification notification = new Notification("Dataset destroyed with success !",
              Notification.Type.TRAY_NOTIFICATION);
          notification.setStyleName("warning");
          notification.show(Page.getCurrent());
        } catch (Exception e) {
          displayErrorNotification("Dataset could not be destroyed !", ExceptionUtils.getRootCauseMessage(e));
          refreshDatasetStuff(listDataProvider);
        }
      });


      datasetInfoLabel.addComponentsAndExpand(datasetNameLabel, addDatasetInstanceButton, destroyDatasetButton);
      datasetListLayout.addComponent(datasetInfoLabel);

      Set<String> datasetInstanceNames = datasetManagerBusiness.getDatasetInstanceNames(datasetName);
      if (datasetInstanceNames.size() > 0) {
        destroyDatasetButton.setEnabled(false);
      }
      for (String instanceName : datasetInstanceNames) {
        HorizontalLayout datasetInstanceInfoLayout = new HorizontalLayout();
        Label datasetInstanceNameLabel = new Label(instanceName);
        Button closeDatasetButton = new Button("Close dataset instance");
        closeDatasetButton.addClickListener(event -> {
          try {
            datasetManagerBusiness.closeDatasetInstance(datasetName, instanceName);
            refreshDatasetStuff(listDataProvider);
            Notification notification = new Notification("Dataset instance closed with success !",
                Notification.Type.TRAY_NOTIFICATION);
            notification.setStyleName("warning");
            notification.show(Page.getCurrent());
          } catch (Exception e) {
            displayErrorNotification("Dataset instance could not be closed !", ExceptionUtils.getRootCauseMessage(e));
            refreshDatasetStuff(listDataProvider);
          }
        });

        Slider poundingSlider = new Slider();
        poundingSlider.setCaption("NOT POUNDING");
        poundingSlider.addStyleName("pounding-slider");
        if (datasetManagerBusiness.retrievePoundingIntensity(instanceName) > 0) {
          poundingSlider.setValue((double) datasetManagerBusiness.retrievePoundingIntensity(instanceName));
          updatePoundingCaption(poundingSlider, datasetManagerBusiness.retrievePoundingIntensity(instanceName));
        }
        poundingSlider.setMin(0);
        poundingSlider.setMax(11);
        poundingSlider.addValueChangeListener(event -> {
          int poundingIntensity = event.getValue().intValue();
          datasetManagerBusiness.updatePoundingIntensity(instanceName, poundingIntensity);
          updatePoundingCaption(poundingSlider, poundingIntensity);
        });


        datasetInstanceInfoLayout.addComponentsAndExpand(datasetInstanceNameLabel, poundingSlider, closeDatasetButton);
        datasetListLayout.addComponent(datasetInstanceInfoLayout);
      }


    }
    datasetLayout.addComponent(datasetControls);
  }

  private void refreshDatasetControls() {

  }

  private void refreshDatasetManagerControls() {
    if (kitAwareClassLoaderDelegator.containsTerracottaStore()) {
      if (datasetManagerControls != null) {
        datasetLayout.removeComponent(datasetManagerControls);
        if (datasetControls != null) {
          datasetLayout.removeComponent(datasetControls);
        }

      }
      addDatasetManagerControls();
    }
  }

  private void refreshCacheControls() {
    if (cacheControls != null) {
      cacheLayout.removeComponent(cacheControls);
    }
    if (cacheManagerBusiness.isCacheManagerAlive()) {
      addCacheControls();
    }

  }

  private void refreshCacheManagerControls() {
    if (kitAwareClassLoaderDelegator.containsEhcache()) {
      if (cacheManagerControls != null) {
        cacheLayout.removeComponent(cacheManagerControls);
        if (cacheControls != null) {
          cacheLayout.removeComponent(cacheControls);
        }

      }
      addCacheManagerControls();
    }
  }

  private void refreshCacheStuff(ListDataProvider<String> listDataProvider) {
    listDataProvider.refreshAll();
    refreshCacheManagerControls();
  }

  private void refreshDatasetStuff(ListDataProvider<String> listDataProvider) {
    listDataProvider.refreshAll();
    refreshDatasetManagerControls();
  }

  private int getBackupRow() {
    return 1;
  }

  private int getPersistenceRow() {
    return platformBackup.getValue() ? 2 : 1;
  }

  private int getDataRootFirstRow() {
    int header = 1;
    if (platformPersistence.getValue()) header++;
    if (platformBackup.getValue()) header++;
    return header;
  }

  // Create a dynamically updating content for the popup
  private static class CacheManagerConfigurationPopup implements PopupView.Content {
    private final TextArea textArea;

    public CacheManagerConfigurationPopup(TextArea textArea) {
      this.textArea = textArea;
    }

    @Override
    public final Component getPopupComponent() {
      return textArea;
    }

    @Override
    public final String getMinimizedValueAsHTML() {
      return "CacheManager full configuration";
    }
  }


}
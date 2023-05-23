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
import com.vaadin.annotations.Theme;
import com.vaadin.data.HasValue;
import com.vaadin.data.provider.ListDataProvider;
import com.vaadin.server.Page;
import com.vaadin.server.VaadinRequest;
import com.vaadin.server.VaadinSession;
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
import com.vaadin.ui.RadioButtonGroup;
import com.vaadin.ui.Slider;
import com.vaadin.ui.TabSheet;
import com.vaadin.ui.TextArea;
import com.vaadin.ui.TextField;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.themes.ValoTheme;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The UI class
 */
@SuppressWarnings("ALL")
@Theme("tinypounder")
@Push
@SpringUI
@PreserveOnRefresh
public class TinyPounderMainUI extends UI {

  private static final int MIN_SERVER_GRID_COLS = 3;
  private static final int DATAROOT_PATH_COLUMN = 2;
  private static final File HOME = new File(System.getProperty("user.home"));
  private static final String VERSION = getVersion();
  static final String AVAILABILITY = "availability";
  static final String CONSISTENCY = "consistency";
  private static final String SECURITY = "Security";

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

  @Autowired
  private Settings settings;

  private TabSheet mainLayout;
  private VerticalLayout cacheLayout;
  private VerticalLayout datasetLayout;
  private VerticalLayout clientSecurityLayout;

  private VerticalLayout cacheControls;
  private VerticalLayout datasetControls;
  private VerticalLayout cacheManagerControls;
  private VerticalLayout datasetManagerControls;
  private HorizontalLayout clientSecurityControls;
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
  private GridLayout consistencyGrid;
  private HorizontalLayout serverSecurityGrid;
  private Slider dataRoots;
  private CheckBox platformPersistence;
  private CheckBox platformBackup;
  private TextArea configTextArea;
  private GridLayout kitPathLayout;
  private TextField clusterNameTF;
  private Map<String, File> tcConfigLocationPerStripe = new ConcurrentHashMap<>();
  private GridLayout serverControls;
  private TabSheet consoles;
  private TextArea mainConsole;
  private Map<String, RunningServer> runningServers = new ConcurrentHashMap<>();
  private VerticalLayout kitControlsLayout;
  private ScheduledFuture<?> consoleRefresher;
  private Button generateTcConfig;
  private TextField baseLocation;
  private Button trashDataButton;
  private RadioButtonGroup<String> consistencyGroup;
  private CheckBox serverSecurityCheckBox;
  private TextField votersCountTextField;
  private TextField serverSecurityField;
  private CheckBox clientSecurityCheckBox;
  private TextField clientSecurityField;

  @Override
  protected void init(VaadinRequest vaadinRequest) {
    VaadinSession.getCurrent().getSession().setMaxInactiveInterval(-1);
    Page.getCurrent().setTitle("Tiny Pounder (" + VERSION + ")");

    setupLayout();
    addKitControls();
    updateKitControls();
    initVoltronConfigLayout();
    initVoltronControlLayout();
    initRuntimeLayout();
    addExitCloseTab();
    updateServerGrid();

    // refresh consoles if any
    consoleRefresher = scheduledExecutorService.scheduleWithFixedDelay(
        () -> access(() -> runningServers.values().forEach(RunningServer::refreshConsole)),
        2, 2, TimeUnit.SECONDS);
  }

  private void addExitCloseTab() {
    VerticalLayout exitLayout = new VerticalLayout();
    exitLayout.addComponentsAndExpand(new Label("We hope you had fun using the TinyPounder, it is now shutdown, " +
        "as well as all the DatasetManagers, CacheManagers, and Terracotta servers you started with it"));
    TabSheet.Tab tab = mainLayout.addTab(exitLayout, "EXIT : Close TinyPounder " + VERSION);
    tab.setStyleName("tab-absolute-right");
    mainLayout.addSelectedTabChangeListener(tabEvent -> {
      if (tabEvent.getTabSheet().getSelectedTab().equals(tab.getComponent())) {
        new Thread(() -> {
          runningServers.values().forEach(RunningServer::kill);
          consoleRefresher.cancel(true);
          SpringApplication.exit(appContext);
        }).start();
      }
    });
  }

  private void updateKitControls() {
    if (kitAwareClassLoaderDelegator.isEEKit()) {
      if (kitPathLayout.getRows() == 1) {
        final TextField licensePath = new TextField();
        licensePath.setWidth(100, Unit.PERCENTAGE);
        licensePath.setValue(settings.getLicensePath().orElse(""));
        licensePath.setPlaceholder("License location");
        licensePath.addValueChangeListener(event -> {
          try {
            displayWarningNotification("License location updated with success !");
            String licensePathValue = licensePath.getValue();
            if (licensePathValue != null) {
              File file = new File(licensePathValue);
              if (!file.exists() || !file.isFile()) {
                throw new NoSuchFileException("Path does not exist on the system !");
              }
            }
            settings.setLicensePath(event.getValue());
          } catch (NoSuchFileException e) {
            displayErrorNotification("Kit path could not update !", "Make sure the path points to a valid license file !");
          }
        });
        kitPathLayout.addComponent(licensePath);
      }
    } else {
      if (kitPathLayout.getRows() == 2) {
        kitPathLayout.removeRow(1);
      }
    }
  }

  private void initRuntimeLayout() {
    if (settings.getKitPath() != null) {
      if (mainLayout.getTab(runtimeLayout) == null) {
        runtimeLayout = new VerticalLayout();
        mainLayout.addTab(runtimeLayout, "STEP 4: DATASETS & CACHES");
      }
    }
    if (kitAwareClassLoaderDelegator.containsTerracottaStore() || kitAwareClassLoaderDelegator.containsEhcache()) {
      initClientSecurityLayout();
      initCacheLayout();
      initDatasetLayout();
    }
  }

  private void initClientSecurityLayout() {
    if (kitAwareClassLoaderDelegator.isEEKit() && clientSecurityLayout == null) {
      clientSecurityLayout = new VerticalLayout();
      clientSecurityLayout.addStyleName("client-layout");
      runtimeLayout.addComponentsAndExpand(clientSecurityLayout);
      addClientSecurityControls();
    } else if (!kitAwareClassLoaderDelegator.containsTerracottaStore() && clientSecurityLayout != null) {
      // kit does no longer contain tc store libs; clear the datasetLayout
      runtimeLayout.removeComponent(datasetLayout);
      datasetLayout = null;
    }
  }

  private void initDatasetLayout() {
    if (kitAwareClassLoaderDelegator.containsTerracottaStore() && datasetLayout == null) {
      datasetLayout = new VerticalLayout();
      datasetLayout.addStyleName("client-layout");
      runtimeLayout.addComponentsAndExpand(datasetLayout);
      addDatasetManagerControls();
    } else if (!kitAwareClassLoaderDelegator.containsTerracottaStore() && datasetLayout != null) {
      // kit does no longer contain tc store libs; clear the datasetLayout
      runtimeLayout.removeComponent(datasetLayout);
      datasetLayout = null;
    }
  }

  private void initCacheLayout() {
    if (kitAwareClassLoaderDelegator.containsEhcache() && cacheLayout == null) {
      cacheLayout = new VerticalLayout();
      cacheLayout.addStyleName("client-layout");
      runtimeLayout.addComponentsAndExpand(cacheLayout);
      addCacheManagerControls();
    } else if (!kitAwareClassLoaderDelegator.containsEhcache() && cacheLayout != null) {
      // kit does no longer contain ehcache libs; clear the cacheLayout
      runtimeLayout.removeComponent(cacheLayout);
      cacheLayout = null;
    }
  }

  private void initVoltronConfigLayout() {
    if (settings.getKitPath() != null) {
      if (voltronConfigLayout == null) {
        voltronConfigLayout = new VerticalLayout();
        voltronConfigLayout.addStyleName("voltron-config-layout");
        TabSheet.Tab tab = mainLayout.addTab(voltronConfigLayout, "STEP 2: SERVER CONFIGURATIONS");
        mainLayout.addSelectedTabChangeListener(tabEvent -> {
          if (tabEvent.getTabSheet().getSelectedTab().equals(tab.getComponent())) {
            changeTrashButtonStatus(baseLocation.getValue());
          }
        });
      }
      addVoltronConfigControls();
    }
  }

  private void initVoltronControlLayout() {
    if (settings.getKitPath() != null) {
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

    HorizontalLayout row1 = new HorizontalLayout();

    Button clusterStartBtn = new Button();
    clusterStartBtn.setCaption("Start all servers");
    clusterStartBtn.setDescription("Don't use if not all servers are co-located with TinyPounder");
    clusterStartBtn.addStyleName("align-bottom");
    clusterStartBtn.addClickListener(event -> {
      for (Component child : serverControls) {
        if (child instanceof Button && "START".equals(child.getCaption()) && child.isEnabled()) {
          ((Button) child).click();
        }
      }
    });
    row1.addComponents(clusterStartBtn);

    if (kitAwareClassLoaderDelegator.isEEKit()) {
      clusterNameTF = new TextField();
      clusterNameTF.setCaption("Cluster name");
      clusterNameTF.setValue("MyCluster");

      Button clusterConfigBtn = new Button();
      Button clusterReConfigBtn = new Button();
      if (isDynamicConfig()) {
        clusterConfigBtn.addStyleName("align-bottom");
        clusterConfigBtn.setCaption("Configure");
        clusterConfigBtn.setData("activate");
        clusterConfigBtn.addClickListener((Button.ClickListener) this::executeConfigToolCommand);
      } else {
        clusterConfigBtn.addStyleName("align-bottom");
        clusterConfigBtn.setCaption("Configure");
        clusterConfigBtn.setData("configure");
        clusterConfigBtn.addClickListener((Button.ClickListener) this::executeClusterToolCommand);

        clusterReConfigBtn.addStyleName("align-bottom");
        clusterReConfigBtn.setCaption("Reconfigure");
        clusterReConfigBtn.setData("reconfigure");
        clusterReConfigBtn.addClickListener((Button.ClickListener) this::executeClusterToolCommand);
      }

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

      Button clusterStatusBtn = new Button();
      clusterStatusBtn.addStyleName("align-bottom");
      clusterStatusBtn.setCaption("Status");
      clusterStatusBtn.setData("status");
      clusterStatusBtn.addClickListener((Button.ClickListener) this::executeClusterToolCommand);

      Button clusterStopBtn = new Button();
      clusterStopBtn.addStyleName("align-bottom");
      clusterStopBtn.setCaption("Stop cluster");
      clusterStopBtn.setData("stop");
      clusterStopBtn.addClickListener((Button.ClickListener) this::executeClusterToolCommand);

      if (isDynamicConfig()) {
        row1.addComponents(clusterNameTF, clusterConfigBtn, clusterBackupBtn, clusterDumpBtn, clusterStopBtn, clusterStatusBtn);
      } else {
        row1.addComponents(clusterNameTF, clusterConfigBtn, clusterReConfigBtn, clusterBackupBtn, clusterDumpBtn, clusterStopBtn, clusterStatusBtn);
      }
    }

    voltronControlLayout.addComponentsAndExpand(row1);

    consoles = new TabSheet();
    consoles.addSelectedTabChangeListener((TabSheet.SelectedTabChangeListener) event -> {
      TextArea textArea = (TextArea) event.getTabSheet().getSelectedTab();
      textArea.setCursorPosition(textArea.getValue().length());
    });

    mainConsole = addConsole("Main", "main");

    voltronControlLayout.addComponentsAndExpand(consoles);
  }

  protected static Collection<Path> find(String searchDirectory, PathMatcher matcher) {
    try (Stream<Path> files = Files.walk(Paths.get(searchDirectory))) {
      return files.filter(matcher::matches).collect(Collectors.toList());
    } catch (IOException e) {
      throw new RuntimeException("Cannot locate cluster-tool script", e);
    }
  }

  private void executeClusterToolCommand(Button.ClickEvent event) {
    String command = (String) event.getButton().getData();
    File workDir = new File(settings.getKitPath());
    LinkedBlockingQueue<String> consoleLines = new LinkedBlockingQueue<>(); // no limit, get all the output
    PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:**cluster-tool.sh");
    Path clusterToolPath = find(workDir.getAbsolutePath(), matcher).iterator().next();
    String script = new File(clusterToolPath.getParent().toFile(), "cluster-tool." + (ProcUtils.isWindows() ? "bat" : "sh")).getAbsolutePath();
    String configs = tcConfigLocationPerStripe.values()
        .stream()
        .sorted() // keep ordering so that stripe idx does not change
        .map(File::getAbsolutePath).collect(Collectors.joining(" "));
    List<String> hostPortList = getHostPortList();

    script = script + (clientSecurityCheckBox.getValue() ? (" -srd " + clientSecurityField.getValue()) : "");

    String hostPortOpt = " -s ";
    String hostPortJoiner = isDynamicConfig() ? "," : hostPortOpt;

    switch (command) {

      case "configure":
      case "reconfigure": {
        if (settings.getLicensePath() == null) {
          Notification.show("ERROR", "Please set a license file location!", Notification.Type.ERROR_MESSAGE);
          return;
        }
        ProcUtils.run(
            workDir,
            script + " " + command + "  -n " + clusterNameTF.getValue() + " -l " + settings.getLicensePath() + " " + configs,
            consoleLines,
            newLine -> {
            },
            () -> access(() -> {
              updateMainConsole(consoleLines);
              consoles.setSelectedTab(mainConsole);
            }));
        break;
      }

      case "dump":
      case "status":
      case "backup":
      case "stop": {
        if (command.equals("stop") && isDynamicConfig()) {
          command = "shutdown";
        }

        ProcUtils.run(
            workDir,
            script + " " + command + " -n " + clusterNameTF.getValue() + hostPortOpt + hostPortList.stream().collect(Collectors.joining(hostPortJoiner)),
            consoleLines,
            newLine -> access(() -> updateMainConsole(consoleLines)),
            () -> access(() -> consoles.setSelectedTab(mainConsole)));
        break;
      }
      default:
        // probably a serverName is coming in, so we want status for this server
        String hostPort = getHostPortFromServerName(command);

        ProcUtils.run(
            workDir,
            script + " status -s " + hostPort,
            consoleLines,
            newLine -> access(() -> updateMainConsole(consoleLines)),
            () -> access(() -> consoles.setSelectedTab(mainConsole)));
        break;

    }

    consoles.setSelectedTab(mainConsole);
    updateMainConsole(consoleLines);
  }

  private void executeConfigToolCommand(Button.ClickEvent event) {
    String command = (String) event.getButton().getData();
    File workDir = new File(settings.getKitPath());
    LinkedBlockingQueue<String> consoleLines = new LinkedBlockingQueue<>(); // no limit, get all the output
    String script = new File(workDir, "tools/bin/config-tool." + (ProcUtils.isWindows() ? "bat" : "sh")).getAbsolutePath();
    String config = tcConfigLocationPerStripe.values()
        .stream()
        .findAny()
        .map(File::getAbsolutePath).get();

    script = script + (clientSecurityCheckBox.getValue() ? (" -srd " + clientSecurityField.getValue()) : "");

    switch (command) {
      case "activate": {
        ProcUtils.run(
            workDir,
            script + " " + command
                + " -f " + config
                + " -n " + clusterNameTF.getValue()
                + (settings.getLicensePath().map(File::new).map(f -> " -l " + f).orElse("")),
            consoleLines,
            newLine -> access(() -> updateMainConsole(consoleLines)),
            () -> access(() -> consoles.setSelectedTab(mainConsole)));
        break;
      }
      default:
        break;
    }

    consoles.setSelectedTab(mainConsole);
    updateMainConsole(consoleLines);
  }

  private String getHostPortFromServerName(String serverName) {
    List<String> serverNameList = getServerNameList();
    List<String> hostPortList = getHostPortList();
    return hostPortList.get(serverNameList.indexOf(serverName));
  }

  private void updateMainConsole(Queue<String> consoleLines) {
    updateTextArea(mainConsole, consoleLines);
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
    serverControls.setColumns(6);

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
          TextField nodeHostnameTF = (TextField) form.getComponent(2);
          String nodeHostname = nodeHostnameTF.getValue();
          TextField clientPortTF = (TextField) form.getComponent(3);
          String clientPort = clientPortTF.getValue();

          serverControls.addComponent(new Label(serverName));

          Button startBT = new Button();
          startBT.setCaption("START");
          startBT.setDescription("Only works for servers on same host as TinyPounder");
          startBT.setData(serverName);
          startBT.setStyleName("align-top");
          serverControls.addComponent(startBT);

          Button killBT = new Button();
          killBT.setEnabled(false);
          killBT.setCaption("KILL");
          killBT.setDescription("Only works for servers on same host as TinyPounder");
          killBT.setStyleName("align-top");
          killBT.setData(serverName);
          serverControls.addComponent(killBT);

          Button statusBT = new Button();
          statusBT.setEnabled(false);
          statusBT.setCaption("STATUS");
          statusBT.setStyleName("align-top");
          statusBT.setData(serverName);
          serverControls.addComponent(statusBT);

          Label pid = new Label();
          serverControls.addComponent(pid);

          Label state = new Label();
          serverControls.addComponent(state);

          addConsole(serverName, stripeName + "-" + serverName);

          startBT.addClickListener((Button.ClickListener) event -> {
            String clusterName = clusterNameTF.getValue();
            startServer(clusterName, stripeName, (String) event.getButton().getData(), nodeHostname, clientPort, startBT, killBT,
                statusBT, state, pid);
          });
          killBT.addClickListener((Button.ClickListener) event -> {
            killServer(stripeName, (String) event.getButton().getData(), killBT, statusBT);
          });
          statusBT.addClickListener((Button.ClickListener) this::executeClusterToolCommand);
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
          TextField hostNameTF = (TextField) form.getComponent(2);
          TextField clientPortTF = (TextField) form.getComponent(3);
          servers.add(hostNameTF.getValue() + ":" + clientPortTF.getValue());
        }
      }
    }
    return servers;
  }

  private List<String> getServerNameList() {
    int nStripes = serverGrid.getRows() - 1;
    int nServersPerStripe = serverGrid.getColumns() - 1;
    List<String> servers = new ArrayList<>(nStripes * nServersPerStripe);
    for (int stripeId = 1; stripeId < serverGrid.getRows(); stripeId++) {
      for (int serverId = 1; serverId < serverGrid.getColumns(); serverId++) {
        FormLayout form = (FormLayout) serverGrid.getComponent(serverId, stripeId);
        if (form != null) {
          TextField serverName = (TextField) form.getComponent(0);
          servers.add(serverName.getValue());
        }
      }
    }
    return servers;
  }

  private void killServer(String stripeName, String serverName, Button killBT, Button statusBT) {
    RunningServer runningServer = runningServers.get(stripeName + "-" + serverName);
    if (runningServer != null) {
      runningServer.kill();
      killBT.setEnabled(false);
      statusBT.setEnabled(false);
      runningServer.refreshConsole();
    }
  }

  private boolean isDynamicConfig() {
    return Paths.get(settings.getKitPath(), "init").toFile().exists();
  }

  private void startServer(String clusterName, String stripeName, String serverName, String hostname, String clientPort,
                           Button startBT, Button killBT, Button statusBT, Label stateLBL, Label pidLBL) {
    File stripeconfig = tcConfigLocationPerStripe.get(stripeName);
    if (stripeconfig == null) {
      if (!isDynamicConfig()) {
        generateXML(false);
      } else {
        generatePropertiesFile(false);
      }
      stripeconfig = tcConfigLocationPerStripe.get(stripeName);
    }

    File workDir = new File(settings.getKitPath());
    String key = stripeName + "-" + serverName;
    TextArea console = getConsole(key);

    RunningServer runningServer = new RunningServer(
        workDir, clusterName, stripeconfig, stripeName, serverName, hostname, clientPort, console, 500,
        () -> {
          runningServers.remove(key);
          access(() -> {
            killBT.setEnabled(false);
            statusBT.setEnabled(false);
            startBT.setEnabled(true);
            pidLBL.setValue("");
            stateLBL.setValue("TERMINATED");
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
    startBT.setEnabled(false);
    killBT.setEnabled(true);
    statusBT.setEnabled(true);
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

    HorizontalLayout locationLayout = new HorizontalLayout();
    // trash data button
    trashDataButton = new Button("Delete this folder");
    trashDataButton.setStyleName("delete");

    // base location
    baseLocation = new TextField();
    baseLocation.setStyleName("base");
    baseLocation.setCaption("Base location for logs, backups and dataroots");
    baseLocation.addValueChangeListener(event -> {
      String pathname = event.getValue();
      changeTrashButtonStatus(pathname);
    });
    baseLocation.setValue(new File(HOME, "terracotta/MyCluster").getAbsolutePath());
    trashDataButton.addClickListener(event -> {
      File file = new File(baseLocation.getValue());
      if (file.exists() && new File(file, "logs").exists() && new File(file, "data").exists()) {
        Path rootPath = file.toPath();
        try {
          Files.walk(rootPath, FileVisitOption.FOLLOW_LINKS)
              .sorted(Comparator.reverseOrder())
              .map(Path::toFile)
              .forEach(File::delete);
          displayWarningNotification("Folder deleted with success");
          changeTrashButtonStatus(baseLocation.getValue());
        } catch (IOException e) {
          displayErrorNotification("Could not delete the folder", e);
        }
      } else {
        displayErrorNotification("Could not delete the folder", "Either folder does not exist or does not have logs/ nor data/ in it");
      }
    });
    baseLocation.addValueChangeListener(event -> {
      String oldValue = event.getOldValue();
      String eventValue = event.getValue();
      Consumer<Component> updatePath = component -> {
        if (component instanceof TextField && !component.isEnabled()) {
          ((TextField) component).setValue(((TextField) component).getValue().replace(oldValue, eventValue));
        } else if (component instanceof FormLayout) {
          ((FormLayout) component).forEach(subComponent -> {
            if (subComponent instanceof TextField && !subComponent.isEnabled()) {
              ((TextField) subComponent).setValue(((TextField) subComponent).getValue().replace(oldValue, eventValue));
            }
          });
        }
      };
      dataRootGrid.iterator().forEachRemaining(updatePath);
      serverGrid.iterator().forEachRemaining(updatePath);
      clusterNameTF.setValue(Paths.get(eventValue).getFileName().toString());
    });

    locationLayout.addComponentsAndExpand(baseLocation, trashDataButton);

    layout.addComponentsAndExpand(locationLayout);

    // offheap resources
    {
      int nOffheaps = settings.getOffheapCount();

      offheapGrid = new GridLayout(2, nOffheaps + 1);

      offheaps = new Slider(nOffheaps + " offheap resources", 1, 4);
      offheaps.setValue((double) nOffheaps);
      offheaps.addValueChangeListener((HasValue.ValueChangeListener<Double>) event -> {
        offheaps.setCaption(event.getValue().intValue() + " offheap resources");
        settings.setOffheapCount(event.getValue().intValue());
        updateOffHeapGrid();
      });
      offheapGrid.addComponent(offheaps, 0, 0, 1, 0);

      updateOffHeapGrid();

      layout.addComponentsAndExpand(offheapGrid);
    }

    // ee stuff
    if (ee) {
      int nData = settings.getDataRootCount();

      dataRootGrid = new GridLayout(3, nData + 1);
      dataRootGrid.setWidth(100, Unit.PERCENTAGE);
      dataRootGrid.setColumnExpandRatio(2, 2);

      // data roots
      dataRoots = new Slider(nData + " data roots", 1, 10);
      dataRoots.setValue((double) nData);
      dataRoots.addValueChangeListener(event -> {
        dataRoots.setCaption(event.getValue().intValue() + " data roots");
        settings.setDataRootCount(event.getValue().intValue());
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

      //security
      serverSecurityGrid = new HorizontalLayout();
      serverSecurityCheckBox = new CheckBox();
      serverSecurityCheckBox.setCaption(SECURITY);

      serverSecurityCheckBox.addStyleName(ValoTheme.OPTIONGROUP_HORIZONTAL);

      serverSecurityGrid.addComponent(serverSecurityCheckBox);
      serverSecurityCheckBox.addStyleName("align-bottom25");
      serverSecurityField = new TextField("Security Root Directory");
      serverSecurityField.setValue(settings.getServerSecurityPath() != null ? settings.getServerSecurityPath() : "");

      serverSecurityCheckBox.addValueChangeListener(event -> {
        if (!event.getValue()) {
          serverSecurityGrid.removeComponent(serverSecurityField);
        } else {
          serverSecurityGrid.addComponentsAndExpand(serverSecurityField);
        }
      });

      serverSecurityField.addValueChangeListener(event -> {
        if (kitAwareClassLoaderDelegator.verifySecurityPath(serverSecurityField.getValue())) {
          displayWarningNotification("Security Root Directory location updated with success !");
          settings.setServerSecurityPath(serverSecurityField.getValue());
        } else {
          displayErrorNotification("Security Root Directory path could not update !", "Make sure the path points to a security root directory !");
        }
      });

      layout.addComponentsAndExpand(serverSecurityGrid);

      //consistency and voters
      consistencyGrid = new GridLayout(2, 1);
      consistencyGroup = new RadioButtonGroup<>();
      consistencyGroup.setItems(AVAILABILITY, CONSISTENCY);
      consistencyGroup.addStyleName(ValoTheme.OPTIONGROUP_HORIZONTAL);

      consistencyGrid.addComponent(consistencyGroup);
      consistencyGroup.addStyleName("align-bottom");

      FormLayout votersGroup = new FormLayout();
      votersGroup.addStyleName("align-bottom3");
      votersCountTextField = new TextField("Voters count");
      votersCountTextField.setMaxLength(2);

      votersCountTextField.addValueChangeListener(event -> {
        try {
          int n = Integer.parseInt(event.getValue());
          votersCountTextField.setCaption("Voters count");
          settings.setFailoverPriority("consistency:" + n);
        } catch (NumberFormatException e) {
          votersCountTextField.setCaption("Voters count - MUST BE INTEGER");
        }
      });

      votersGroup.addComponent(votersCountTextField);

      consistencyGroup.addValueChangeListener(event -> {
        if (event.getValue().equals(AVAILABILITY)) {
          consistencyGrid.removeComponent(votersGroup);
          settings.setFailoverPriority(AVAILABILITY);
        } else {
          consistencyGrid.addComponent(votersGroup, 1, 0);
          try {
            int n = Integer.parseInt(votersCountTextField.getValue());
            settings.setFailoverPriority(CONSISTENCY + ":" + n);
          } catch (NumberFormatException e) {
            settings.setFailoverPriority(CONSISTENCY);
          }
        }
      });

      layout.addComponentsAndExpand(consistencyGrid);

      if(CONSISTENCY.equals(settings.getFailoverPriorityMode())) {
        votersCountTextField.setValue(settings.getVoterCount());
      }
      consistencyGroup.setSelectedItem(settings.getFailoverPriorityMode());
    }

    // stripe / server form
    {
      int nStripes = ee ? settings.getStripeCount() : 1;
      int nServers = settings.getServerCount();
      int nReconWin = settings.getReconnectWindow();

      serverGrid = new GridLayout(MIN_SERVER_GRID_COLS, nStripes + 1);
      serverGrid.setWidth(100, Unit.PERCENTAGE);
      serverGrid.addStyleName("server-grid");

      stripes = new Slider(nStripes + " stripes", 1, ee ? 8 : 1);
      stripes.setValue((double) nStripes);
      stripes.addValueChangeListener((HasValue.ValueChangeListener<Double>) event -> {
        stripes.setCaption(event.getValue().intValue() + " stripes");
        settings.setStripeCount(event.getValue().intValue());
        updateServerGrid();
      });
      stripes.setReadOnly(!ee);
      serverGrid.addComponent(stripes);

      servers = new Slider(nServers + " servers per stripe", 1, 4);
      servers.setValue((double) nServers);
      servers.addValueChangeListener(event -> {
        servers.setCaption(event.getValue().intValue() + " servers per stripe");
        settings.setServerCount(event.getValue().intValue());
        updateServerGrid();
      });
      serverGrid.addComponent(servers);

      reconnectWindow = new Slider("Reconnect window: " + nReconWin + " seconds", 5, 300);
      reconnectWindow.setValue((double) nReconWin);
      reconnectWindow.addValueChangeListener(event -> {
        reconnectWindow.setCaption("Reconnect window: " + event.getValue().intValue() + " seconds");
        settings.setReconnectWindow(event.getValue().intValue());
      });
      serverGrid.addComponent(reconnectWindow);

      layout.addComponentsAndExpand(serverGrid);
    }

    // XML file generation
    {
      generateTcConfig = new Button(isDynamicConfig() ? "Generate config file" : "Generate all config files");
      generateTcConfig.addStyleName("align-bottom");
      generateTcConfig.setWidth(100, Unit.PERCENTAGE);
      generateTcConfig.addClickListener((Button.ClickListener) event -> {
        if (!isDynamicConfig()) {
          generateXML(true);
        } else {
          generatePropertiesFile(true);
        }
        List<String> filenames = tcConfigLocationPerStripe.values().stream().map(File::getName).collect(Collectors.toList());
        Notification.show("Configurations saved:", "Location: " + settings.getKitPath() + System.lineSeparator() + "Files: " + filenames, Notification.Type.HUMANIZED_MESSAGE);
      });
      layout.addComponentsAndExpand(generateTcConfig);

      configTextArea = new TextArea();
      configTextArea.setWidth(100, Unit.PERCENTAGE);
      configTextArea.setWordWrap(false);
      configTextArea.setRows(50);
      configTextArea.setStyleName("tc-config-xml");
      layout.addComponentsAndExpand(configTextArea);
    }

    voltronConfigLayout.addComponentsAndExpand(layout);
  }

  private void changeTrashButtonStatus(String pathname) {
    File file = new File(pathname);
    if (!file.exists()) {
      trashDataButton.setEnabled(false);
      trashDataButton.setCaption("Folder will be created");
    } else {
      if (file.isDirectory()) {
        trashDataButton.setEnabled(true);
        trashDataButton.setCaption("Delete this folder");
      } else {
        trashDataButton.setEnabled(false);
        trashDataButton.setCaption("What kind of path is that ?");
      }
    }
  }

  private void generateXML(boolean skipConfirmOverwrite) {
    boolean ee = kitAwareClassLoaderDelegator.isEEKit();

    tcConfigLocationPerStripe.clear();
    configTextArea.setValue("");

    for (int stripeRow = 1; stripeRow < serverGrid.getRows(); stripeRow++) {

      // starts xml
      StringBuilder sb;
      if (ee) {
        sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + System.lineSeparator() +
          System.lineSeparator() +
          "<tc-config xmlns=\"http://www.terracotta.org/config\" " + System.lineSeparator() +
          "           xmlns:ohr=\"http://www.terracotta.org/config/offheap-resource\"" + System.lineSeparator() +
          "           xmlns:backup=\"http://www.terracottatech.com/config/backup-restore\"" + System.lineSeparator() +
          "           xmlns:data=\"http://www.terracottatech.com/config/data-roots\">" + System.lineSeparator() +
          System.lineSeparator() +
          "  <plugins>" + System.lineSeparator() +
          System.lineSeparator());
      } else {
        sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + System.lineSeparator() +
          System.lineSeparator() +
          "<tc-config xmlns=\"http://www.terracotta.org/config\" " + System.lineSeparator() +
          "           xmlns:ohr=\"http://www.terracotta.org/config/offheap-resource\">" + System.lineSeparator() +
          System.lineSeparator() +
          "  <plugins>" + System.lineSeparator() +
          System.lineSeparator());
      }

      // offheaps
      if (offheapGrid.getRows() > 1) {
        sb.append("    <config>" + System.lineSeparator() +
            "      <ohr:offheap-resources>"+ System.lineSeparator());
        for (int r = 1; r < offheapGrid.getRows(); r++) {
          TextField name = (TextField) offheapGrid.getComponent(0, r);
          TextField memory = (TextField) offheapGrid.getComponent(1, r);
          sb.append("        <ohr:resource name=\"" + name.getValue() + "\" unit=\"MB\">" + memory.getValue() + "</ohr:resource>"+ System.lineSeparator());
        }
        sb.append("      </ohr:offheap-resources>" + System.lineSeparator() +
            "    </config>" + System.lineSeparator() +
            System.lineSeparator());
      }

      if (ee) {
        // dataroots
        sb.append("    <config>" + System.lineSeparator() +
            "      <data:data-directories>"+ System.lineSeparator());
        // platform persistece
        if (platformPersistence.getValue()) {
          TextField path = (TextField) dataRootGrid.getComponent(DATAROOT_PATH_COLUMN, getPersistenceRow());
          sb.append("        <data:directory name=\"PLATFORM\" use-for-platform=\"true\">" + path.getValue() + "</data:directory>"+ System.lineSeparator());
        }

        // do not know why but .getComponent(x,y) does not work
//        for (int r = getDataRootFirstRow(); r < dataRootGrid.getRows(); r++) {
//          TextField name = (TextField) dataRootGrid.getComponent(DATAROOT_NAME_COLUMN, r);
//          TextField path = (TextField) dataRootGrid.getComponent(DATAROOT_PATH_COLUMN, r);
//          sb.append("        <data:directory name=\"" + name.getValue() + "\" use-for-platform=\"false\">" + path.getValue() + "</data:directory>" + System.lineSeparator());
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
          sb.append("        <data:directory name=\"" + name.getValue() + "\" use-for-platform=\"false\">" + path.getValue() + "</data:directory>"+ System.lineSeparator());
        }

        // end data roots
        sb.append("      </data:data-directories>" + System.lineSeparator() +
            "    </config>" + System.lineSeparator() + System.lineSeparator() );

        // backup
        if (platformBackup.getValue()) {
          TextField path = (TextField) dataRootGrid.getComponent(DATAROOT_PATH_COLUMN, getBackupRow());
          sb.append("    <service>" + System.lineSeparator() +
              "      <backup:backup-restore>" + System.lineSeparator() +
              "        <backup:backup-location path=\"" + path.getValue() + "\" />" + System.lineSeparator() +
              "      </backup:backup-restore>" + System.lineSeparator() +
              "    </service>" + System.lineSeparator() + System.lineSeparator());
        }

        // security
        if (serverSecurityCheckBox.getValue()) {

          sb.append(
              "    <service xmlns:security=\"http://www.terracottatech.com/config/security\">" + System.lineSeparator() +
              "      <security:security>" + System.lineSeparator() +
              "        <security:security-root-directory>" + serverSecurityField.getValue() + "</security:security-root-directory>" + System.lineSeparator() +
              "        <security:ssl-tls/>" + System.lineSeparator() +
              "        <security:authentication>" + System.lineSeparator() +
              "          <security:file/>" + System.lineSeparator() +
              "        </security:authentication>" + System.lineSeparator() +
              "      </security:security>" + System.lineSeparator() +
              "    </service>" + System.lineSeparator() + System.lineSeparator());
        }
      }

      // servers
      sb.append(
          "  </plugins>" + System.lineSeparator() + System.lineSeparator() +
          "  <servers>" + System.lineSeparator() +  System.lineSeparator());

      for (int serverCol = 1; serverCol < serverGrid.getColumns(); serverCol++) {
        FormLayout form = (FormLayout) serverGrid.getComponent(serverCol, stripeRow);
        if (form != null) {
          TextField name = (TextField) form.getComponent(0);
          TextField logs = (TextField) form.getComponent(1);
          TextField hostName = (TextField) form.getComponent(2);
          TextField clientPort = (TextField) form.getComponent(3);
          TextField groupPort = (TextField) form.getComponent(4);
          sb.append(
              "    <server host=\"" + hostName.getValue() + "\" name=\"" + name.getValue() + "\">" + System.lineSeparator() +
              "      <logs>" + logs.getValue() + "</logs>" + System.lineSeparator() +
              "      <tsa-port>" + clientPort.getValue() + "</tsa-port>" + System.lineSeparator() +
              "      <tsa-group-port>" + groupPort.getValue() + "</tsa-group-port>" + System.lineSeparator() +
              "    </server>"+ System.lineSeparator() + System.lineSeparator());
        }
      }

      // reconnect window
      sb.append(
        "    <client-reconnect-window>" + reconnectWindow.getValue().intValue() + "</client-reconnect-window>"+ System.lineSeparator() + System.lineSeparator());
      sb.append("  </servers>"+ System.lineSeparator() + System.lineSeparator());

      if (consistencyGroup.isSelected(CONSISTENCY)) {
        int votersCount = Integer.parseInt(votersCountTextField.getValue());
        sb.append(
            "  <failover-priority>" + System.lineSeparator() +
            "    <consistency>" + System.lineSeparator() +
            "      <voter count=\"" + votersCount + "\"/>" + System.lineSeparator() +
            "    </consistency>" + System.lineSeparator() +
            "  </failover-priority>"+ System.lineSeparator() + System.lineSeparator());
      } else {
        sb.append(
            "  <failover-priority>" + System.lineSeparator() +
            "    <availability/>" + System.lineSeparator() +
            "  </failover-priority>"+ System.lineSeparator() + System.lineSeparator());
      }

      // ends XML
      sb.append("</tc-config>");

      String filename = "tc-config-stripe-" + stripeRow + ".xml";
      File location = new File(settings.getKitPath(), filename);
      tcConfigLocationPerStripe.put("stripe-" + stripeRow, location);

      String xml;
      if (location.exists() && !skipConfirmOverwrite) {
        Notification.show("Config already found: " + location.getName());
        try {
          xml = new String(Files.readAllBytes(location.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      } else {
        xml = sb.toString();
        try {
          Files.write(location.toPath(), xml.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }

      configTextArea.setValue(configTextArea.getValue() + xml + System.lineSeparator() + System.lineSeparator());

    }
  }

  private void generatePropertiesFile(boolean skipConfirmOverwrite) {
    boolean ee = kitAwareClassLoaderDelegator.isEEKit();

    tcConfigLocationPerStripe.clear();
    configTextArea.setValue("");

    StringBuilder sb = new StringBuilder();

    if (offheapGrid.getRows() > 1) {
      List<String> offheapList = new ArrayList<>();
      for (int r = 1; r < offheapGrid.getRows(); r++) {
        TextField name = (TextField) offheapGrid.getComponent(0, r);
        TextField memory = (TextField) offheapGrid.getComponent(1, r);
        offheapList.add(name.getValue() + ":" + memory.getValue() + "MB");
      }
      sb.append("offheap-resources=" + String.join(",", offheapList) + System.lineSeparator());
    }

    sb.append("client-reconnect-window=" + reconnectWindow.getValue().intValue() + "s"+ System.lineSeparator());

    if (consistencyGroup.isSelected(CONSISTENCY)) {
      int votersCount = Integer.parseInt(votersCountTextField.getValue());
      sb.append("failover-priority=consistency:" + votersCount + System.lineSeparator());
    } else {
      sb.append("failover-priority=availability"+ System.lineSeparator());
    }

    for (int stripeRow = 1; stripeRow < serverGrid.getRows(); stripeRow++) {
      sb.append(String.format("stripe.%d.stripe-name=stripe-%d"+ System.lineSeparator(), stripeRow, stripeRow));

      for (int serverCol = 1; serverCol < serverGrid.getColumns(); serverCol++) {
        FormLayout form = (FormLayout) serverGrid.getComponent(serverCol, stripeRow);
        if (form != null) {
          TextField name = (TextField) form.getComponent(0);
          TextField logs = (TextField) form.getComponent(1);
          TextField hostName = (TextField) form.getComponent(2);
          TextField clientPort = (TextField) form.getComponent(3);
          TextField groupPort = (TextField) form.getComponent(4);

          String nodePrefix = "stripe." + stripeRow + ".node." + serverCol + ".";
          sb.append(nodePrefix + "name=" + name.getValue() + System.lineSeparator());
          sb.append(nodePrefix + "hostname=" + hostName.getValue() + System.lineSeparator());
          sb.append(nodePrefix + "port=" + clientPort.getValue() + System.lineSeparator());
          sb.append(nodePrefix + "group-port=" + groupPort.getValue() + System.lineSeparator());
          sb.append(nodePrefix + "log-dir=" + createPath(logs.getValue()) + System.lineSeparator());

          // For testing
          sb.append(nodePrefix + "tc-properties=logging.maxBackups:10,logging.maxLogFileSize:256"+ System.lineSeparator());
          sb.append(nodePrefix + "logger-overrides=org.terracotta.management:INFO,com.terracottatech.management:INFO"+ System.lineSeparator());

          if (platformBackup.getValue()) {
            TextField path = (TextField) dataRootGrid.getComponent(DATAROOT_PATH_COLUMN, getBackupRow());
            sb.append(nodePrefix + "backup-dir=" + createPath(path.getValue()) + System.lineSeparator());
          }

          if (ee) {
            if (serverSecurityCheckBox.getValue()) {
              sb.append("security-dir=" + createPath(serverSecurityField.getValue()) + System.lineSeparator());
            }

            if (platformPersistence.getValue()) {
              TextField path = (TextField) dataRootGrid.getComponent(DATAROOT_PATH_COLUMN, getPersistenceRow());
              sb.append(nodePrefix + "metadata-dir=" + createPath(path.getValue()) + System.lineSeparator());
            }

            // do not know why but .getComponent(x,y) does not work
//        for (int r = getDataRootFirstRow(); r < dataRootGrid.getRows(); r++) {
//          TextField name = (TextField) dataRootGrid.getComponent(DATAROOT_NAME_COLUMN, r);
//          TextField path = (TextField) dataRootGrid.getComponent(DATAROOT_PATH_COLUMN, r);
//          sb.append("        <data:directory name=\"" + name.getValue() + "\" use-for-platform=\"false\">" + path.getValue() + "</data:directory>" + System.lineSeparator());
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
            List<String> dataDirList = new ArrayList<>();
            for (int i = 0; i < components.size(); i += 2) {
              TextField dataDirName = (TextField) components.get(i);
              TextField dataDirPath = (TextField) components.get(i + 1);
              dataDirList.add(dataDirName.getValue() + ":" + createPath(dataDirPath.getValue()));
            }
            sb.append((nodePrefix + "data-dirs=" + String.join(",", dataDirList) + System.lineSeparator()));
          }
        }
      }
    }

    String filename = "cluster.properties";
    File location = new File(settings.getKitPath(), filename);
    String configText;

    if (location.exists() && !skipConfirmOverwrite) {
      Notification.show("Config already found: " + location.getName());
      try {
        configText = new String(Files.readAllBytes(location.toPath()), "UTF-8");
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    } else {
      configText = sb.toString();
      try {
        Files.write(location.toPath(), configText.getBytes("UTF-8"));
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    configTextArea.setValue(configText);

    for (int stripeRow = 1; stripeRow < serverGrid.getRows(); stripeRow++) {
      tcConfigLocationPerStripe.put("stripe-" + stripeRow, location);
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
        path.setValue(new File(baseLocation.getValue(), "data/dataroot-" + (r - header + 1)).getAbsolutePath());
        path.setEnabled(false);
        id.addValueChangeListener(event -> path.setValue(new File(baseLocation.getValue(), "data/" + event.getValue()).getAbsolutePath()));
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
      path.setValue(new File(baseLocation.getValue(), "data/backup").getAbsolutePath());
      path.setEnabled(false);
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
      path.setValue(new File(baseLocation.getValue(), "data/platform").getAbsolutePath());
      path.setEnabled(false);
      path.setWidth(100, Unit.PERCENTAGE);
      dataRootGrid.addComponent(id, 0, row, 1, row);
      dataRootGrid.addComponent(path, DATAROOT_PATH_COLUMN, row);
    } else {
      dataRootGrid.removeRow(row);
    }
  }

  private void updateServerGrid() {
    if (settings.getKitPath() != null) {
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
                  new Label("Host name"),
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
              logs.setValue(new File(baseLocation.getValue(), "logs/" + name.getValue()).getAbsolutePath());
              logs.setEnabled(false);
              name.addValueChangeListener(event -> logs.setValue(new File(baseLocation.getValue(), "logs/" + name.getValue()).getAbsolutePath()));
              TextField hostName = new TextField();
              hostName.setPlaceholder("Host name");
              //hostName.setValue("SAG-C02YF3AEJGH6.local");
              hostName.setValue("localhost");
              TextField clientPort = new TextField();
              clientPort.setPlaceholder("Client port");
              clientPort.setValue("" + (9410 + (r - 1) * 10 + (c - 1)));
              TextField groupPort = new TextField();
              groupPort.setPlaceholder("Group port");
              groupPort.setValue("" + (9530 + (r - 1) * 10 + (c - 1)));
              form.addComponents(name, logs, hostName, clientPort, groupPort);
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

    List<Long> onHeapValues = Arrays.asList(0L, 1L, 10L, 100L, 1000L, 10_000L);
    ComboBox<Long> onHeapSizeComboBox = new ComboBox<>("OnHeap size", onHeapValues);
    onHeapSizeComboBox.addStyleName("small-combo");
    onHeapSizeComboBox.setTextInputAllowed(false);
    onHeapSizeComboBox.setEmptySelectionAllowed(false);
    onHeapSizeComboBox.setValue(onHeapValues.get(3));
    cacheCreation.addComponent(onHeapSizeComboBox);

    List<String> onHeapUnitValues = Arrays.asList("ENTRIES", "KB", "MB", "GB");
    ComboBox<String> onHeapUnitComboBox = new ComboBox<>("OnHeap unit", onHeapUnitValues);
    onHeapUnitComboBox.setValue(onHeapUnitValues.get(0));
    onHeapUnitComboBox.setEmptySelectionAllowed(false);
    onHeapUnitComboBox.setTextInputAllowed(false);
    cacheCreation.addComponent(onHeapUnitComboBox);

    List<Long> offHeapValues = Arrays.asList(0L, 1L, 10L, 100L, 1000L, 10_000L);
    ComboBox<Long> offHeapSizeComboBox = new ComboBox<>("Offheap size", offHeapValues);
    offHeapSizeComboBox.addStyleName("small-combo");
    offHeapSizeComboBox.setTextInputAllowed(false);
    offHeapSizeComboBox.setEmptySelectionAllowed(false);
    offHeapSizeComboBox.setValue(offHeapValues.get(1));
    cacheCreation.addComponent(offHeapSizeComboBox);

    List<String> offHeapUnitValues = Arrays.asList("KB", "MB", "GB");
    ComboBox<String> offHeapUnitComboBox = new ComboBox<>("OffHeap unit", offHeapUnitValues);
    offHeapUnitComboBox.addStyleName("small-combo");
    offHeapUnitComboBox.setValue(offHeapUnitValues.get(1));
    offHeapUnitComboBox.setEmptySelectionAllowed(false);
    offHeapUnitComboBox.setTextInputAllowed(false);
    cacheCreation.addComponent(offHeapUnitComboBox);

    List<Long> diskValues = Arrays.asList(0L, 1L, 10L, 100L, 1000L, 10_000L);
    ComboBox<Long> diskSizeComboBox = new ComboBox<>("Disk size", diskValues);
    diskSizeComboBox.addStyleName("small-combo");
    diskSizeComboBox.setEmptySelectionAllowed(false);
    diskSizeComboBox.setTextInputAllowed(false);
    diskSizeComboBox.setValue(diskValues.get(0));
    cacheCreation.addComponent(diskSizeComboBox);

    List<String> diskUnitValues = Arrays.asList("KB", "MB", "GB");
    ComboBox<String> diskUnitComboBox = new ComboBox<>("Disk unit", diskUnitValues);
    diskUnitComboBox.addStyleName("small-combo");
    diskUnitComboBox.setEmptySelectionAllowed(false);
    diskUnitComboBox.setTextInputAllowed(false);
    diskUnitComboBox.setValue(diskUnitValues.get(1));
    cacheCreation.addComponent(diskUnitComboBox);

    List<String> clusteredValues = Arrays.asList("NONE", "shared", "dedicated 10MB", "dedicated 100MB");
    ComboBox<String> clusteredComboBox = new ComboBox<>("Clustered tier", clusteredValues);
    clusteredComboBox.setEmptySelectionAllowed(false);
    clusteredComboBox.setTextInputAllowed(false);
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
        displayWarningNotification("Cache added with success !");
      } catch (RuntimeException e) {
        displayErrorNotification("Cache could not be added !", e);
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
          displayWarningNotification("Cache removed with success !");
        } catch (RuntimeException e) {
          displayErrorNotification("Cache could not be removed !", e);
          refreshCacheStuff(listDataProvider);
        }
      });

      Button clearCacheButton = new Button("Clear cache");
      clearCacheButton.addClickListener(event -> {
        try {
          cacheManagerBusiness.clearCache(cacheName);
          displayWarningNotification("Cache cleared with success !");
        } catch (RuntimeException e) {
          displayErrorNotification("Cache could not be cleared !", e);
          refreshCacheStuff(listDataProvider);
        }
      });

      Button destroyCacheButton = new Button("Destroy cache");
      destroyCacheButton.addClickListener(event -> {
        try {
          cacheManagerBusiness.destroyCache(cacheName);
          cacheNames.remove(cacheName);
          refreshCacheStuff(listDataProvider);
          displayWarningNotification("Cache destroyed with success !");
        } catch (Exception e) {
          displayErrorNotification("Cache could not be destroyed !", e);
          refreshCacheStuff(listDataProvider);
        }
      });
      cacheInfo.addComponentsAndExpand(cacheNameLabel, poundingSlider, clearCacheButton, removeCacheButton, destroyCacheButton);
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


  private void displayWarningNotification(String caption) {
    Notification notification = new Notification(caption,
        Notification.Type.TRAY_NOTIFICATION);
    notification.setStyleName("warning");
    notification.show(Page.getCurrent());
  }

  private void displayErrorNotification(String caption, Throwable e) {
    e.printStackTrace();
    displayErrorNotification(caption, ExceptionUtils.getRootCauseMessage(e));
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


    CheckBox clusteredCheckBox = new CheckBox("Clustered service", true);
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

    CheckBox diskCheckBox = new CheckBox("Persistence", true);
    diskCheckBox.addStyleName("align-bottom");
    diskCheckBox.addValueChangeListener(valueChangeEvent -> {
      if (valueChangeEvent.getValue()) {
        diskPersistenceLocationField.setEnabled(true);
      } else {
        diskPersistenceLocationField.setEnabled(false);
      }
    });

    Button createCacheManagerButton = new Button("Initialize CacheManager");

    createCacheManagerButton.addClickListener(event -> {
      try {
        cacheManagerBusiness.initializeCacheManager(
            !clusteredCheckBox.getValue() ? null : terracottaUrlField.getValue(),
            clusterTierManagerNameField.getValue(),
            !diskCheckBox.getValue() ? null : diskPersistenceLocationField.getValue(),
            offHeapPersistenceLocationField.getValue(),
            serverDiskPersistenceLocationField.getValue(),
            clientSecurityCheckBox.getValue() ? clientSecurityField.getValue() : null);
        cacheManagerConfigTextArea.setValue(cacheManagerBusiness.retrieveHumanReadableConfiguration());
        refreshCacheManagerControls();
      } catch (Exception e) {
        displayErrorNotification("CacheManager could not be initialized!", e);
      }
    });

    Button closeCacheManager = new Button("Close CacheManager");
    closeCacheManager.addClickListener(event -> {
      try {
        cacheManagerBusiness.close();
        refreshCacheControls();
        refreshCacheManagerControls();
      } catch (Exception e) {
        displayErrorNotification("CacheManager could not be closed!", e);
      }
    });
    Button destroyCacheManager = new Button("Destroy CacheManager");
    destroyCacheManager.addClickListener(event -> {
      try {
        cacheManagerBusiness.destroy();
        refreshCacheControls();
        refreshCacheManagerControls();
        displayWarningNotification("CacheManager destroyed with success !");
      } catch (Exception e) {
        displayErrorNotification("CacheManager could not be destroyed!", e);
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
    kitPathLayout = new GridLayout(1, 1);
    kitPathLayout.setWidth(100, Unit.PERCENTAGE);
    kitPathLayout.setColumnExpandRatio(0, 2);

    Label info = new Label();
    if (settings.getKitPath() != null) {
      info.setValue("Using " + (kitAwareClassLoaderDelegator.isEEKit() ? "Enterprise Kit" : "Open source Kit"));
    } else {
      info.setValue("Enter Kit location:");
    }
    TextField kitPath = new TextField();
    kitPath.setPlaceholder("Kit location");
    kitPath.setWidth("100%");
    kitPath.setValue(settings.getKitPath() != null ? settings.getKitPath() : "");
    kitPath.addValueChangeListener(event -> {
      try {
        kitAwareClassLoaderDelegator.setAndVerifyKitPathAndClassLoader(kitPath.getValue());
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
        displayWarningNotification("Kit location updated with success !");
      } catch (Exception e) {
        if (e.getCause() instanceof NoSuchFileException) {
          displayErrorNotification("Kit path could not update !", "Make sure the path points to a kit !");
        } else {
          displayErrorNotification("Kit path could not update !", e);
        }
      }
    });
    kitPathLayout.addComponent(kitPath);
    kitControlsLayout.addComponent(info);
    kitControlsLayout.addComponent(kitPathLayout);
    mainLayout.addTab(kitControlsLayout, "STEP 1: KIT");
  }

  private void addClientSecurityControls() {
    clientSecurityControls = new HorizontalLayout();
    clientSecurityCheckBox = new CheckBox();
    clientSecurityCheckBox.setCaption(SECURITY);

    clientSecurityCheckBox.addStyleName(ValoTheme.OPTIONGROUP_HORIZONTAL);

    clientSecurityControls.addComponent(clientSecurityCheckBox);
    clientSecurityCheckBox.addStyleName("align-bottom25");
    clientSecurityField = new TextField("Security Root Directory");
    clientSecurityField.setValue(settings.getClientSecurityPath() != null ? settings.getClientSecurityPath() : "");

    clientSecurityCheckBox.addValueChangeListener(event -> {
      if (!event.getValue()) {
        clientSecurityControls.removeComponent(clientSecurityField);
      } else {
        clientSecurityControls.addComponentsAndExpand(clientSecurityField);
      }
    });

    clientSecurityField.addValueChangeListener(event -> {
      if (kitAwareClassLoaderDelegator.verifySecurityPath(clientSecurityField.getValue())) {
        displayWarningNotification("Security Root Directory location updated with success !");
        settings.setClientSecurityPath(clientSecurityField.getValue());
      } else {
        displayErrorNotification("Security Root Directory path could not update !", "Make sure the path points to a security root directory !");
      }
    });

    clientSecurityLayout.addComponent(clientSecurityControls);

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
        datasetManagerBusiness.initializeDatasetManager(
            !clusteredCheckBox.getValue() ? null : terracottaUrlField.getValue(),
            clientSecurityCheckBox.getValue() ? clientSecurityField.getValue() : null);
        refreshDatasetManagerControls();
      } catch (Exception e) {
        displayErrorNotification("DatasetManager could not be initialized!", e);
      }
    });

    Button closeDatasetManager = new Button("Close DatasetManager");
    closeDatasetManager.addClickListener(event -> {
      try {
        datasetManagerBusiness.close();
        refreshDatasetControls();
        refreshDatasetManagerControls();
      } catch (Exception e) {
        displayErrorNotification("DatasetManager could not be closed!", e);
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

    List<String> keyTypeValues = Arrays.asList("STRING", "INT", "LONG", "DOUBLE", "BOOL", "CHAR");
    ComboBox<String> keyTypeComboBox = new ComboBox<>("Key type", keyTypeValues);
    keyTypeComboBox.setStyleName("datasetAttribute");
    keyTypeComboBox.setEmptySelectionAllowed(false);
    keyTypeComboBox.setTextInputAllowed(false);
    keyTypeComboBox.setValue(keyTypeValues.get(0));

    HorizontalLayout offHeapOption = new HorizontalLayout();
    offHeapOption.setStyleName("datasetAttribute");
    TextField offHeapPersistenceLocationField = new TextField();
    CheckBox offHeapCheckBox = new CheckBox("offheap", true);
    offHeapCheckBox.addStyleName("bottom");
    offHeapCheckBox.addValueChangeListener(valueChangeEvent -> {
      if (valueChangeEvent.getValue()) {
        offHeapPersistenceLocationField.setEnabled(true);
      } else {
        offHeapPersistenceLocationField.setEnabled(false);
      }
    });
    offHeapPersistenceLocationField.setCaption("offheap resource name");
    offHeapPersistenceLocationField.setValue("offheap-1");
    offHeapOption.addComponents(offHeapCheckBox, offHeapPersistenceLocationField);

    HorizontalLayout diskOption = new HorizontalLayout();
    diskOption.setStyleName("datasetAttribute");
    TextField diskPersistenceLocationField = new TextField();
    CheckBox diskCheckBox = new CheckBox("disk", true);
    diskCheckBox.addStyleName("bottom");
    diskCheckBox.addValueChangeListener(valueChangeEvent -> {
      if (valueChangeEvent.getValue()) {
        diskPersistenceLocationField.setEnabled(true);
      } else {
        diskPersistenceLocationField.setEnabled(false);
      }
    });
    diskPersistenceLocationField.setCaption("disk resource name");
    diskPersistenceLocationField.setValue("dataroot-1");
    diskOption.addComponents(diskCheckBox, diskPersistenceLocationField);

    CheckBox indexCheckBox = new CheckBox("use index", true);
    indexCheckBox.setStyleName("datasetAttribute");
    indexCheckBox.addStyleName("bottom");

    Button addDatasetButton = new Button("Add dataset");
    addDatasetButton.setStyleName("datasetAttribute");

    datasetCreation.addComponents(datasetNameField, keyTypeComboBox, offHeapOption, diskOption, indexCheckBox, addDatasetButton);
    ListDataProvider<String> listDataProvider = new ListDataProvider<>(datasetNames);
    addDatasetButton.addClickListener(clickEvent -> {
      try {
        DatasetConfiguration datasetConfiguration = new DatasetConfiguration(keyTypeComboBox.getValue(), offHeapCheckBox.getValue() ? offHeapPersistenceLocationField.getValue() : null, diskCheckBox.getValue() ? diskPersistenceLocationField.getValue() : null, indexCheckBox.getValue());
        datasetManagerBusiness.createDataset(datasetNameField.getValue(), datasetConfiguration);
        datasetNames.add(datasetNameField.getValue());
        refreshDatasetStuff(listDataProvider);
        datasetNameField.clear();
        displayWarningNotification("Dataset added with success !");
      } catch (RuntimeException e) {
        displayErrorNotification("Dataset could not be added !", e);
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
          displayWarningNotification("Dataset instance " + datasetInstanceName + " created  with success !");
        } catch (Exception e) {
          displayErrorNotification("Dataset instance could not be created !", e);
          refreshDatasetStuff(listDataProvider);
        }
      });

      Button destroyDatasetButton = new Button("Destroy dataset");
      destroyDatasetButton.addClickListener(event -> {
        try {
          datasetManagerBusiness.destroyDataset(datasetName);
          datasetNames.remove(datasetName);
          refreshDatasetStuff(listDataProvider);
          displayWarningNotification("Dataset destroyed with success !");
        } catch (Exception e) {
          displayErrorNotification("Dataset could not be destroyed !", e);
          refreshDatasetStuff(listDataProvider);
        }
      });


      datasetInfoLabel.addComponentsAndExpand(datasetNameLabel, addDatasetInstanceButton, destroyDatasetButton);
      datasetListLayout.addComponent(datasetInfoLabel);

      Set<String> datasetInstanceNames = datasetManagerBusiness.getDatasetInstanceNames(datasetName);
      if (datasetInstanceNames.size() > 0) {
        destroyDatasetButton.setEnabled(false);
      }
      int count = 0;
      for (String instanceName : datasetInstanceNames) {
        HorizontalLayout datasetInstanceInfoLayout = new HorizontalLayout();
        if ((count++) % 2 == 0) {
          datasetInstanceInfoLayout.setStyleName("greyBackground");
        }
        Label datasetInstanceNameLabel = new Label(instanceName);
        datasetInstanceNameLabel.setStyleName("instance");

        TextField newCellField = new TextField();
        newCellField.setPlaceholder("myCellName:STRING");
        newCellField.setStyleName("instance");
        Button addCellButton = new Button("Add cell");
        addCellButton.setStyleName("instance");
        addCellButton.addClickListener(event -> {
          try {
            datasetManagerBusiness.addCustomCell(newCellField.getValue());
            displayWarningNotification("New cell is added.");
          } catch (Exception e) {
            displayErrorNotification("New cell cannot be added.", e);
          }
        });
        Button removeCellButton = new Button("Remove cell");
        removeCellButton.setStyleName("instance");
        removeCellButton.addClickListener(event -> {
          try {
            datasetManagerBusiness.removeCustomCell(newCellField.getValue());
            displayWarningNotification("New cell is removed.");
          } catch (Exception e) {
            displayErrorNotification("New cell cannot be removed.", e);
          }
        });
        Button closeDatasetButton = new Button("Close dataset instance");
        closeDatasetButton.setStyleName("instance");
        closeDatasetButton.addClickListener(event -> {
          try {
            datasetManagerBusiness.closeDatasetInstance(datasetName, instanceName);
            refreshDatasetStuff(listDataProvider);
            displayWarningNotification("Dataset instance closed with success !");
          } catch (Exception e) {
            displayErrorNotification("Dataset instance could not be closed !", e);
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


        datasetInstanceInfoLayout.addComponentsAndExpand(datasetInstanceNameLabel, newCellField, addCellButton, removeCellButton, poundingSlider, closeDatasetButton);
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

  private static String getVersion() {
    Package p = TinyPounderMainUI.class.getPackage();
    if (p == null) {
      return "dev";
    }
    String v = p.getImplementationVersion();
    return v == null ? "dev" : v;
  }

  static void updateTextArea(TextArea textArea, Collection<String> lines) {
    synchronized (textArea) {
      String text = String.join("", lines);
      if (!Objects.equals(textArea.getValue(), text)) {
        int position = textArea.getCursorPosition();
        boolean autoscroll = position >= textArea.getValue().length() - 1;
        textArea.setValue(text);
        textArea.setCursorPosition(autoscroll ? text.length() : Math.min(position, text.length()));
      }
    }
  }

  private static String createPath(String path) {
    return path.replace("\\", "/");
  }


}
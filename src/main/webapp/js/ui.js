/**
 * UI module for managing user interface interactions and state.
 */
const UI = {
  // Current state
  state: {
    kitInfo: null,
    config: null,
    isEE: false
  },

  /**
   * Initialize UI components
   */
  init() {
    this.initTabs();
    this.initNotifications();
    this.loadInitialState();
  },

  /**
   * Initialize tab switching
   */
  initTabs() {
    const tabButtons = document.querySelectorAll('.tab-button');
    const tabPanes = document.querySelectorAll('.tab-pane');

    tabButtons.forEach(button => {
      button.addEventListener('click', () => {
        const tabName = button.dataset.tab;

        if (tabName === 'exit') {
          if (confirm('Are you sure you want to exit TinyPounder? All running servers will be stopped.')) {
            window.close();
          }
          return;
        }

        // Remove active class from all buttons and panes
        tabButtons.forEach(btn => btn.classList.remove('active'));
        tabPanes.forEach(pane => pane.classList.remove('active'));

        // Add active class to clicked button and corresponding pane
        button.classList.add('active');
        document.getElementById(`${tabName}-tab`).classList.add('active');
      });
    });
  },

  /**
   * Initialize notification system
   */
  initNotifications() {
    this.notificationElement = document.getElementById('notification');
  },

  /**
   * Show notification
   */
  showNotification(message, type = 'info') {
    // Log notification to console
    const logPrefix = type === 'error' ? 'ERROR' : type === 'warning' ? 'WARN' : 'INFO';
    console.log(`[${logPrefix}] ${message}`);
    
    this.notificationElement.textContent = message;
    this.notificationElement.className = `notification ${type} show`;

    setTimeout(() => {
      this.notificationElement.classList.remove('show');
    }, 5000);
  },

  /**
   * Load initial state from backend
   */
  async loadInitialState() {
    try {
      const kitInfo = await API.kit.getInfo();
      this.state.kitInfo = kitInfo.data;
      this.state.isEE = kitInfo.data.isEE;
      this.updateKitUI();

      const config = await API.config.getCurrent();
      this.state.config = config.data;
      
      // Initialize plus-minus controls before updating config UI
      if (typeof initializePlusMinusControls === 'function') {
        initializePlusMinusControls();
      }
      
      this.updateConfigUI();
      
      // Restore security checkbox states
      await this.restoreSecurityState();
      
      // Restore cache manager state
      await this.restoreCacheManagerState();
      
      // Restore dataset manager state
      await this.restoreDatasetManagerState();
      
      // Note: Server state restoration happens in updateServerControls()
      // when the Control tab is clicked and the grid is created
    } catch (error) {
      console.error('Failed to load initial state', error);
    }
  },
  
  /**
   * Restore cache manager state from backend
   */
  async restoreCacheManagerState() {
    try {
      const statusResponse = await API.cacheManager.getStatus();
      if (statusResponse.success && statusResponse.data) {
        const status = statusResponse.data.status;
        const cmStatusElement = document.getElementById('cm-status');
        const cmCloseButton = document.getElementById('cm-close');
        const cmDestroyButton = document.getElementById('cm-destroy');
        const cmInitSection = document.getElementById('cache-manager-init');
        const cacheSection = document.getElementById('cache-section');
        
        if (cmStatusElement) {
          cmStatusElement.textContent = status;
        }
        
        // If cache manager is initialized, show cache section and enable buttons
        if (status === 'INITIALIZED') {
          if (cmCloseButton) cmCloseButton.disabled = false;
          if (cmDestroyButton) cmDestroyButton.disabled = false;
          if (cmInitSection) cmInitSection.style.display = 'none';
          if (cacheSection) cacheSection.style.display = 'block';
          
          // Trigger cache list refresh (refreshCacheList is defined in app.js)
          if (typeof refreshCacheList === 'function') {
            await refreshCacheList();
          }
        }
      }
    } catch (error) {
      console.error('Failed to restore cache manager state:', error);
    }
  },
  
  /**
   * Restore dataset manager state from backend
   */
  async restoreDatasetManagerState() {
    try {
      const statusResponse = await API.datasetManager.getStatus();
      if (statusResponse.success && statusResponse.data) {
        const status = statusResponse.data.status;
        const dmStatusElement = document.getElementById('dm-status');
        const dmCloseButton = document.getElementById('dm-close');
        const datasetSection = document.getElementById('dataset-section');
        
        if (dmStatusElement) {
          dmStatusElement.textContent = status;
        }
        
        // If dataset manager is initialized, show dataset section and enable buttons
        if (status === 'INITIALIZED') {
          if (dmCloseButton) dmCloseButton.disabled = false;
          if (datasetSection) datasetSection.style.display = 'block';
          
          // Trigger dataset list refresh (refreshDatasetList is defined in app.js)
          if (typeof refreshDatasetList === 'function') {
            await refreshDatasetList();
          }
        }
      }
    } catch (error) {
      console.error('Failed to restore dataset manager state:', error);
    }
  },
  
  /**
   * Restore security checkbox states from backend
   */
  async restoreSecurityState() {
    try {
      // Restore server security state
      const serverConfig = await API.security.getServer();
      if (serverConfig.success) {
        const serverCheckbox = document.getElementById('server-security');
        const serverPathInput = document.getElementById('server-security-path');
        
        if (serverCheckbox) {
          serverCheckbox.checked = serverConfig.data.enabled;
        }
        if (serverPathInput) {
          serverPathInput.value = serverConfig.data.serverSecurityPath || '';
          serverPathInput.style.display = serverConfig.data.enabled ? 'block' : 'none';
        }
      }
      
      // Restore client security state
      const clientConfig = await API.security.getClient();
      if (clientConfig.success) {
        const clientCheckbox = document.getElementById('client-security');
        const clientPathInput = document.getElementById('client-security-path');
        
        if (clientCheckbox) {
          clientCheckbox.checked = clientConfig.data.enabled;
        }
        if (clientPathInput) {
          clientPathInput.value = clientConfig.data.clientSecurityPath || '';
          clientPathInput.style.display = clientConfig.data.enabled ? 'block' : 'none';
        }
      }
    } catch (error) {
      console.error('Failed to restore security state:', error);
    }
  },
  


  /**
   * Update kit UI based on current state
   */
  updateKitUI() {
    const kitInfo = this.state.kitInfo;
    const kitInfoDiv = document.getElementById('kit-info');
    const kitPathInput = document.getElementById('kit-path');
    const licenseGroup = document.getElementById('license-group');

    // Always update input field to reflect current state (even if empty)
    if (kitPathInput) {
      kitPathInput.value = kitInfo.kitPath || '';
    }

    if (kitInfo.kitPath) {
      const kitType = kitInfo.isEE ? 'Enterprise Kit' : 'Open source Kit';
      kitInfoDiv.textContent = `Using ${kitType}`;
      kitInfoDiv.className = 'info-message';

      if (kitInfo.isEE) {
        licenseGroup.style.display = 'block';
        const licensePathInput = document.getElementById('license-path');
        if (licensePathInput) {
          licensePathInput.value = kitInfo.licensePath || '';
        }
        
        // Show EE-only sections on runtime tab
        const clientSecuritySection = document.getElementById('client-security-section');
        const datasetManagerSection = document.getElementById('dataset-manager-section');
        const diskResourceGroup = document.getElementById('cm-disk-resource-group');
        
        if (clientSecuritySection) clientSecuritySection.style.display = 'block';
        if (datasetManagerSection) datasetManagerSection.style.display = 'block';
        if (diskResourceGroup) diskResourceGroup.style.display = 'block';
      }
    } else {
      // Clear info message if no kit path
      if (kitInfoDiv) {
        kitInfoDiv.textContent = '';
        kitInfoDiv.className = '';
      }
    }
  },

  /**
   * Update config UI based on current state
   */
  updateConfigUI() {
    const config = this.state.config;
    
    // Set base location from config
    const baseLocation = document.getElementById('base-location');
    if (config.baseLocation) {
      baseLocation.value = config.baseLocation;
    }
    
    // Set server security path from config
    const serverSecurityPath = document.getElementById('server-security-path');
    if (serverSecurityPath && config.serverSecurityPath) {
      serverSecurityPath.value = config.serverSecurityPath;
      const serverSecurityCheckbox = document.getElementById('server-security');
      if (serverSecurityCheckbox) {
        serverSecurityCheckbox.checked = true;
        serverSecurityPath.style.display = 'block';
      }
    }
    
    // Set client security path from config
    const clientSecurityPath = document.getElementById('client-security-path');
    if (clientSecurityPath && config.clientSecurityPath) {
      clientSecurityPath.value = config.clientSecurityPath;
      const clientSecurityCheckbox = document.getElementById('client-security');
      if (clientSecurityCheckbox) {
        clientSecurityCheckbox.checked = true;
        clientSecurityPath.style.display = 'block';
      }
    }

    // Update plus-minus controls if they exist
    const offheapControl = document.getElementById('offheap-control');
    if (offheapControl) {
      offheapControl.value = config.offheapCount || 2;
      const offheapLabel = document.getElementById('offheap-control-label');
      if (offheapLabel) {
        offheapLabel.textContent = `${config.offheapCount || 2} offheap resources`;
      }
    }

    if (this.state.isEE) {
      document.getElementById('ee-config').style.display = 'block';
      
      const datarootControl = document.getElementById('dataroot-control');
      if (datarootControl) {
        datarootControl.value = config.dataRootCount || 2;
        const datarootLabel = document.getElementById('dataroot-control-label');
        if (datarootLabel) {
          datarootLabel.textContent = `${config.dataRootCount || 2} data roots`;
        }
      }
    }

    const stripeControl = document.getElementById('stripe-control');
    const serverControl = document.getElementById('server-control');
    
    if (stripeControl) {
      stripeControl.value = config.stripeCount || 2;
      const stripeLabel = document.getElementById('stripe-control-label');
      if (stripeLabel) {
        stripeLabel.textContent = `${config.stripeCount || 2} stripes`;
      }
    }
    
    if (serverControl) {
      serverControl.value = config.serverCount || 2;
      const serverLabel = document.getElementById('server-control-label');
      if (serverLabel) {
        serverLabel.textContent = `${config.serverCount || 2} servers per stripe`;
      }
    }
    
    document.getElementById('reconnect-slider').value = config.reconnectWindow || 120;
    document.getElementById('reconnect-label').textContent = `Reconnect window: ${config.reconnectWindow || 120} seconds`;

    // Update failover priority
    if (config.failoverPriorityMode === 'consistency') {
      document.querySelector('input[name="failover"][value="consistency"]').checked = true;
      const voterInput = document.getElementById('voter-count');
      const voterLabel = document.getElementById('voter-count-label');
      voterInput.style.display = 'block';
      voterInput.value = config.voterCount || 1;
      if (voterLabel) {
        voterLabel.style.display = 'block';
      }
    } else {
      document.querySelector('input[name="failover"][value="availability"]').checked = true;
    }

    this.updateOffheapGrid();
    this.updateDataRootGrid();
    this.updateServerGrid();
  },

  /**
   * Update offheap resource grid
   */
  updateOffheapGrid() {
    const offheapControl = document.getElementById('offheap-control');
    const count = offheapControl ? parseInt(offheapControl.value) : (this.state.config?.offheapCount || 2);
    const grid = document.getElementById('offheap-grid');
    grid.innerHTML = '';

    for (let i = 1; i <= count; i++) {
      const item = document.createElement('div');
      item.className = 'config-item config-item-horizontal';
      item.innerHTML = `
        <div class="config-field">
          <label>Name</label>
          <input type="text" value="offheap-${i}" data-offheap-name="${i}">
        </div>
        <div class="config-field">
          <label>Size (MB)</label>
          <input type="number" value="256" data-offheap-size="${i}">
        </div>
      `;
      grid.appendChild(item);
    }
  },

  /**
   * Update data root grid (EE only)
   */
  updateDataRootGrid() {
    if (!this.state.isEE) return;

    const datarootControl = document.getElementById('dataroot-control');
    const count = datarootControl ? parseInt(datarootControl.value) : (this.state.config?.dataRootCount || 2);
    const grid = document.getElementById('dataroot-grid');
    const baseLocation = document.getElementById('base-location').value;
    grid.innerHTML = '';

    // Add platform persistence if checked
    if (document.getElementById('platform-persistence').checked) {
      const item = document.createElement('div');
      item.className = 'config-item';
      item.innerHTML = `
        <label>ID</label>
        <input type="text" value="PLATFORM" readonly>
        <label>Location</label>
        <input type="text" value="${baseLocation}/data/platform" readonly>
      `;
      grid.appendChild(item);
    }

    // Add platform backup if checked
    if (document.getElementById('platform-backup').checked) {
      const item = document.createElement('div');
      item.className = 'config-item';
      item.innerHTML = `
        <label>ID</label>
        <input type="text" value="BACKUP" readonly>
        <label>Location</label>
        <input type="text" value="${baseLocation}/data/backup" readonly>
      `;
      grid.appendChild(item);
    }

    // Add data roots
    for (let i = 1; i <= count; i++) {
      const item = document.createElement('div');
      item.className = 'config-item';
      item.innerHTML = `
        <label>ID</label>
        <input type="text" value="dataroot-${i}" data-dataroot-id="dataroot-${i}">
        <label>Location</label>
        <input type="text" value="${baseLocation}/data/dataroot-${i}" data-dataroot-location="dataroot-${i}">
      `;
      grid.appendChild(item);
    }
  },

  /**
   * Update server configuration grid
   */
  updateServerGrid() {
    const stripeControl = document.getElementById('stripe-control');
    const serverControl = document.getElementById('server-control');
    
    // Fall back to config state if controls don't exist yet
    const stripeCount = stripeControl ? parseInt(stripeControl.value) : (this.state.config?.stripeCount || 2);
    const serverCount = serverControl ? parseInt(serverControl.value) : (this.state.config?.serverCount || 2);
    const baseLocation = document.getElementById('base-location').value;
    const grid = document.getElementById('server-grid');
    grid.innerHTML = '';

    for (let stripe = 1; stripe <= stripeCount; stripe++) {
      // Create stripe column container
      const stripeColumn = document.createElement('div');
      stripeColumn.className = 'stripe-column';
      
      // Add stripe header
      const stripeHeader = document.createElement('div');
      stripeHeader.className = 'stripe-header';
      stripeHeader.textContent = `Stripe ${stripe}`;
      stripeColumn.appendChild(stripeHeader);
      
      for (let server = 1; server <= serverCount; server++) {
        const item = document.createElement('div');
        item.className = 'config-item';
        const serverName = `stripe-${stripe}-server-${server}`;
        const clientPort = 9410 + (stripe - 1) * 10 + (server - 1);
        const groupPort = 9530 + (stripe - 1) * 10 + (server - 1);
        
        item.innerHTML = `
          <label>Server Name</label>
          <input type="text" value="${serverName}" data-server-name="${stripe}-${server}">
          <label>Logs location</label>
          <input type="text" value="${baseLocation}/logs" data-server-logs="${stripe}-${server}">
          <label>Host name</label>
          <input type="text" value="localhost" data-server-host="${stripe}-${server}">
          <div class="port-row">
            <div class="port-field">
              <label>Client port</label>
              <input type="text" value="${clientPort}" data-server-port="${stripe}-${server}">
            </div>
            <div class="port-field">
              <label>Group port</label>
              <input type="text" value="${groupPort}" data-server-group="${stripe}-${server}">
            </div>
          </div>
        `;
        stripeColumn.appendChild(item);
      }
      
      grid.appendChild(stripeColumn);
    }
  },

  /**
   * Update server controls grid (creates grid from scratch)
   */
  async updateServerControls() {
    const grid = document.getElementById('server-controls');
    grid.innerHTML = '';

    const stripeCount = parseInt(document.getElementById('stripe-control').value);
    const serverCount = parseInt(document.getElementById('server-control').value);

    for (let stripe = 1; stripe <= stripeCount; stripe++) {
      // Create stripe column container
      const stripeColumn = document.createElement('div');
      stripeColumn.className = 'stripe-column';
      
      // Add stripe header
      const stripeHeader = document.createElement('div');
      stripeHeader.className = 'stripe-header';
      stripeHeader.textContent = `Stripe ${stripe}`;
      stripeColumn.appendChild(stripeHeader);
      
      for (let server = 1; server <= serverCount; server++) {
        const serverName = `stripe-${stripe}-server-${server}`;
        const item = document.createElement('div');
        item.className = 'server-control-item';
        item.innerHTML = `
          <span class="server-name">${serverName}</span>
          <button class="btn btn-primary btn-start" data-server="${serverName}" data-stripe="${stripe}">START</button>
          <button class="btn btn-danger btn-kill" data-server="${serverName}" data-stripe="${stripe}" disabled>KILL</button>
          <button class="btn btn-primary btn-status" data-server="${serverName}" data-stripe="${stripe}" disabled>STATUS</button>
          <span class="status" data-pid="${serverName}"></span>
          <span class="status" data-state="${serverName}"></span>
        `;
        stripeColumn.appendChild(item);
      }
      
      grid.appendChild(stripeColumn);
    }

    // Add event listeners
    grid.querySelectorAll('.btn-start').forEach(btn => {
      btn.addEventListener('click', () => this.startServer(btn.dataset.server, btn.dataset.stripe));
    });

    grid.querySelectorAll('.btn-kill').forEach(btn => {
      btn.addEventListener('click', () => this.killServer(btn.dataset.server, btn.dataset.stripe));
    });
    
    // Restore server states after grid creation
    await this.restoreServerStates();
  },
  
  /**
   * Restore server states without regenerating grid
   */
  async restoreServerStates() {
    try {
      const serverList = await API.servers.list();
      console.log('API.servers.list() response:', serverList);
      
      if (serverList.data && serverList.data.servers) {
        console.log(`Restoring ${serverList.data.servers.length} running servers`);
        console.log('Server data:', JSON.stringify(serverList.data.servers, null, 2));
        
        serverList.data.servers.forEach(server => {
          console.log('Restoring server state:', server);
          
          // Update UI to show server as running
          const startBtn = document.querySelector(`.btn-start[data-server="${server.key}"]`);
          const killBtn = document.querySelector(`.btn-kill[data-server="${server.key}"]`);
          const statusBtn = document.querySelector(`.btn-status[data-server="${server.key}"]`);
          const pidElement = document.querySelector(`[data-pid="${server.key}"]`);
          const stateElement = document.querySelector(`[data-state="${server.key}"]`);
          
          if (startBtn) {
            startBtn.disabled = true;
            console.log(`Disabled START button for ${server.key}`);
          } else {
            console.log(`START button not found for ${server.key}`);
          }
          
          if (killBtn) {
            killBtn.disabled = false;
            console.log(`Enabled KILL button for ${server.key}`);
          } else {
            console.log(`KILL button not found for ${server.key}`);
          }
          
          if (statusBtn) {
            statusBtn.disabled = false;
            console.log(`Enabled STATUS button for ${server.key}`);
          } else {
            console.log(`STATUS button not found for ${server.key}`);
          }
          
          if (pidElement && server.pid) {
            pidElement.textContent = `PID: ${server.pid}`;
            console.log(`Set PID for ${server.key}: ${server.pid}`);
          }
          
          if (pidElement && server.pid) {
            pidElement.textContent = `PID: ${server.pid}`;
            console.log(`Set PID for ${server.key}: ${server.pid}`);
          } else {
            console.log(`PID element not found for ${server.key}`);
          }
          
          if (stateElement) {
            // Use the detected state from server discovery (e.g., DIAGNOSTIC MODE, ACTIVE, PASSIVE)
            stateElement.textContent = server.state || 'RUNNING';
            console.log(`Set state for ${server.key}: ${server.state || 'RUNNING'}`);
          } else {
            console.log(`State element not found for ${server.key}`);
          }
          
          // Reconnect to console WebSocket only if not already connected
          if (!Console.connections.has(server.consoleName)) {
            Console.addConsoleTab(server.consoleName, server.serverName);
            console.log(`Reconnected console for ${server.consoleName}`);
          } else {
            console.log(`Console already connected for ${server.consoleName}`);
          }
        });
      }
      
      // Update cluster control button states based on running servers
      this.updateClusterControlButtons(serverList.data.servers);
    } catch (error) {
      console.error('Failed to restore server states', error);
    }
  },
  
  /**
   * Helper: Check if a server is "not running"
   * A server is "not running" if status is STOPPED, TERMINATED, or empty
   */
  isServerNotRunning(state) {
    return !state || state === 'STOPPED' || state === 'TERMINATED' || state === '';
  },

  /**
   * Helper: Check if a server is "running"
   * A server is "running" if it's not in a "not running" state
   */
  isServerRunning(state) {
    return !this.isServerNotRunning(state);
  },

  /**
   * Helper: Check if a server is "configured"
   * A server is "configured" if status is ACTIVE-COORDINATOR or PASSIVE-STANDBY
   */
  isServerConfigured(state) {
    return state === 'ACTIVE-COORDINATOR' || state === 'PASSIVE-STANDBY';
  },

  /**
   * Helper: Check if a stripe is "active"
   * A stripe is "active" if it has at least one server with ACTIVE-COORDINATOR status
   */
  isStripeActive(servers, stripeNumber) {
    return servers.some(server => {
      const match = server.key.match(/stripe-(\d+)-server-\d+/);
      return match && parseInt(match[1]) === stripeNumber && server.state === 'ACTIVE-COORDINATOR';
    });
  },

  /**
   * Helper: Check if the cluster is "active"
   * The cluster is "active" if each stripe is active
   */
  isClusterActive(servers, stripeCount) {
    for (let stripe = 1; stripe <= stripeCount; stripe++) {
      if (!this.isStripeActive(servers, stripe)) {
        return false;
      }
    }
    return true;
  },

  /**
   * Update cluster control button states based on running servers
   */
  updateClusterControlButtons(runningServers) {
    // Try to get counts from config elements first
    const stripeCountEl = document.getElementById('stripe-control');
    const serverCountEl = document.getElementById('server-control');
    
    let stripeCount, serverCount, totalServers;
    
    if (stripeCountEl && serverCountEl) {
      // Use config tab values if available
      stripeCount = parseInt(stripeCountEl.value) || 1;
      serverCount = parseInt(serverCountEl.value) || 1;
      totalServers = stripeCount * serverCount;
    } else if (runningServers && runningServers.length > 0) {
      // Derive counts from running servers if config elements not available
      const stripes = new Set();
      const serversPerStripe = new Map();
      
      runningServers.forEach(server => {
        const match = server.key.match(/stripe-(\d+)-server-(\d+)/);
        if (match) {
          const stripe = parseInt(match[1]);
          stripes.add(stripe);
          serversPerStripe.set(stripe, (serversPerStripe.get(stripe) || 0) + 1);
        }
      });
      
      stripeCount = stripes.size;
      serverCount = Math.max(...serversPerStripe.values());
      totalServers = stripeCount * serverCount;
      console.log(`Derived cluster config from running servers: ${stripeCount} stripes, ${serverCount} servers per stripe`);
    } else {
      console.log('Cannot determine cluster configuration - no config elements and no running servers');
      return;
    }
    
    // Build complete server list: prioritize backend state over UI state
    // Start with all servers from UI grid (these are the expected servers)
    const allServerElements = document.querySelectorAll('[data-state]');
    const allServers = [];
    
    // Create map of running servers for O(1) lookup
    const runningServersMap = new Map();
    if (runningServers) {
      runningServers.forEach(server => {
        runningServersMap.set(server.key, server.state);
      });
    }
    
    // Build complete list: only use backend state for running servers
    // If server is not in backend list, treat as not running (empty state)
    allServerElements.forEach(el => {
      const serverName = el.getAttribute('data-state');
      const backendState = runningServersMap.get(serverName);
      
      // Only use backend state (source of truth)
      // If not in backend map, server is not running (empty state)
      const state = backendState !== undefined ? backendState : '';
      
      allServers.push({
        key: serverName,
        state: state
      });
    });
    
    console.log('All servers with states:', allServers.map(s => ({ key: s.key, state: s.state })));
    console.log('Running servers from backend:', runningServers ? runningServers.map(s => ({ key: s.key, state: s.state })) : 'none');
    
    // Check conditions per requirements
    const anyServerNotRunning = allServers.some(s => this.isServerNotRunning(s.state));
    const allServersInDiagnosticMode = allServers.length > 0 && 
                                       allServers.every(s => s.state === 'DIAGNOSTIC MODE');
    const anyServerRunning = allServers.some(s => this.isServerRunning(s.state));
    const clusterActive = this.isClusterActive(allServers, stripeCount);
    
    console.log(`Button state conditions:
      - Any server not running: ${anyServerNotRunning}
      - All servers in DIAGNOSTIC MODE: ${allServersInDiagnosticMode}
      - Any server running: ${anyServerRunning}
      - Cluster active: ${clusterActive}`);
    
    // Apply button states per requirements
    const startAllButton = document.getElementById('start-all-servers');
    if (startAllButton) {
      startAllButton.disabled = !anyServerNotRunning;
    }
    
    const configureButton = document.getElementById('configure-cluster');
    if (configureButton) {
      configureButton.disabled = !allServersInDiagnosticMode;
    }
    
    const backupButton = document.getElementById('backup-cluster');
    if (backupButton) {
      backupButton.disabled = !clusterActive;
    }
    
    const dumpButton = document.getElementById('dump-cluster');
    if (dumpButton) {
      dumpButton.disabled = !anyServerRunning;
    }
    
    const statusButton = document.getElementById('status-cluster');
    if (statusButton) {
      statusButton.disabled = !anyServerRunning;
    }
    
    const stopButton = document.getElementById('stop-cluster');
    if (stopButton) {
      stopButton.disabled = !anyServerRunning;
    }
  },

  /**
   * Start a server
   */
  async startServer(serverName, stripeNumber) {
    try {
      const clusterName = document.getElementById('cluster-name').value || 'MyCluster';
      
      // Parse server name to extract stripe and server numbers
      // Format: stripe-X-server-Y
      const parts = serverName.split('-');
      const stripe = parseInt(parts[1]);
      const server = parseInt(parts[3]);
      
      // Calculate client port: 9410 + (stripe-1)*10 + (server-1)
      const clientPort = String(9410 + (stripe - 1) * 10 + (server - 1));
      const stripeName = `stripe-${stripe}`;
      const hostname = 'localhost';
      
      console.log(`Starting server: ${serverName}, stripe: ${stripeName}, port: ${clientPort}`);
      
      const result = await API.servers.start(serverName, stripeName, hostname, clientPort, clusterName);
      this.showNotification(result.message || 'Server start initiated', 'success');
      
      // Create console tab and connect to WebSocket for this server
      if (result.data && result.data.consoleName) {
        const consoleName = result.data.consoleName;
        Console.addConsoleTab(consoleName, serverName);
      }
      
      // Update button states - use serverName as it matches the data-server attribute
      const startBtn = document.querySelector(`.btn-start[data-server="${serverName}"]`);
      const killBtn = document.querySelector(`.btn-kill[data-server="${serverName}"]`);
      const statusBtn = document.querySelector(`.btn-status[data-server="${serverName}"]`);
      
      if (startBtn) startBtn.disabled = true;
      if (killBtn) killBtn.disabled = false;
      if (statusBtn) statusBtn.disabled = false;
      
      // Update status display elements
      const pidElement = document.querySelector(`[data-pid="${serverName}"]`);
      const stateElement = document.querySelector(`[data-state="${serverName}"]`);
      if (stateElement) stateElement.textContent = 'STARTING';
      
      // Update cluster control buttons after starting server
      const startServerList = await API.servers.list();
      if (startServerList.data && startServerList.data.servers) {
        this.updateClusterControlButtons(startServerList.data.servers);
      }
      
    } catch (error) {
      this.showNotification(`Failed to start server: ${error.message}`, 'error');
      console.error('Server start error:', error);
    }
  },

  /**
   * Kill a server
   */
  async killServer(serverName, stripeName) {
    try {
      const result = await API.servers.kill(serverName, `stripe-${stripeName}`);
      this.showNotification(result.message || 'Server killed', 'warning');
      
      // Update button states
      const startBtn = document.querySelector(`.btn-start[data-server="${serverName}"]`);
      const killBtn = document.querySelector(`.btn-kill[data-server="${serverName}"]`);
      const statusBtn = document.querySelector(`.btn-status[data-server="${serverName}"]`);
      
      if (startBtn) startBtn.disabled = false;
      if (killBtn) killBtn.disabled = true;
      if (statusBtn) statusBtn.disabled = true;
      
      // Update cluster control buttons after killing server
      const killServerList = await API.servers.list();
      if (killServerList.data && killServerList.data.servers) {
        this.updateClusterControlButtons(killServerList.data.servers);
      }
      
      if (startBtn) startBtn.disabled = false;
      if (killBtn) killBtn.disabled = true;
      if (statusBtn) statusBtn.disabled = true;
      
      // Update status display
      const pidElement = document.querySelector(`[data-pid="${serverName}"]`);
      const stateElement = document.querySelector(`[data-state="${serverName}"]`);
      if (pidElement) pidElement.textContent = '';
      if (stateElement) stateElement.textContent = 'TERMINATED';
      
      // Disconnect WebSocket but keep console tab for review
      Console.disconnect(serverName);
      
    } catch (error) {
      this.showNotification(`Failed to kill server: ${error.message}`, 'error');
    }
  }
};



/**
 * Utility function to create a plus-minus control
 * @param {string} id - The ID for the control
 * @param {string} label - The label text
 * @param {number} value - Initial value
 * @param {number} min - Minimum value
 * @param {number} max - Maximum value
 * @param {function} onChange - Callback function when value changes
 * @returns {HTMLElement} The plus-minus control element
 */
function createPlusMinusControl(id, label, value, min, max, onChange) {
  const container = document.createElement('div');
  container.className = 'plus-minus-control';
  container.id = `${id}-control`;
  
  const labelElement = document.createElement('label');
  labelElement.textContent = label;
  labelElement.id = `${id}-label`;
  
  const controlButtons = document.createElement('div');
  controlButtons.className = 'control-buttons';
  
  const minusBtn = document.createElement('button');
  minusBtn.className = 'btn-minus';
  minusBtn.textContent = '−';
  minusBtn.type = 'button';
  
  const input = document.createElement('input');
  input.type = 'number';
  input.id = id;
  input.value = value;
  input.min = min;
  input.max = max;
  input.readOnly = true;
  
  const plusBtn = document.createElement('button');
  plusBtn.className = 'btn-plus';
  plusBtn.textContent = '+';
  plusBtn.type = 'button';
  
  // Update button states
  const updateButtons = () => {
    const currentValue = parseInt(input.value);
    minusBtn.disabled = currentValue <= min;
    plusBtn.disabled = currentValue >= max;
  };
  
  // Minus button handler
  minusBtn.addEventListener('click', () => {
    const currentValue = parseInt(input.value);
    if (currentValue > min) {
      input.value = currentValue - 1;
      updateButtons();
      if (onChange) onChange(parseInt(input.value));
    }
  });
  
  // Plus button handler
  plusBtn.addEventListener('click', () => {
    const currentValue = parseInt(input.value);
    if (currentValue < max) {
      input.value = currentValue + 1;
      updateButtons();
      if (onChange) onChange(parseInt(input.value));
    }
  });
  
  controlButtons.appendChild(minusBtn);
  controlButtons.appendChild(input);
  controlButtons.appendChild(plusBtn);
  
  container.appendChild(labelElement);
  container.appendChild(controlButtons);
  
  updateButtons();
  
  return container;
}

/**
 * Main application entry point.
 * Initializes all modules and sets up event handlers.
 */

// Initialize application when DOM is ready
document.addEventListener('DOMContentLoaded', () => {
  console.log('TinyPounder initializing...');
  
  // Initialize modules
  UI.init();
  Console.init();
  
  // Connect to main console
  Console.connect('main');
  
  // Setup event handlers
  setupKitHandlers();
  setupConfigHandlers();
  setupControlHandlers();
  setupRuntimeHandlers();
  
  console.log('TinyPounder initialized');
});

/**
 * Initialize plus-minus controls for stripe and server counts
 */
function initializePlusMinusControls() {
  // Get containers
  const stripeContainer = document.getElementById('stripe-control-container');
  const serverContainer = document.getElementById('server-control-container');
  
  // Skip if containers don't exist or already have controls
  if (!stripeContainer || !serverContainer) {
    console.warn('Plus-minus control containers not found');
    return;
  }
  
  if (document.getElementById('stripe-control')) {
    console.log('Plus-minus controls already initialized');
    return;
  }
  
  // Get initial values from config or defaults
  const initialStripeCount = UI.state.config?.stripeCount || 2;
  const initialServerCount = UI.state.config?.serverCount || 2;
  
  console.log('Initializing plus-minus controls:', { initialStripeCount, initialServerCount });
  
  // Clear containers
  stripeContainer.innerHTML = '';
  serverContainer.innerHTML = '';
  
  // Create stripe control
  const stripeControl = createPlusMinusControl(
    'stripe-control',
    `${initialStripeCount} stripes`,
    initialStripeCount,
    1,
    5,
    async (value) => {
      // Update label
      document.getElementById('stripe-control-label').textContent = `${value} stripes`;
      // Update server grid
      UI.updateServerGrid();
      // Save to backend
      try {
        const serverCount = parseInt(document.getElementById('server-control').value);
        const reconnectWindow = parseInt(document.getElementById('reconnect-slider').value);
        await API.config.setServers(value, serverCount, reconnectWindow);
      } catch (error) {
        console.error('Failed to update stripe count', error);
      }
    }
  );
  
  // Create server control
  const serverControl = createPlusMinusControl(
    'server-control',
    `${initialServerCount} servers per stripe`,
    initialServerCount,
    1,
    5,
    async (value) => {
      // Update label
      document.getElementById('server-control-label').textContent = `${value} servers per stripe`;
      // Update server grid
      UI.updateServerGrid();
      // Save to backend
      try {
        const stripeCount = parseInt(document.getElementById('stripe-control').value);
        const reconnectWindow = parseInt(document.getElementById('reconnect-slider').value);
        await API.config.setServers(stripeCount, value, reconnectWindow);
      } catch (error) {
        console.error('Failed to update server count', error);
      }
    }
  );
  
  // Append controls to containers
  stripeContainer.appendChild(stripeControl);
  serverContainer.appendChild(serverControl);
  
  console.log('Plus-minus controls initialized successfully');
}


/**
 * Initialize plus-minus control for offheap resources
 */
function initializeOffheapControl() {
  const container = document.getElementById('offheap-control-container');
  
  if (!container) {
    console.warn('Offheap control container not found');
    return;
  }
  
  if (document.getElementById('offheap-control')) {
    console.log('Offheap control already initialized');
    return;
  }
  
  const initialCount = UI.state.config?.offheapCount || 2;
  
  console.log('Initializing offheap control:', { initialCount });
  
  container.innerHTML = '';
  
  const offheapControl = createPlusMinusControl(
    'offheap-control',
    `${initialCount} offheap resources`,
    initialCount,
    1,
    5,
    async (value) => {
      document.getElementById('offheap-control-label').textContent = `${value} offheap resources`;
      UI.updateOffheapGrid();
      try {
        await API.config.setOffheap(value);
      } catch (error) {
        console.error('Failed to update offheap count', error);
      }
    }
  );
  
  container.appendChild(offheapControl);
  console.log('Offheap control initialized successfully');
}

/**
 * Initialize plus-minus control for data roots (EE only)
 */
function initializeDatarootControl() {
  const container = document.getElementById('dataroot-control-container');
  
  if (!container) {
    console.log('Dataroot control container not found (may not be EE)');
    return;
  }
  
  if (document.getElementById('dataroot-control')) {
    console.log('Dataroot control already initialized');
    return;
  }
  
  const initialCount = UI.state.config?.dataRootCount || 2;
  
  console.log('Initializing dataroot control:', { initialCount });
  
  container.innerHTML = '';
  
  const datarootControl = createPlusMinusControl(
    'dataroot-control',
    `${initialCount} data roots`,
    initialCount,
    1,
    5,
    async (value) => {
      document.getElementById('dataroot-control-label').textContent = `${value} data roots`;
      UI.updateDataRootGrid();
      try {
        await API.config.setDataRoots(value);
      } catch (error) {
        console.error('Failed to update data root count', error);
      }
    }
  );
  
  container.appendChild(datarootControl);
  console.log('Dataroot control initialized successfully');
}


/**
 * Setup Kit tab event handlers
 */
function setupKitHandlers() {
  document.getElementById('set-kit-path').addEventListener('click', async () => {
    const kitPath = document.getElementById('kit-path').value.trim();
    if (!kitPath) {
      UI.showNotification('Please enter a kit path', 'warning');
      return;
    }
    
    try {
      const result = await API.kit.setPath(kitPath);
      UI.state.kitInfo = result.data;
      UI.state.isEE = result.data.isEE;
      UI.updateKitUI();
      UI.showNotification(result.message || 'Kit path set successfully', 'success');
      
      // Reload config to update UI
      const config = await API.config.getCurrent();
      UI.state.config = config.data;
      UI.updateConfigUI();
    } catch (error) {
      UI.showNotification(`Failed to set kit path: ${error.message}`, 'error');
    }
  });
  
  document.getElementById('set-license-path').addEventListener('click', async () => {
    const licensePath = document.getElementById('license-path').value.trim();
    if (!licensePath) {
      UI.showNotification('Please enter a license path', 'warning');
      return;
    }
    
    try {
      const result = await API.kit.setLicense(licensePath);
      UI.showNotification(result.message || 'License path set successfully', 'success');
    } catch (error) {
      UI.showNotification(`Failed to set license path: ${error.message}`, 'error');
    }
  });
}

/**
 * Setup Config tab event handlers
 */
function setupConfigHandlers() {
  // Initialize plus-minus controls
  initializePlusMinusControls();
  initializeOffheapControl();
  initializeDatarootControl();
  
  // Platform persistence/backup checkboxes
  ['platform-persistence', 'platform-backup'].forEach(id => {
    const checkbox = document.getElementById(id);
    if (checkbox) {
      checkbox.addEventListener('change', () => UI.updateDataRootGrid());
    }
  });
  
  // Security checkbox
  const serverSecurityCheckbox = document.getElementById('server-security');
  if (serverSecurityCheckbox) {
    serverSecurityCheckbox.addEventListener('change', async (e) => {
      const pathInput = document.getElementById('server-security-path');
      pathInput.style.display = e.target.checked ? 'block' : 'none';
      
      // Save both enabled state and path
      try {
        await API.security.setServer(e.target.checked, pathInput.value.trim());
        console.log('Server security setting saved');
      } catch (error) {
        console.error('Failed to save server security:', error);
      }
    });
  }
  
  // Server security path input handler
  const serverSecurityPathInput = document.getElementById('server-security-path');
  if (serverSecurityPathInput) {
    serverSecurityPathInput.addEventListener('blur', async (e) => {
      const path = e.target.value.trim();
      const isEnabled = serverSecurityCheckbox?.checked || false;
      
      if (path) {
        try {
          await API.security.setServer(isEnabled, path);
          console.log('Server security path saved:', path);
        } catch (error) {
          console.error('Failed to save server security path:', error);
        }
      }
    });
  }
  
  // Failover priority
  document.querySelectorAll('input[name="failover"]').forEach(radio => {
    radio.addEventListener('change', (e) => {
      const voterInput = document.getElementById('voter-count');
      const voterLabel = document.getElementById('voter-count-label');
      const isConsistency = e.target.value === 'consistency';
      
      voterInput.style.display = isConsistency ? 'block' : 'none';
      if (voterLabel) {
        voterLabel.style.display = isConsistency ? 'block' : 'none';
      }
      
      const failoverPriority = isConsistency ? 
        `consistency:${voterInput.value || 1}` : 'availability';
      API.config.setFailover(failoverPriority).catch(console.error);
    });
  });
  
  // Voter count input - save on change
  const voterCountInput = document.getElementById('voter-count');
  if (voterCountInput) {
    voterCountInput.addEventListener('change', async (e) => {
      const voterCount = e.target.value || 1;
      const failoverPriority = `consistency:${voterCount}`;
      try {
        await API.config.setFailover(failoverPriority);
        console.log('Voter count saved:', voterCount);
      } catch (error) {
        console.error('Failed to save voter count:', error);
      }
    });
  }
  
  // Initialize plus-minus controls for stripe and server counts
  initializePlusMinusControls();
  
  // Reconnect window slider
  document.getElementById('reconnect-slider').addEventListener('input', (e) => {
    const seconds = e.target.value;
    document.getElementById('reconnect-label').textContent = `Reconnect window: ${seconds} seconds`;
  });
  
  // Save reconnect window on slider change
  document.getElementById('reconnect-slider').addEventListener('change', async () => {
    try {
      const stripeCount = parseInt(document.getElementById('stripe-control').value);
      const serverCount = parseInt(document.getElementById('server-control').value);
      const reconnectWindow = parseInt(document.getElementById('reconnect-slider').value);
      await API.config.setServers(stripeCount, serverCount, reconnectWindow);
    } catch (error) {
      console.error('Failed to update server config', error);
    }
  });
  
  // Base location input - save on blur
  const baseLocationInput = document.getElementById('base-location');
  console.log('Setting up base-location blur handler, element:', baseLocationInput);
  
  if (baseLocationInput) {
    baseLocationInput.addEventListener('blur', async (e) => {
      const baseLocation = e.target.value.trim();
      console.log('Base location blur event:', baseLocation);
      if (!baseLocation) {
        console.log('Base location is empty, skipping save');
        return;
      }
      
      try {

        console.log('Calling API.config.setBaseLocation with:', baseLocation);
        const result = await API.config.setBaseLocation(baseLocation);
        console.log('API response:', result);
        UI.showNotification(result.message || 'Base location saved', 'success');
        // Update grids to reflect new base location
        UI.updateDataRootGrid();
        UI.updateServerGrid();
      } catch (error) {
        console.error('Failed to save base location:', error);
        UI.showNotification(`Failed to save base location: ${error.message}`, 'error');
      }
    });
    console.log('Base location blur handler attached successfully');
  } else {
    console.error('base-location element not found!');
  }
  
  // Base location trash button
  document.getElementById('trash-data').addEventListener('click', async () => {
    const baseLocation = document.getElementById('base-location').value.trim();
    if (!baseLocation) {
      UI.showNotification('Please enter a base location', 'warning');
      return;
    }
    
    if (!confirm(`Are you sure you want to delete ${baseLocation}?`)) {
      return;
    }
    
    try {
      const result = await API.config.deleteBaseLocation(baseLocation);
      UI.showNotification(result.message || 'Base location deleted', 'success');
    } catch (error) {
      UI.showNotification(`Failed to delete base location: ${error.message}`, 'error');
    }
  });
  
  // Generate config button
  document.getElementById('generate-config').addEventListener('click', async () => {
    try {
      // Collect offheap resources from UI
      const offheapResources = [];
      document.querySelectorAll('[data-offheap-name]').forEach((input) => {
        const nameInput = input;
        const index = nameInput.getAttribute('data-offheap-name');
        const sizeInput = document.querySelector(`[data-offheap-size="${index}"]`);
        if (nameInput && sizeInput) {
          offheapResources.push({
            name: nameInput.value,
            size: parseInt(sizeInput.value) || 256
          });
        }
      });
      
      // Collect dataroot paths from UI
      const datarootPaths = [];
      document.querySelectorAll('[data-dataroot-id]').forEach((input) => {
        const id = input.getAttribute('data-dataroot-id');
        const locationInput = document.querySelector(`[data-dataroot-location="${id}"]`);
        if (id && locationInput && id !== 'PLATFORM') {
          datarootPaths.push({
            id: id,
            location: locationInput.value
          });
        }
      });
      
      // Collect server configurations from UI
      const servers = [];
      document.querySelectorAll('[data-server-name]').forEach((input) => {
        const key = input.getAttribute('data-server-name');
        const logsInput = document.querySelector(`[data-server-logs="${key}"]`);
        const hostInput = document.querySelector(`[data-server-host="${key}"]`);
        const portInput = document.querySelector(`[data-server-port="${key}"]`);
        const groupInput = document.querySelector(`[data-server-group="${key}"]`);
        
        if (logsInput && hostInput && portInput && groupInput) {
          servers.push({
            name: input.value,
            logs: logsInput.value,
            host: hostInput.value,
            port: parseInt(portInput.value),
            groupPort: parseInt(groupInput.value)
          });
        }
      });
      
      // Collect platform flags
      const platformPersistence = document.getElementById('platform-persistence').checked;
      const platformBackup = document.getElementById('platform-backup').checked;
      
      console.log('Collected config data:', {
        offheapResources,
        datarootPaths,
        servers,
        platformPersistence,
        platformBackup
      });
      
      const result = await API.config.generate(offheapResources, datarootPaths, servers, platformPersistence, platformBackup);
      UI.showNotification(result.message || 'Config files generated', 'success');
      
      // Load preview
      const preview = await API.config.getPreview();
      // The API returns the config text directly in the 'data' field
      const configText = preview.data || preview.message || 'Config preview not available';
      document.getElementById('config-preview').value = configText;
    } catch (error) {
      UI.showNotification(`Failed to generate config: ${error.message}`, 'error');
    }
  });
}

/**
 * Setup Control tab event handlers
 */
function setupControlHandlers() {
  // Update server controls when tab is shown - only restore state, don't regenerate grid
  document.querySelector('[data-tab="control"]').addEventListener('click', async () => {
    // Only create grid if it doesn't exist
    const grid = document.getElementById('server-controls');
    if (!grid.hasChildNodes()) {
      await UI.updateServerControls();
    } else {
      // Grid exists, just restore server states
      await UI.restoreServerStates();
    }
  });
  
  // Start all servers
  document.getElementById('start-all-servers').addEventListener('click', async () => {
    UI.showNotification('Starting all servers...', 'info');
    
    // Ensure server controls are rendered first
    UI.updateServerControls();
    
    // Wait for DOM to update
    await new Promise(resolve => setTimeout(resolve, 100));
    
    try {
      const stripeCount = parseInt(document.getElementById('stripe-control').value);
      const serverCount = parseInt(document.getElementById('server-control').value);
      const clusterName = document.getElementById('cluster-name').value.trim() || 'MyCluster';
      
      // Get list of currently running servers
      const serverList = await API.servers.list();
      const runningServerKeys = new Set();
      if (serverList.data && serverList.data.servers) {
        serverList.data.servers.forEach(server => {
          runningServerKeys.add(server.key);
        });
      }
      
      let successCount = 0;
      let failCount = 0;
      let skippedCount = 0;
      const errors = [];
      
      // Start all servers in all stripes (skip already running servers)
      for (let stripe = 1; stripe <= stripeCount; stripe++) {
        for (let server = 1; server <= serverCount; server++) {
          const stripeName = `stripe-${stripe}`;
          const serverName = `stripe-${stripe}-server-${server}`;
          const hostname = 'localhost';
          const clientPort = String(9410 + (stripe - 1) * 10 + (server - 1));
          
          // Skip if server is already running
          if (runningServerKeys.has(serverName)) {
            console.log(`Skipping ${serverName} - already running`);
            skippedCount++;
            continue;
          }
          
          try {
            const result = await API.servers.start(serverName, stripeName, hostname, clientPort, clusterName);
            
            // Check if start was successful
            if (result.success) {
              successCount++;
              
              // Create console tab and connect for this server
              const consoleName = result.data?.consoleName;
              if (consoleName) {
                Console.addConsoleTab(consoleName, serverName);
              }
            } else {
              console.warn(`Failed to start ${serverName}: ${result.message}`);
              continue; // Skip button updates for failed start
            }
            
            // Update button states for this server
            const startBtn = document.querySelector(`.btn-start[data-server="${serverName}"]`);
            const killBtn = document.querySelector(`.btn-kill[data-server="${serverName}"]`);
            const statusBtn = document.querySelector(`.btn-status[data-server="${serverName}"]`);
            
            console.log(`Updating buttons for ${serverName}:`, {startBtn, killBtn, statusBtn});
            
            if (startBtn) {
              startBtn.disabled = true;
              console.log(`Disabled START button for ${serverName}`);
            }
            if (killBtn) {
              killBtn.disabled = false;
              console.log(`Enabled KILL button for ${serverName}`);
            }
            if (statusBtn) {
              statusBtn.disabled = false;
              console.log(`Enabled STATUS button for ${serverName}`);
            }
            
            // Update status display
            const stateElement = document.querySelector(`[data-state="${serverName}"]`);
            if (stateElement) {
              stateElement.textContent = 'STARTING';
              console.log(`Set state to STARTING for ${serverName}`);
            }
          } catch (error) {
            failCount++;
            errors.push(`${serverName}: ${error.message}`);
            console.error(`Failed to start ${serverName}:`, error);
          }
        }
      }
      
      // Update cluster control buttons after starting all servers
      const updatedServerList = await API.servers.list();
      if (updatedServerList.data && updatedServerList.data.servers) {
        UI.updateClusterControlButtons(updatedServerList.data.servers);
      }
      
      // Show summary
      const message = [];
      if (successCount > 0) message.push(`${successCount} started`);
      if (skippedCount > 0) message.push(`${skippedCount} already running`);
      if (failCount > 0) message.push(`${failCount} failed`);
      
      if (failCount === 0) {
        UI.showNotification(message.join(', '), 'success');
      } else {
        UI.showNotification(message.join(', ') + '. Check console for details.', 'warning');
        errors.forEach(err => Console.appendToConsole('main', `ERROR: ${err}\n`));
      }
      
    } catch (error) {
      UI.showNotification(`Failed to start servers: ${error.message}`, 'error');
    }
  });
  
  // Cluster control buttons
  document.getElementById('configure-cluster').addEventListener('click', async () => {
    const clusterName = document.getElementById('cluster-name').value.trim();
    if (!clusterName) {
      UI.showNotification('Please enter a cluster name', 'warning');
      return;
    }
    
    try {
      const result = await API.cluster.configure(clusterName);
      UI.showNotification(result.message || 'Cluster configured successfully', 'success');
      
      // Refresh server states and button states after successful configuration
      await UI.restoreServerStates();
      
      // Update cluster control buttons after configuration
      const configureServerList = await API.servers.list();
      if (configureServerList.data && configureServerList.data.servers) {
        UI.updateClusterControlButtons(configureServerList.data.servers);
      }
    } catch (error) {
      // Check if this is a "no config files" error
      if (error.data && error.data.errorCode === 'NO_CONFIG_FILES') {
        const goToConfig = confirm(
          'No configuration files found.\n\n' +
          'Would you like to go to the Server Configuration tab to generate them?'
        );
        if (goToConfig) {
          // Switch to config tab
          document.querySelectorAll('.tab-button').forEach(btn => btn.classList.remove('active'));
          document.querySelectorAll('.tab-pane').forEach(pane => pane.classList.remove('active'));
          
          document.querySelector('[data-tab="config-tab"]').classList.add('active');
          document.getElementById('config-tab').classList.add('active');
        }
      } else {
        UI.showNotification(`Failed to configure cluster: ${error.message}`, 'error');
      }
    }
  });
  
  document.getElementById('backup-cluster').addEventListener('click', async () => {
    const clusterName = document.getElementById('cluster-name').value.trim();
    try {
      const result = await API.cluster.backup(clusterName);
      UI.showNotification(result.message || 'Cluster backup initiated', 'success');
      
      // Update cluster control buttons after backup operation
      const backupServerList = await API.servers.list();
      if (backupServerList.data && backupServerList.data.servers) {
        UI.updateClusterControlButtons(backupServerList.data.servers);
      }
    } catch (error) {
      UI.showNotification(`Failed to backup cluster: ${error.message}`, 'error');
    }
  });
  
  document.getElementById('dump-cluster').addEventListener('click', async () => {
    const clusterName = document.getElementById('cluster-name').value.trim();
    try {
      const result = await API.cluster.dump(clusterName);
      UI.showNotification(result.message || 'Cluster dump initiated', 'success');
      
      // Update cluster control buttons after dump operation
      const dumpServerList = await API.servers.list();
      if (dumpServerList.data && dumpServerList.data.servers) {
        UI.updateClusterControlButtons(dumpServerList.data.servers);
      }
    } catch (error) {
      UI.showNotification(`Failed to dump cluster: ${error.message}`, 'error');
    }
  });
  
  document.getElementById('stop-cluster').addEventListener('click', async () => {
    const clusterName = document.getElementById('cluster-name').value.trim();
    if (!confirm(`Are you sure you want to stop cluster ${clusterName}?`)) {
      return;
    }
    
    try {
      const result = await API.cluster.stop(clusterName);
      UI.showNotification(result.message || 'Cluster stopped successfully', 'success');
      
      // Update UI to reflect stopped servers
      if (result.data && result.data.stoppedServers) {
        result.data.stoppedServers.forEach(serverName => {
          const startBtn = document.querySelector(`.btn-start[data-server="${serverName}"]`);
          const killBtn = document.querySelector(`.btn-kill[data-server="${serverName}"]`);
          const statusBtn = document.querySelector(`.btn-status[data-server="${serverName}"]`);
          const pidElement = document.querySelector(`[data-pid="${serverName}"]`);
          const stateElement = document.querySelector(`[data-state="${serverName}"]`);
          
          if (startBtn) startBtn.disabled = false;
          if (killBtn) killBtn.disabled = true;
          if (statusBtn) statusBtn.disabled = true;
          if (pidElement) pidElement.textContent = '';
          if (stateElement) stateElement.textContent = 'STOPPED';
        });
      }
      
      // Update cluster control buttons after stopping servers
      const stopServerList = await API.servers.list();
      if (stopServerList.data && stopServerList.data.servers) {
        UI.updateClusterControlButtons(stopServerList.data.servers);
      }
    } catch (error) {
      UI.showNotification(`Failed to stop cluster: ${error.message}`, 'error');
    }
  });
  
  document.getElementById('status-cluster').addEventListener('click', async () => {
    try {
      const result = await API.cluster.getStatus();
      Console.appendToConsole('main', `\n=== Cluster Status ===\n${JSON.stringify(result.data, null, 2)}\n`);
    } catch (error) {
      UI.showNotification(`Failed to get cluster status: ${error.message}`, 'error');
    }
  });
}

/**
 * Check cache manager status on page load and restore UI state
 */
async function checkCacheManagerStatus() {
  try {
    const result = await API.cacheManager.getStatus();
    
    if (result.data && result.data.status) {
      const status = result.data.status;
      document.getElementById('cm-status').textContent = status;
      
      if (status === 'NO CACHE MANAGER' || status === 'UNINITIALIZED') {
        // Disable buttons when no cache manager exists
        document.getElementById('cm-show-config').disabled = true;
        document.getElementById('cm-close').disabled = true;
        document.getElementById('cm-destroy').disabled = true;
        document.getElementById('cache-section').style.display = 'none';
      } else {
        // Cache manager is initialized, restore UI state
        document.getElementById('cm-show-config').disabled = false;
        document.getElementById('cm-close').disabled = false;
        document.getElementById('cm-destroy').disabled = false;
        document.getElementById('cache-section').style.display = 'block';
        
        // Load existing caches
        await refreshCacheList();
      }
    }
  } catch (error) {
    // Cache manager not initialized or error occurred
    console.log('Cache manager not initialized or error checking status:', error.message);
  }
}

/**
 * Setup Runtime tab event handlers
 */
function setupRuntimeHandlers() {
  // Check cache manager status on page load
  checkCacheManagerStatus();
  
  // Client security checkbox
  const clientSecurityCheckbox = document.getElementById('client-security');
  if (clientSecurityCheckbox) {
    clientSecurityCheckbox.addEventListener('change', async (e) => {
      const pathInput = document.getElementById('client-security-path');
      if (pathInput) {
        pathInput.style.display = e.target.checked ? 'block' : 'none';
        
        // Save both enabled state and path
        try {
          await API.security.setClient(e.target.checked, pathInput.value.trim());
          console.log('Client security setting saved');
        } catch (error) {
          console.error('Failed to save client security:', error);
        }
      }
    });
  }
  
  // Client security path input - save on blur
  const clientSecurityPathInput = document.getElementById('client-security-path');
  if (clientSecurityPathInput) {
    clientSecurityPathInput.addEventListener('blur', async (e) => {
      const clientSecurityPath = e.target.value.trim();
      const isEnabled = clientSecurityCheckbox?.checked || false;
      
      if (clientSecurityPath) {
        try {
          await API.security.setClient(isEnabled, clientSecurityPath);
          console.log('Client security path saved:', clientSecurityPath);
        } catch (error) {
          console.error('Failed to save client security path:', error);
        }
      }
    });
  }
  
  // Cache Manager - Clustered service checkbox
  const cmClusteredCheckbox = document.getElementById('cm-clustered');
  const cmClusteredConfig = document.getElementById('cm-clustered-config');
  if (cmClusteredCheckbox && cmClusteredConfig) {
    cmClusteredCheckbox.addEventListener('change', (e) => {
      cmClusteredConfig.style.display = e.target.checked ? 'grid' : 'none';
    });
    // Set initial state
    cmClusteredConfig.style.display = cmClusteredCheckbox.checked ? 'grid' : 'none';
  }
  
  // Cache Manager - Persistence checkbox
  const cmPersistenceCheckbox = document.getElementById('cm-persistence');
  const cmDiskLocationInput = document.getElementById('cm-disk-location');
  if (cmPersistenceCheckbox && cmDiskLocationInput) {
    cmPersistenceCheckbox.addEventListener('change', (e) => {
      cmDiskLocationInput.style.display = e.target.checked ? 'block' : 'none';
    });
    // Set initial state
    cmDiskLocationInput.style.display = cmPersistenceCheckbox.checked ? 'block' : 'none';
  }
  
  // Cache Manager initialization
  document.getElementById('cm-initialize').addEventListener('click', async () => {
    const clustered = document.getElementById('cm-clustered').checked;
    const terracottaUrl = clustered ? document.getElementById('cm-terracotta-url').value : null;
    const clusterName = clustered ? document.getElementById('cm-cluster-name').value : null;
    const offheapResource = clustered ? document.getElementById('cm-offheap-resource').value : null;
    const diskResource = document.getElementById('cm-disk-resource').value;
    const persistence = document.getElementById('cm-persistence').checked;
    const diskLocation = persistence ? document.getElementById('cm-disk-location').value : null;
    const clientSecurityEnabled = document.getElementById('client-security').checked;
    const clientSecurityPath = clientSecurityEnabled ? document.getElementById('client-security-path').value : null;
    
    try {
      const result = await API.cacheManager.init(
        terracottaUrl, clusterName, diskLocation,
        offheapResource, diskResource, clientSecurityPath
      );
      UI.showNotification(result.message || 'Cache manager initialized', 'success');
      
      // Update UI based on status (only if data exists)
      if (result.data && result.data.status) {
        const status = result.data.status;
        document.getElementById('cm-status').textContent = status;
        
        if (status === 'NO CACHE MANAGER' || status === 'UNINITIALIZED') {
          document.getElementById('cm-show-config').disabled = true;
          document.getElementById('cm-close').disabled = true;
          document.getElementById('cm-destroy').disabled = true;
          document.getElementById('cache-section').style.display = 'none';
        } else {
          document.getElementById('cm-show-config').disabled = false;
          document.getElementById('cm-close').disabled = false;
          document.getElementById('cm-destroy').disabled = false;
          document.getElementById('cache-section').style.display = 'block';
        }
      }
    } catch (error) {
      UI.showNotification(`Failed to initialize cache manager: ${error.message}`, 'error');
    }
  });
  
  // Show Configuration
  document.getElementById('cm-show-config').addEventListener('click', async () => {
    try {
      const result = await API.cacheManager.getConfig();
      document.getElementById('config-text').textContent = result.data.config;
      document.getElementById('config-modal').style.display = 'flex';
    } catch (error) {
      UI.showNotification(`Failed to get configuration: ${error.message}`, 'error');
    }
  });
  
  // Close modal
  document.querySelector('.modal-close').addEventListener('click', () => {
    document.getElementById('config-modal').style.display = 'none';
  });
  
  // Close modal on outside click
  document.getElementById('config-modal').addEventListener('click', (e) => {
    if (e.target.id === 'config-modal') {
      document.getElementById('config-modal').style.display = 'none';
    }
  });
  
  // Cache Manager close/destroy
  document.getElementById('cm-close').addEventListener('click', async () => {
    try {
      const result = await API.cacheManager.close();
      UI.showNotification(result.message || 'Cache manager closed', 'success');
      document.getElementById('cm-status').textContent = 'UNINITIALIZED';
      document.getElementById('cm-show-config').disabled = true;
      document.getElementById('cm-close').disabled = true;
      document.getElementById('cm-destroy').disabled = true;
      document.getElementById('cache-section').style.display = 'none';
    } catch (error) {
      UI.showNotification(`Failed to close cache manager: ${error.message}`, 'error');
    }
  });
  
  document.getElementById('cm-destroy').addEventListener('click', async () => {
    if (!confirm('Are you sure you want to destroy the cache manager?')) {
      return;
    }
    
    try {
      const result = await API.cacheManager.destroy();
      UI.showNotification(result.message || 'Cache manager destroyed', 'warning');
      document.getElementById('cm-status').textContent = 'UNINITIALIZED';
      document.getElementById('cm-show-config').disabled = true;
      document.getElementById('cm-close').disabled = true;
      document.getElementById('cm-destroy').disabled = true;
      document.getElementById('cache-section').style.display = 'none';
    } catch (error) {
      UI.showNotification(`Failed to destroy cache manager: ${error.message}`, 'error');
    }
  });
  
  // Add cache
  document.getElementById('add-cache').addEventListener('click', async () => {
    const cacheName = document.getElementById('cache-name').value.trim();
    if (!cacheName) {
      UI.showNotification('Please enter a cache name', 'warning');
      return;
    }
    
    // Parse clustered tier configuration
    const clusteredValue = document.getElementById('cache-clustered').value;
    let clusteredDedicatedSize = 0;
    let clusteredDedicatedUnit = 'MB';
    let clusteredSharedPoolName = null;
    let clusterTierType = 'NONE';
    
    if (clusteredValue === 'shared') {
      clusterTierType = 'SHARED';
      clusteredSharedPoolName = 'resource-pool-a';
    } else if (clusteredValue.startsWith('dedicated')) {
      clusterTierType = 'DEDICATED';
      // Parse "dedicated 10MB" or "dedicated 100MB"
      const parts = clusteredValue.split(' ');
      if (parts.length === 2) {
        const sizeStr = parts[1];
        clusteredDedicatedSize = parseInt(sizeStr);
        clusteredDedicatedUnit = sizeStr.replace(/[0-9]/g, '');
      }
    }
    
    try {
      const result = await API.cache.create(
        cacheName,
        parseInt(document.getElementById('cache-onheap-size').value),
        document.getElementById('cache-onheap-unit').value,
        parseInt(document.getElementById('cache-offheap-size').value),
        document.getElementById('cache-offheap-unit').value,
        parseInt(document.getElementById('cache-disk-size').value),
        document.getElementById('cache-disk-unit').value,
        clusteredDedicatedSize, clusteredDedicatedUnit, clusteredSharedPoolName, clusterTierType
      );
      UI.showNotification(result.message || 'Cache created', 'success');
      document.getElementById('cache-name').value = '';
      
      // Refresh cache list
      await refreshCacheList();
    } catch (error) {
      UI.showNotification(`Failed to create cache: ${error.message}`, 'error');
    }
  });
  
  // Dataset Manager initialization
  const dmInitButton = document.getElementById('dm-initialize');
  if (dmInitButton) {
    dmInitButton.addEventListener('click', async () => {
      const terracottaUrl = document.getElementById('dm-terracotta-url').value;
      const clientSecurityEnabled = document.getElementById('client-security').checked;
      const clientSecurityPath = clientSecurityEnabled ? document.getElementById('client-security-path').value : null;
      
      try {
        const result = await API.datasetManager.init(terracottaUrl, clientSecurityPath);
        UI.showNotification(result.message || 'Dataset manager initialized', 'success');
        
        // Update UI
        document.getElementById('dm-status').textContent = result.data.status;
        document.getElementById('dm-close').disabled = false;
        document.getElementById('dataset-section').style.display = 'block';
      } catch (error) {
        UI.showNotification(`Failed to initialize dataset manager: ${error.message}`, 'error');
      }
    });
  }
  
  // Dataset Manager close
  const dmCloseButton = document.getElementById('dm-close');
  if (dmCloseButton) {
    dmCloseButton.addEventListener('click', async () => {
      try {
        const result = await API.datasetManager.close();
        UI.showNotification(result.message || 'Dataset manager closed', 'success');
        document.getElementById('dm-status').textContent = 'NO DATASET MANAGER';
        document.getElementById('dm-close').disabled = true;
        document.getElementById('dataset-section').style.display = 'none';
      } catch (error) {
        UI.showNotification(`Failed to close dataset manager: ${error.message}`, 'error');
      }
    });
  }
  
  // Dataset - Offheap checkbox
  const datasetOffheapCheckbox = document.getElementById('dataset-offheap');
  const datasetOffheapResource = document.getElementById('dataset-offheap-resource');
  if (datasetOffheapCheckbox && datasetOffheapResource) {
    datasetOffheapCheckbox.addEventListener('change', (e) => {
      datasetOffheapResource.disabled = !e.target.checked;
    });
    // Set initial state
    datasetOffheapResource.disabled = !datasetOffheapCheckbox.checked;
  }
  
  // Dataset - Disk checkbox
  const datasetDiskCheckbox = document.getElementById('dataset-disk');
  const datasetDiskResource = document.getElementById('dataset-disk-resource');
  if (datasetDiskCheckbox && datasetDiskResource) {
    datasetDiskCheckbox.addEventListener('change', (e) => {
      datasetDiskResource.disabled = !e.target.checked;
    });
    // Set initial state
    datasetDiskResource.disabled = !datasetDiskCheckbox.checked;
  }
  
  // Add dataset
  const addDatasetButton = document.getElementById('add-dataset');
  if (addDatasetButton) {
    addDatasetButton.addEventListener('click', async () => {
      const datasetName = document.getElementById('dataset-name').value.trim();
      if (!datasetName) {
        UI.showNotification('Please enter a dataset name', 'warning');
        return;
      }
      
      const keyType = document.getElementById('dataset-key-type').value;
      const offheapEnabled = document.getElementById('dataset-offheap').checked;
      const offheapResourceName = offheapEnabled ? document.getElementById('dataset-offheap-resource').value : null;
      const diskEnabled = document.getElementById('dataset-disk').checked;
      const diskResourceName = diskEnabled ? document.getElementById('dataset-disk-resource').value : null;
      
      try {
        const result = await API.dataset.create(
          datasetName, keyType, offheapEnabled, offheapResourceName,
          diskEnabled, diskResourceName
        );
        UI.showNotification(result.message || 'Dataset created', 'success');
        document.getElementById('dataset-name').value = '';
        
        // Refresh dataset list
        await refreshDatasetList();
      } catch (error) {
        UI.showNotification(`Failed to create dataset: ${error.message}`, 'error');
      }
    });
  }
  
  // EE features are now shown in UI.updateKitUI() when kit info is loaded
}

/**
 * Refresh the cache list from the server
 */
async function refreshCacheList() {
  try {
    const result = await API.cache.list();
    const cacheList = document.getElementById('cache-list');
    
    if (!result.data || !result.data.caches || result.data.caches.length === 0) {
      cacheList.innerHTML = '<div class="empty-state">No caches available</div>';
      return;
    }
    
    // Build cache list HTML
    cacheList.innerHTML = '';
    result.data.caches.forEach(cache => {
      const cacheItem = document.createElement('div');
      cacheItem.className = 'dataset-instance-item';
      
      // Cache name
      const cacheName = document.createElement('span');
      cacheName.className = 'instance-name';
      cacheName.textContent = cache.name;
      cacheItem.appendChild(cacheName);
      
      // Pounding slider
      const sliderDiv = document.createElement('div');
      sliderDiv.className = 'pounding-slider';
      
      const label = document.createElement('label');
      const currentIntensity = cache.poundingIntensity || 0;
      label.textContent = `Pounding: ${currentIntensity}`;
      sliderDiv.appendChild(label);
      
      const slider = document.createElement('input');
      slider.type = 'range';
      slider.min = '0';
      slider.max = '11';
      slider.value = cache.poundingIntensity || '0';
      slider.addEventListener('input', async (e) => {
        const intensity = parseInt(e.target.value);
        label.textContent = `Pounding: ${intensity}`;
        try {
          await API.cache.setPounding(cache.name, intensity);
        } catch (error) {
          console.error('Failed to set pounding intensity:', error);
        }
      });
      sliderDiv.appendChild(slider);
      cacheItem.appendChild(sliderDiv);
      
      // Clear button
      const clearBtn = document.createElement('button');
      clearBtn.textContent = 'Clear';
      clearBtn.className = 'btn btn-secondary';
      clearBtn.onclick = () => clearCache(cache.name);
      cacheItem.appendChild(clearBtn);
      
      // Destroy button
      const destroyBtn = document.createElement('button');
      destroyBtn.textContent = 'Destroy';
      destroyBtn.className = 'btn btn-danger';
      destroyBtn.onclick = () => destroyCache(cache.name);
      cacheItem.appendChild(destroyBtn);
      
      cacheList.appendChild(cacheItem);
    });
  } catch (error) {
    console.error('Failed to refresh cache list:', error);
  }
}

/**
 * Clear a cache
 */
async function clearCache(cacheName) {
  try {
    const result = await API.cache.clear(cacheName);
    UI.showNotification(result.message || `Cache ${cacheName} cleared`, 'success');
  } catch (error) {
    UI.showNotification(`Failed to clear cache: ${error.message}`, 'error');
  }
}

/**
 * Destroy a cache
 */
async function destroyCache(cacheName) {
  if (!confirm(`Are you sure you want to destroy cache ${cacheName}?`)) {
    return;
  }
  
  try {
    const result = await API.cache.destroy(cacheName);
    UI.showNotification(result.message || `Cache ${cacheName} destroyed`, 'warning');
    await refreshCacheList();
  } catch (error) {
    UI.showNotification(`Failed to destroy cache: ${error.message}`, 'error');
  }
}

/**
 * Refresh the dataset list from the server
 */
async function refreshDatasetList() {
  try {
    const result = await API.dataset.list();
    const datasetList = document.getElementById('dataset-list');
    
    if (!result.data || !result.data.datasets || result.data.datasets.length === 0) {
      datasetList.innerHTML = '<div class="empty-state">No datasets available</div>';
      return;
    }
    
    // Clear existing content
    datasetList.innerHTML = '';
    
    // Build dataset list HTML
    result.data.datasets.forEach(dataset => {
      // Dataset header row
      const datasetHeader = document.createElement('div');
      datasetHeader.className = 'dataset-header';
      
      const nameSpan = document.createElement('span');
      nameSpan.className = 'dataset-name';
      nameSpan.textContent = dataset.name;
      datasetHeader.appendChild(nameSpan);
      
      const addInstanceBtn = document.createElement('button');
      addInstanceBtn.textContent = 'Add dataset instance';
      addInstanceBtn.className = 'btn btn-primary';
      addInstanceBtn.onclick = () => addDatasetInstance(dataset.name);
      datasetHeader.appendChild(addInstanceBtn);
      
      const destroyBtn = document.createElement('button');
      destroyBtn.textContent = 'Destroy dataset';
      destroyBtn.className = 'btn btn-danger';
      destroyBtn.onclick = () => destroyDataset(dataset.name);
      datasetHeader.appendChild(destroyBtn);
      
      datasetList.appendChild(datasetHeader);
      
      // Dataset instances
      if (dataset.instances && dataset.instances.length > 0) {
        dataset.instances.forEach(instance => {
          const instanceItem = document.createElement('div');
          instanceItem.className = 'dataset-instance-item';
          
          // Instance name
          const instanceName = document.createElement('span');
          instanceName.className = 'instance-name';
          instanceName.textContent = instance.name;
          instanceItem.appendChild(instanceName);
          
          // Cell input
          const cellInput = document.createElement('input');
          cellInput.type = 'text';
          cellInput.placeholder = 'Cell value';
          cellInput.className = 'cell-input';
          instanceItem.appendChild(cellInput);
          
          // Add cell button
          const addCellBtn = document.createElement('button');
          addCellBtn.textContent = 'Add cell';
          addCellBtn.className = 'btn btn-secondary btn-sm';
          addCellBtn.onclick = () => addCell(dataset.name, cellInput.value);
          instanceItem.appendChild(addCellBtn);
          
          // Remove cell button
          const removeCellBtn = document.createElement('button');
          removeCellBtn.textContent = 'Remove cell';
          removeCellBtn.className = 'btn btn-secondary btn-sm';
          removeCellBtn.onclick = () => removeCell(dataset.name, cellInput.value);
          instanceItem.appendChild(removeCellBtn);
          
          // Pounding slider
          const sliderDiv = document.createElement('div');
          sliderDiv.className = 'pounding-slider';
          
          const label = document.createElement('label');
          const currentIntensity = instance.poundingIntensity || 0;
          label.textContent = `Pounding: ${currentIntensity}`;
          sliderDiv.appendChild(label);
          
          const slider = document.createElement('input');
          slider.type = 'range';
          slider.min = '0';
          slider.max = '11';
          slider.value = instance.poundingIntensity || '0';
          slider.addEventListener('input', async (e) => {
            const intensity = parseInt(e.target.value);
            label.textContent = `Pounding: ${intensity}`;
            try {
              await API.dataset.setPounding(instance.name, intensity);
            } catch (error) {
              console.error('Failed to set pounding intensity:', error);
            }
          });
          sliderDiv.appendChild(slider);
          instanceItem.appendChild(sliderDiv);
          
          // Close instance button
          const closeBtn = document.createElement('button');
          closeBtn.textContent = 'Close dataset instance';
          closeBtn.className = 'btn btn-secondary';
          closeBtn.onclick = () => closeDatasetInstance(dataset.name, instance.name);
          instanceItem.appendChild(closeBtn);
          
          datasetList.appendChild(instanceItem);
        });
      }
    });
  } catch (error) {
    console.error('Failed to refresh dataset list:', error);
  }
}

/**
 * Add a dataset instance
 */
async function addDatasetInstance(datasetName) {
  try {
    const result = await API.dataset.createInstance(datasetName);
    UI.showNotification(result.message || `Dataset instance created: ${result.data.instanceName}`, 'success');
    await refreshDatasetList();
  } catch (error) {
    UI.showNotification(`Failed to create dataset instance: ${error.message}`, 'error');
  }
}

/**
 * Close a dataset instance
 */
async function closeDatasetInstance(datasetName, instanceName) {
  try {
    const result = await API.dataset.closeInstance(datasetName, instanceName);
    UI.showNotification(result.message || `Dataset instance ${instanceName} closed`, 'success');
    await refreshDatasetList();
  } catch (error) {
    UI.showNotification(`Failed to close dataset instance: ${error.message}`, 'error');
  }
}

/**
 * Add a cell to dataset
 */
async function addCell(datasetName, cellValue) {
  if (!cellValue || cellValue.trim() === '') {
    UI.showNotification('Please enter a cell value', 'warning');
    return;
  }
  
  try {
    const result = await API.dataset.addCell(datasetName, cellValue);
    UI.showNotification(result.message || 'Cell added successfully', 'success');
  } catch (error) {
    UI.showNotification(`Failed to add cell: ${error.message}`, 'error');
  }
}

/**
 * Remove a cell from dataset
 */
async function removeCell(datasetName, cellValue) {
  if (!cellValue || cellValue.trim() === '') {
    UI.showNotification('Please enter a cell value', 'warning');
    return;
  }
  
  try {
    const result = await API.dataset.removeCell(datasetName, cellValue);
    UI.showNotification(result.message || 'Cell removed successfully', 'success');
  } catch (error) {
    UI.showNotification(`Failed to remove cell: ${error.message}`, 'error');
  }
}

/**
 * Destroy a dataset
 */
async function destroyDataset(datasetName) {
  if (!confirm(`Are you sure you want to destroy dataset ${datasetName}?`)) {
    return;
  }
  
  try {
    const result = await API.dataset.destroy(datasetName);
    UI.showNotification(result.message || `Dataset ${datasetName} destroyed`, 'warning');
    await refreshDatasetList();
  } catch (error) {
    UI.showNotification(`Failed to destroy dataset: ${error.message}`, 'error');
  }
}


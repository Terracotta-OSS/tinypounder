/**
 * Console module for managing WebSocket connections and console output.
 */
const Console = {
  connections: new Map(),
  
  /**
   * Initialize console system
   */
  init() {
    this.setupConsoleTabSwitching();
  },

  /**
   * Setup console tab switching
   */
  setupConsoleTabSwitching() {
    const tabButtons = document.querySelectorAll('.console-tab-button');

    tabButtons.forEach(button => {
      button.addEventListener('click', () => {
        const consoleName = button.dataset.console;

        // Deactivate all tabs and consoles
        document.querySelectorAll('.console-tab-button').forEach(btn => btn.classList.remove('active'));
        document.querySelectorAll('.console').forEach(consoleEl => {
          consoleEl.classList.remove('active');
          consoleEl.style.display = 'none';
        });

        // Activate clicked tab and its console
        button.classList.add('active');
        const target = document.getElementById(`console-${consoleName}`);
        if (target) {
          target.classList.add('active');
          target.style.display = 'block';
        }
      });
    });
  },

  /**
   * Connect to a console WebSocket
   */
  connect(consoleName) {
    if (this.connections.has(consoleName)) {
      console.log(`Already connected to console: ${consoleName}`);
      return;
    }

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${window.location.host}/ws/console/${consoleName}`;
    
    console.log(`Connecting to console WebSocket: ${wsUrl}`);
    
    const ws = new WebSocket(wsUrl);
    
    ws.onopen = () => {
      console.log(`WebSocket connected: ${consoleName}`);
      this.connections.set(consoleName, ws);
    };

    ws.onmessage = (event) => {
      try {
        const message = JSON.parse(event.data);
        this.handleMessage(consoleName, message);
      } catch (error) {
        console.error('Failed to parse WebSocket message', error);
      }
    };

    ws.onerror = (error) => {
      console.error(`WebSocket error for ${consoleName}:`, error);
    };

    ws.onclose = (event) => {
      console.log(`WebSocket closed: ${consoleName}, code: ${event.code}, wasClean: ${event.wasClean}`);
      this.connections.delete(consoleName);
      
      // Only auto-reconnect if it was an unexpected closure (not a clean close)
      // Clean close (code 1000) happens during page refresh or manual disconnect
      if (!event.wasClean && event.code !== 1000) {
        setTimeout(() => {
          console.log(`Attempting to reconnect to ${consoleName}...`);
          this.connect(consoleName);
        }, 5000);
      }
    };
  },

  /**
   * Disconnect from a console WebSocket
   */
  disconnect(consoleName) {
    const ws = this.connections.get(consoleName);
    if (ws) {
      ws.close();
      this.connections.delete(consoleName);
    }
  },

  /**
   * Handle incoming WebSocket message
   */
  handleMessage(consoleName, message) {
    switch (message.type) {
      case 'connected':
        this.appendToConsole(consoleName, `[Connected to ${consoleName}]\n`);
        break;
        
      case 'console':
        if (message.lines && Array.isArray(message.lines)) {
          const text = message.lines.join('\n');
          this.appendToConsole(consoleName, text);
        }
        break;
        
      case 'status':
        this.updateServerStatus(consoleName, message.status, message.pid);
        break;
        
      default:
        console.warn('Unknown message type:', message.type);
    }
  },

  /**
   * Append text to console
   */
  appendToConsole(consoleName, text) {
    const consoleElement = document.getElementById(`console-${consoleName}`);
    if (consoleElement) {
      // Check if user has scrolled away from bottom
      const isScrolledToBottom = consoleElement.scrollHeight - consoleElement.clientHeight <= consoleElement.scrollTop + 1;
      
      consoleElement.textContent += text;
      
      // Only auto-scroll if user was already at bottom
      if (isScrolledToBottom) {
        consoleElement.scrollTop = consoleElement.scrollHeight;
      }
    }
  },

  /**
   * Clear console
   */
  clearConsole(consoleName) {
    const consoleElement = document.getElementById(`console-${consoleName}`);
    if (consoleElement) {
      consoleElement.textContent = '';
    }
  },

  /**
   * Update server status in UI
   */
  async updateServerStatus(consoleName, status, pid) {
    // consoleName is the key like "stripe-1-server-1"
    const pidElement = document.querySelector(`[data-pid="${consoleName}"]`);
    const stateElement = document.querySelector(`[data-state="${consoleName}"]`);
    
    if (pidElement) {
      pidElement.textContent = pid ? `PID: ${pid}` : '';
    }
    
    if (stateElement) {
      stateElement.textContent = status;
    }

    // Update button states based on server status
    const startBtn = document.querySelector(`.btn-start[data-server="${consoleName}"]`);
    const killBtn = document.querySelector(`.btn-kill[data-server="${consoleName}"]`);
    const statusBtn = document.querySelector(`.btn-status[data-server="${consoleName}"]`);

    // Enable kill/status buttons when server is starting or running
    const isRunning = status && status !== 'TERMINATED' && status !== 'STOPPED';
    
    if (startBtn) startBtn.disabled = isRunning;
    if (killBtn) killBtn.disabled = !isRunning;
    if (statusBtn) statusBtn.disabled = !isRunning;

    const serverList = await API.servers.list();
    if (serverList.data && serverList.data.servers) {
      UI.updateClusterControlButtons(serverList.data.servers);
    }
  },

  /**
   * Add a new console tab
   */
  addConsoleTab(consoleName, displayName) {
    // Check if tab already exists
    const existingButton = document.querySelector(`.console-tab-button[data-console="${consoleName}"]`);
    const existingConsole = document.getElementById(`console-${consoleName}`);
    
    if (existingButton && existingConsole) {
      // Tab exists, just reactivate it
      // Don't clear console or reconnect if already connected
      
      // Activate existing tab
      document.querySelectorAll('.console-tab-button').forEach(btn => btn.classList.remove('active'));
      document.querySelectorAll('.console').forEach(console => console.classList.remove('active'));
      existingButton.classList.add('active');
      existingConsole.classList.add('active');
      
      // Only reconnect if not already connected
      if (!this.connections.has(consoleName)) {
        console.log(`Reconnecting to existing console tab: ${consoleName}`);
        this.connect(consoleName);
      } else {
        console.log(`Console tab already active and connected: ${consoleName}`);
      }
      return;
    }
    
    // Create new tab button
    const tabButtons = document.querySelector('.console-tab-buttons');
    const button = document.createElement('button');
    button.className = 'console-tab-button';
    button.dataset.console = consoleName;
    button.textContent = displayName || consoleName;
    button.addEventListener('click', () => {
      // Deactivate all tabs and consoles
      document.querySelectorAll('.console-tab-button').forEach(btn => btn.classList.remove('active'));
      document.querySelectorAll('.console').forEach(consoleEl => {
        consoleEl.classList.remove('active');
        consoleEl.style.display = 'none';
      });
      
      // Activate clicked tab and its console
      button.classList.add('active');
      const target = document.getElementById(`console-${consoleName}`);
      if (target) {
        target.classList.add('active');
        target.style.display = 'block';
      }
    });
    tabButtons.appendChild(button);

    // Add console element
    const consoleContent = document.querySelector('.console-content');
    const consoleElement = document.createElement('pre');
    consoleElement.id = `console-${consoleName}`;
    consoleElement.className = 'console';
    consoleContent.appendChild(consoleElement);

    // Activate the new tab immediately
    document.querySelectorAll('.console-tab-button').forEach(btn => btn.classList.remove('active'));
    document.querySelectorAll('.console').forEach(consoleEl => {
      consoleEl.classList.remove('active');
      consoleEl.style.display = 'none';
    });
    button.classList.add('active');
    consoleElement.classList.add('active');
    consoleElement.style.display = 'block';

    // Connect to WebSocket
    this.connect(consoleName);
  },

  /**
   * Remove a console tab
   */
  removeConsoleTab(consoleName) {
    // Disconnect WebSocket
    this.disconnect(consoleName);

    // Remove tab button
    const button = document.querySelector(`.console-tab-button[data-console="${consoleName}"]`);
    if (button) {
      button.remove();
    }

    // Remove console element
    const consoleElement = document.getElementById(`console-${consoleName}`);
    if (consoleElement) {
      consoleElement.remove();
    }
  }
};

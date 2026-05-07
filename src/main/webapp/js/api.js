/**
 * API module for communicating with the TinyPounder backend.
 * Provides functions for all REST API endpoints.
 */
const API = {
  baseUrl: '/api',

  /**
   * Make a generic API request
   */
  async request(method, endpoint, body = null) {
    const options = {
      method,
      headers: {
        'Content-Type': 'application/json'
      }
    };

    if (body) {
      options.body = JSON.stringify(body);
    }

    try {
      const response = await fetch(`${this.baseUrl}${endpoint}`, options);
      const data = await response.json();
      
      // Check HTTP status
      if (!response.ok) {
        throw new Error(data.error || `HTTP ${response.status}`);
      }
      
      // Check API response success flag
      if (data.success === false) {
        throw new Error(data.error || 'API request failed');
      }
      
      return data;
    } catch (error) {
      console.error(`API request failed: ${method} ${endpoint}`, error);
      throw error;
    }
  },

  // Kit API
  kit: {
    setPath: (kitPath) => API.request('POST', '/kit/path', { kitPath }),
    setLicense: (licensePath) => API.request('POST', '/kit/license', { licensePath }),
    getInfo: () => API.request('GET', '/kit/info')
  },

  // Config API
  config: {
    setBaseLocation: (baseLocation) => API.request('POST', '/config/base-location', { baseLocation }),
    deleteBaseLocation: (baseLocation) => API.request('DELETE', '/config/base-location', { baseLocation }),
    setOffheap: (offheapCount) => API.request('POST', '/config/offheap', { offheapCount }),
    setDataRoots: (dataRootCount) => API.request('POST', '/config/dataroots', { dataRootCount }),
    setFailover: (failoverPriority) => API.request('POST', '/config/failover', { failoverPriority }),
    setServers: (stripeCount, serverCount, reconnectWindow) => 
      API.request('POST', '/config/servers', { stripeCount, serverCount, reconnectWindow }),
    generate: (offheapResources, datarootPaths, servers, platformPersistence, platformBackup) => 
      API.request('POST', '/config/generate', { offheapResources, datarootPaths, servers, platformPersistence, platformBackup }),
    getCurrent: () => API.request('GET', '/config/current'),
    getPreview: () => API.request('GET', '/config/preview')
  },

  // Security API
  security: {
    setServer: (enabled, serverSecurityPath) => 
      API.request('POST', '/security/server', { enabled, serverSecurityPath }),
    getServer: () => API.request('GET', '/security/server'),
    setClient: (enabled, clientSecurityPath) => 
      API.request('POST', '/security/client', { enabled, clientSecurityPath }),
    getClient: () => API.request('GET', '/security/client')
  },

  // Server API
  servers: {
    start: (serverName, stripeName, hostname, clientPort, clusterName) => 
      API.request('POST', '/servers/start', { serverName, stripeName, hostname, clientPort, clusterName }),
    kill: (serverName, stripeName) => 
      API.request('POST', '/servers/kill', { serverName, stripeName }),
    getStatus: () => API.request('GET', '/servers/status'),
    list: () => API.request('GET', '/servers/list')
  },

  // Cluster API
  cluster: {
    configure: (clusterName) => API.request('POST', '/cluster/configure', { clusterName }),
    backup: (clusterName) => API.request('POST', '/cluster/backup', { clusterName }),
    dump: (clusterName) => API.request('POST', '/cluster/dump', { clusterName }),
    stop: (clusterName) => API.request('POST', '/cluster/stop', { clusterName }),
    getStatus: () => API.request('GET', '/cluster/status')
  },

  // Cache Manager API
  cacheManager: {
    init: (terracottaUrl, clusterTierManagerName, diskPersistenceLocation, 
           offheapResourceName, serverDiskResourceName, clientSecurityPath) => 
      API.request('POST', '/cachemanager/init', {
        terracottaUrl, clusterTierManagerName, diskPersistenceLocation,
        offheapResourceName, serverDiskResourceName, clientSecurityPath
      }),
    close: () => API.request('POST', '/cachemanager/close'),
    destroy: () => API.request('POST', '/cachemanager/destroy'),
    getStatus: () => API.request('GET', '/cachemanager/status'),
    getConfig: () => API.request('GET', '/cachemanager/config')
  },

  // Cache API
  cache: {
    create: (cacheName, onHeapSize, onHeapUnit, offHeapSize, offHeapUnit, 
             diskSize, diskUnit, clusteredDedicatedSize, clusteredDedicatedUnit,
             clusteredSharedPoolName, clusterTierType) => 
      API.request('POST', '/cache/create', {
        cacheName, onHeapSize, onHeapUnit, offHeapSize, offHeapUnit,
        diskSize, diskUnit, clusteredDedicatedSize, clusteredDedicatedUnit,
        clusteredSharedPoolName, clusterTierType
      }),
    remove: (cacheName) => API.request('DELETE', `/cache/${cacheName}`),
    clear: (cacheName) => API.request('POST', `/cache/${cacheName}/clear`),
    destroy: (cacheName) => API.request('POST', `/cache/${cacheName}/destroy`),
    setPounding: (cacheName, intensity) => 
      API.request('POST', `/cache/${cacheName}/pound`, { intensity }),
    list: () => API.request('GET', '/cache/list')
  },

  // Dataset Manager API
  datasetManager: {
    init: (terracottaUrl, clientSecurityPath) => 
      API.request('POST', '/datasetmanager/init', { terracottaUrl, clientSecurityPath }),
    close: () => API.request('POST', '/datasetmanager/close'),
    getStatus: () => API.request('GET', '/datasetmanager/status')
  },

  // Dataset API
  dataset: {
    create: (datasetName, keyType, offheapEnabled, offheapResourceName, 
             diskEnabled, diskResourceName) => 
      API.request('POST', '/dataset/create', {
        datasetName, keyType, offheapEnabled, offheapResourceName,
        diskEnabled, diskResourceName
      }),
    remove: (datasetName) => API.request('DELETE', `/dataset/${datasetName}`),
    destroy: (datasetName) => API.request('POST', `/dataset/${datasetName}/destroy`),
    setPounding: (instanceName, intensity) => 
      API.request('POST', `/dataset/${instanceName}/pound`, { intensity }),
    list: () => API.request('GET', '/dataset/list'),
    createInstance: (datasetName) => 
      API.request('POST', `/dataset/${datasetName}/createInstance`),
    closeInstance: (datasetName, instanceName) => 
      API.request('POST', `/dataset/${datasetName}/closeInstance`, { instanceName }),
    addCell: (datasetName, cell) => 
      API.request('POST', `/dataset/${datasetName}/addCell`, { cell }),
    removeCell: (datasetName, cell) => 
      API.request('POST', `/dataset/${datasetName}/removeCell`, { cell })
  }
};

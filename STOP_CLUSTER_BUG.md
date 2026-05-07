# Stop Cluster Bug

## Issue Description

Clicking the "Stop cluster" button causes the Jetty server to crash with `ERR_CONNECTION_REFUSED`.

## Error Details

**Browser Console Error:**
```
POST http://localhost:9490/api/cluster/stop net::ERR_CONNECTION_REFUSED
request @ api.js:24
stop @ api.js:91
(anonymous) @ app.js:670
api.js:39 API request failed: POST /cluster/stop TypeError: Failed to fetch
    at Object.request (api.js:24:30)
    at Object.stop (api.js:91:32)
    at HTMLButtonElement.<anonymous> (app.js:670:40)
request @ api.js:39
await in request
stop @ api.js:91
(anonymous) @ app.js:670
ui.js:63 [ERROR] Failed to stop cluster: Failed to fetch
```

## Root Cause

The issue appears to be in `ClusterApiHandler.handleClusterStop()` method, specifically the `killServerByPort()` method which is too aggressive in killing processes. It likely kills the Jetty server itself when trying to stop Terracotta servers.

## Affected Code

**File:** `src/main/java/org/terracotta/tinypounder/api/ClusterApiHandler.java`

**Method:** `killServerByPort(int port)` (lines ~490-505)

```java
private void killServerByPort(int port) throws Exception {
  String os = System.getProperty("os.name").toLowerCase();
  
  if (os.contains("win")) {
    // Windows: netstat -ano | findstr :<port> to get PID, then taskkill
    ProcessBuilder pb = new ProcessBuilder("cmd", "/c", 
        "for /f \"tokens=5\" %a in ('netstat -ano ^| findstr :" + port + "') do taskkill /F /PID %a");
    pb.start().waitFor();
  } else {
    // Unix/Mac: lsof -ti tcp:<port> to get PID, then kill
    ProcessBuilder pb = new ProcessBuilder("sh", "-c", 
        "lsof -ti tcp:" + port + " | xargs kill -9");
    pb.start().waitFor();
  }
}
```

## Recommended Fix

1. Improve the `killServerByPort()` method to be more selective about which processes it kills
2. Add validation to ensure we're not killing the Jetty server process (port 9490)
3. Consider using the `RunningServer.kill()` method instead of port-based killing when possible
4. Add better error handling to prevent the entire server from crashing

## Steps to Reproduce

1. Start all servers in diagnostic mode
2. Configure the cluster (servers become ACTIVE-COORDINATOR/PASSIVE-STANDBY)
3. Click "Stop cluster" button
4. Observe Jetty server crashes with ERR_CONNECTION_REFUSED

## Priority

Medium - The functionality is broken but there's a workaround (kill servers individually using KILL buttons)

## Related Files

- `src/main/java/org/terracotta/tinypounder/api/ClusterApiHandler.java`
- `src/main/webapp/js/api.js`
- `src/main/webapp/js/app.js`

## Date Discovered

2026-05-07

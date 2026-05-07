# AGENTS.md

This file provides guidance to agents when working with code in this repository.

## Project Overview

**TinyPounder** is a GUI testing tool for Ehcache3 cache managers and TCStore datasets. It's built as a lightweight servlet-based web application with a modern HTML/CSS/JavaScript interface that allows developers to:

- Test creation, removal, and destruction of Ehcache3 cache managers and caches
- Test TCStore dataset managers and datasets for persistent data storage
- Configure and manage Terracotta server clusters
- Simulate load testing ("pounding") on caches and datasets

The application does **not** embed Ehcache3 or TCStore libraries. Instead, it dynamically loads them from external "kits" (archives containing the required libraries), supporting both open-source and enterprise editions.

### Key Technologies

- **Java 21** - Target JVM version
- **Jakarta Servlet API 6.0** - Web application framework
- **Jakarta WebSocket API 2.1** - Real-time console output streaming
- **Jetty 12** - Embedded web server for development
- **Maven** - Build and dependency management
- **Ehcache3** - Distributed caching (loaded from external kit)
- **TCStore** - Persistent data storage (loaded from external kit)
- **Jackson** - JSON serialization for REST API
- **SLF4J + Logback** - Logging framework

### Architecture

The application uses a kit-aware classloader system (`KitAwareClassLoaderDelegator`) to dynamically load Ehcache3 and TCStore libraries from user-specified kit locations. This allows testing different versions without rebuilding the application.

Main components:
- **Web Layer**:
  - `ApiServlet` - REST API endpoint handling all client requests
  - `StaticResourceServlet` - Serves HTML, CSS, JS, and other static files
  - `ConsoleWebSocketEndpoint` - WebSocket endpoint for streaming server console output
  - `TinyPounderContextListener` - Application lifecycle management
- **Business Logic**:
  - `CacheManagerBusiness` - Manages Ehcache3 cache manager lifecycle and operations
  - `DatasetManagerBusinessReflectionImpl` - Manages TCStore dataset manager via reflection
  - `TerracottaServerBusiness` - Controls Terracotta server processes
  - `TerracottaConfigGenerator` - Generates server configuration files
- **API Handlers** (in `org.terracotta.tinypounder.api`):
  - `KitApiHandler` - Kit path and license management
  - `ConfigApiHandler` - Server configuration management
  - `ServerApiHandler` - Server lifecycle control
  - `ClusterApiHandler` - Cluster tools execution
  - `CacheManagerApiHandler` - Cache manager operations
  - `CacheApiHandler` - Individual cache operations
  - `DatasetManagerApiHandler` - Dataset manager operations
  - `DatasetApiHandler` - Individual dataset operations
  - `SecurityApiHandler` - Security configuration
- **Configuration**:
  - `Settings` - Persists user configuration to `~/.tinypounder/config.properties`

### UI Architecture

The web UI is built with vanilla JavaScript and organized into modules:
- `app.js` - Main application initialization and state management
- `ui.js` - UI component rendering and event handling
- `api.js` - REST API client wrapper
- `console.js` - WebSocket console output handling
- `index.html` - Single-page application structure
- `styles.css` - Application styling

## Building and Running

### Build the Application

```bash
./mvnw clean package
```

This produces a WAR file: `target/tinypounder.war`

### Run the Application

**Development mode with Jetty:**
```bash
./mvnw jetty:run
```

**Production deployment:**
Deploy `target/tinypounder.war` to any Jakarta EE 10+ compatible servlet container (Tomcat 10+, Jetty 12+, etc.)

**Quick start script:**
```bash
./start.sh
```

The application starts on **http://localhost:9490** (configurable in `pom.xml` Jetty plugin configuration).

### Development Workflow

1. Make code changes
2. Run `./mvnw clean package` to rebuild
3. Run `./mvnw jetty:run` to test locally
4. Access at http://localhost:9490

For faster iteration on UI changes, modify files in `src/main/webapp/` and refresh the browser (no rebuild needed for static files).

## Development Conventions

### Code Structure

- **Main application**: `src/main/java/org/terracotta/tinypounder/`
  - `servlet/` - Servlet and WebSocket endpoints
  - `api/` - REST API handlers
  - `model/` - Data models (e.g., `ApiResponse`)
  - Business logic classes (CacheManagerBusiness, TerracottaServerBusiness, etc.)
- **Web resources**: `src/main/webapp/`
  - `index.html` - Main application page
  - `css/styles.css` - Application styles
  - `js/` - JavaScript modules
  - `WEB-INF/web.xml` - Servlet configuration
- **Configuration**: `src/main/resources/`
  - `logback.xml` - Logging configuration
- **Tests**: `src/test/java/org/terracotta/tinypounder/`
- **Legacy code**: `src/main/java-old/` - Old Spring Boot + Vaadin implementation (not compiled)

### Configuration Management

User settings are persisted to `~/.tinypounder/config.properties` and include:
- Kit path and license path
- Server configuration (stripe count, server count, offheap resources, data roots)
- Security settings (server and client security root directories)
- Failover priority settings (availability vs. consistency)

### Key Design Patterns

1. **Dynamic Classloading**: Uses reflection and custom classloaders to load Ehcache3/TCStore from external kits
2. **Process Management**: Spawns and manages Terracotta server processes with console output capture via IPC EventBus
3. **Configuration Generation**: Dynamically generates Terracotta server configuration files (XML or properties format)
4. **REST API**: JSON-based API with consistent response format (`ApiResponse` model)
5. **WebSocket Streaming**: Real-time console output streaming to browser clients

### REST API Structure

All API requests go through `/api` endpoint with the following format:
- **Request**: JSON body with `action` field and action-specific parameters
- **Response**: JSON with `success` boolean, optional `message`, and optional `data` object

Example:
```json
// Request
{
  "action": "getKitPath"
}

// Response
{
  "success": true,
  "data": {
    "kitPath": "/path/to/kit",
    "isEnterpriseKit": true
  }
}
```

### Working with Kits

The application supports two types of kits:
- **Open Source Kits**: Available from [Ehcache3 releases](https://github.com/ehcache/ehcache3/releases) (archives ending with `-kit`)
- **Enterprise Kits**: Require a license file and provide additional features (platform persistence, backup, security, TCStore)

Kit detection logic in `KitAwareClassLoaderDelegator`:
- Checks for presence of specific classes to determine kit type and capabilities
- Validates security root directories for enterprise features
- Dynamically enables/disables UI components based on kit capabilities

### Server Configuration

The application can generate two types of Terracotta server configurations:
- **XML format** (legacy): `tc-config-stripe-<N>.xml` files
- **Properties format** (dynamic config): `cluster.properties` file

Configuration includes:
- Offheap resources
- Data roots (enterprise only)
- Platform persistence and backup (enterprise only)
- Security settings (enterprise only)
- Failover priority (availability vs. consistency with voter count)
- Client reconnect window

### Testing Approach

The application includes unit tests for core components:
- `SettingsTest` - Configuration persistence
- `ExceptionUtilsTest` - Exception handling utilities
- `KitAwareClassLoaderDelegatorTest` - Kit detection logic
- `ApiResponseTest` - API response model

Run tests with:
```bash
./mvnw test
```

### Logging

Logging is configured in `src/main/resources/logback.xml` with specific levels for:
- Terracotta components (mostly WARN/ERROR to reduce noise)
- Ehcache components (WARN level)
- Application components (INFO level)

Console output from managed Terracotta servers is captured via IPC EventBus and streamed to the browser via WebSocket.

## Common Development Tasks

### Adding a New API Endpoint

1. Create a new handler class in `org.terracotta.tinypounder.api` implementing `ApiHandler`
2. Implement the `handle(JsonNode request)` method
3. Register the handler in `ApiServlet.initHandlers()`
4. Add corresponding JavaScript API call in `js/api.js`
5. Update UI in `js/ui.js` to use the new API

### Adding Support for New Kit Versions

1. Update `KitAwareClassLoaderDelegator` to detect new classes or features
2. Adjust configuration generation in `TerracottaConfigGenerator` if XML/properties format changes
3. Test with both open-source and enterprise kits

### Modifying UI Layout

The UI is organized into sections:
- **Kit Configuration** - Kit path and license selection
- **Server Configuration** - Cluster topology and resource settings
- **Server Control** - Start/stop servers, run cluster tools
- **Cache Management** - Create/manage cache managers and caches
- **Dataset Management** - Create/manage dataset managers and datasets
- **Console Output** - Real-time server logs

UI updates should maintain this workflow structure. Modify `index.html` for structure, `css/styles.css` for styling, and `js/ui.js` for behavior.

### Debugging Server Startup Issues

1. Check browser console for JavaScript errors
2. Check WebSocket console output for server logs
3. Verify generated configuration files in the kit directory
4. Check logs in the configured base location (default: `~/terracotta/MyCluster/logs/`)
5. Ensure ports are not already in use (default client port: 9410+)
6. Check application logs (stdout/stderr from Jetty)

### Security Configuration

For enterprise kits with security enabled:
- Server security root directory must contain: `keystore.jks`, `truststore.jks`, `whitelist.txt`
- Client security root directory must contain: `keystore.jks`, `truststore.jks`
- Authentication can be file-based, LDAP, or certificate-based (detected by directory name suffix)

## Migration Notes

This application was previously built with Spring Boot and Vaadin. The legacy code is preserved in `src/main/java-old/` for reference but is not compiled or used. The current implementation uses:
- Plain Jakarta Servlets instead of Spring Boot
- Vanilla JavaScript instead of Vaadin
- REST API + WebSocket instead of Vaadin server-side components

This migration significantly reduced dependencies, improved startup time, and simplified deployment.
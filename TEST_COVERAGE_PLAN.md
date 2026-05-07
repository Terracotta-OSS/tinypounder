# Test Coverage Implementation Plan

## Phase 1: Foundation (Week 1) - High Priority Core Logic

### 1.1 TerracottaConfigGenerator Tests
**File:** `src/test/java/org/terracotta/tinypounder/TerracottaConfigGeneratorTest.java`

**Test Scenarios:**
- XML config generation for single stripe
- XML config generation for multiple stripes
- Properties config generation
- Offheap resource configuration
- Data root configuration (enterprise)
- Security settings integration
- Failover priority settings (availability vs consistency)
- Client reconnect window configuration
- Invalid configuration handling

**Estimated Effort:** 1 day

### 1.2 API Handler Tests
**Files:** `src/test/java/org/terracotta/tinypounder/api/*ApiHandlerTest.java`

**Test Coverage per Handler:**

**KitApiHandler:**
- Get/set kit path
- License path validation
- Kit type detection (OSS vs Enterprise)
- Invalid path handling

**ConfigApiHandler:**
- Get/update server configuration
- Stripe/server count validation
- Resource count validation
- Configuration persistence

**ServerApiHandler:**
- Start/stop servers
- Server status queries
- Error handling for failed starts
- Multiple server management

**ClusterApiHandler:**
- Cluster tool execution
- Command validation
- Output capture

**CacheManagerApiHandler:**
- Create/destroy cache managers
- List cache managers
- Configuration validation
- Connection string parsing

**CacheApiHandler:**
- Create/remove/destroy caches
- Cache configuration
- Pounding intensity control
- Cache statistics

**DatasetManagerApiHandler:**
- Create/destroy dataset managers
- List dataset managers
- Configuration validation

**DatasetApiHandler:**
- Create/remove/destroy datasets
- Dataset configuration
- Dataset operations

**SecurityApiHandler:**
- Security path validation
- Security configuration
- Authentication type detection

**Estimated Effort:** 3 days

## Phase 2: Business Logic (Week 2) - Critical Components

### 2.1 CacheManagerBusinessReflectionImpl Tests
**File:** `src/test/java/org/terracotta/tinypounder/CacheManagerBusinessReflectionImplTest.java`

**Test Scenarios:**
- Cache manager initialization with valid config
- Cache manager initialization with invalid config
- Cache creation (heap, offheap, clustered dedicated, clustered shared)
- Cache removal
- Cache destruction
- Cache clearing
- Pounding intensity management
- Cache statistics retrieval
- Human-readable configuration output
- Error handling for reflection failures
- Concurrent cache operations
- Cache manager lifecycle (init → operations → close → destroy)

**Mocking Strategy:**
- Mock Ehcache classes via reflection
- Create test fixtures for common configurations
- Use temporary directories for disk persistence

**Estimated Effort:** 2 days

### 2.2 DatasetManagerBusinessReflectionImpl Tests
**File:** `src/test/java/org/terracotta/tinypounder/DatasetManagerBusinessReflectionImplTest.java`

**Test Scenarios:**
- Dataset manager initialization
- Dataset creation with various configurations
- Dataset removal
- Dataset destruction
- Dataset operations (put, get, delete)
- Error handling for reflection failures
- Dataset manager lifecycle

**Mocking Strategy:**
- Mock TCStore classes via reflection
- Create test fixtures for dataset configurations

**Estimated Effort:** 2 days

## Phase 3: Infrastructure (Week 3) - Medium Priority

### 3.1 TerracottaServerBusiness Tests
**File:** `src/test/java/org/terracotta/tinypounder/TerracottaServerBusinessTest.java`

**Test Scenarios:**
- Server process spawning
- Server startup with valid config
- Server startup with invalid config
- Server shutdown (graceful and forced)
- Multiple server management
- Console output capture
- Process monitoring
- Error handling for failed starts
- Port conflict detection

**Mocking Strategy:**
- Mock Process and ProcessBuilder
- Mock IPC EventBus for console output
- Use test server scripts instead of real Terracotta servers

**Estimated Effort:** 2 days

### 3.2 Servlet Tests
**Files:** `src/test/java/org/terracotta/tinypounder/servlet/*Test.java`

**ApiServlet Tests:**
- Request routing to correct handlers
- JSON request parsing
- JSON response formatting
- Error handling and error responses
- Invalid action handling

**StaticResourceServlet Tests:**
- Static file serving
- MIME type detection
- 404 handling
- Security (path traversal prevention)

**ConsoleWebSocketEndpoint Tests:**
- WebSocket connection establishment
- Message broadcasting
- Connection lifecycle
- Error handling

**Mocking Strategy:**
- Mock HttpServletRequest/Response
- Mock WebSocket Session
- Use embedded servlet container for integration tests

**Estimated Effort:** 2 days

## Phase 4: Utilities & Polish (Week 4) - Low Priority

### 4.1 ProcUtils Tests
**File:** `src/test/java/org/terracotta/tinypounder/ProcUtilsTest.java`

**Test Scenarios:**
- Process termination
- Process status checking
- Platform-specific behavior (Windows vs Unix)

**Estimated Effort:** 0.5 days

### 4.2 Configuration Classes Tests
**Files:** 
- `src/test/java/org/terracotta/tinypounder/CacheConfigurationTest.java`
- `src/test/java/org/terracotta/tinypounder/DatasetConfigurationTest.java`
- `src/test/java/org/terracotta/tinypounder/RunningServerTest.java`

**Test Scenarios:**
- Object creation and getters/setters
- Validation logic
- Serialization/deserialization

**Estimated Effort:** 0.5 days

### 4.3 Integration Tests
**File:** `src/test/java/org/terracotta/tinypounder/integration/EndToEndTest.java`

**Test Scenarios:**
- Complete workflow: configure → start servers → create cache manager → create cache → pound → destroy
- Multiple cache managers
- Server failover scenarios
- Security-enabled scenarios

**Estimated Effort:** 1 day

## Implementation Guidelines

### Test Infrastructure Setup

1. **Create test fixtures directory:** `src/test/resources/fixtures/`
   - Sample kit structures
   - Sample configuration files
   - Mock security directories

2. **Create test utilities:** `src/test/java/org/terracotta/tinypounder/testutil/`
   - `MockKitBuilder` - Creates mock kit directory structures
   - `TestConfigFactory` - Generates test configurations
   - `ReflectionTestHelper` - Utilities for testing reflection-based code

3. **Add test dependencies to pom.xml:**
   - Mockito for mocking (already present)
   - AssertJ for fluent assertions (already present)
   - JUnit 5 (already present)
   - Embedded servlet container (Jetty) for servlet tests

### Coverage Goals

- **Overall:** 70%+ line coverage
- **Critical classes:** 80%+ coverage
  - CacheManagerBusinessReflectionImpl
  - DatasetManagerBusinessReflectionImpl
  - TerracottaConfigGenerator
  - API Handlers
- **Utility classes:** 60%+ coverage

### Continuous Integration

- Run tests on every commit
- Fail build if coverage drops below threshold
- Generate coverage reports (JaCoCo)

## Total Estimated Effort

- **Phase 1:** 4 days
- **Phase 2:** 4 days  
- **Phase 3:** 4 days
- **Phase 4:** 2 days
- **Total:** ~14 days (3 weeks with buffer)

## Success Metrics

- All new tests passing
- Coverage reports showing 70%+ overall coverage
- No critical bugs found in untested code
- CI/CD pipeline running tests successfully

## Priority Order for Implementation

1. **TerracottaConfigGenerator** - Easy to test, high value, no external dependencies
2. **API Handlers** - Entry points for all functionality, critical for validation
3. **CacheManagerBusinessReflectionImpl** - Most complex business logic
4. **DatasetManagerBusinessReflectionImpl** - Similar to cache manager
5. **TerracottaServerBusiness** - Infrastructure component
6. **Servlets** - Integration layer
7. **Utilities** - Lower priority, simpler code

## Notes

- Focus on testing behavior, not implementation details
- Use mocking judiciously - prefer real objects when practical
- Write tests that are maintainable and easy to understand
- Document complex test scenarios with comments
- Consider property-based testing for configuration validation

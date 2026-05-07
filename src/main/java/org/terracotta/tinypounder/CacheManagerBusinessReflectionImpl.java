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


import static org.terracotta.tinypounder.CacheConfiguration.ClusterTierType.DEDICATED;
import static org.terracotta.tinypounder.CacheConfiguration.ClusterTierType.SHARED;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.URI;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public class CacheManagerBusinessReflectionImpl implements CacheManagerBusiness {

  private final KitAwareClassLoaderDelegator kitAwareClassLoaderDelegator;
  private final ConcurrentMap<String, Integer> poundingMap = new ConcurrentHashMap<>();
  private static final String NO_CACHE_MANAGER = "NO CACHE MANAGER";
  private Object cacheManager;
  private String defaultOffheapResource;
  private Class<?> ehCacheManagerClass;
  private Random random = new Random();

  public CacheManagerBusinessReflectionImpl(KitAwareClassLoaderDelegator kitAwareClassLoaderDelegator, ScheduledExecutorService poundingScheduler) throws Exception {
    this.kitAwareClassLoaderDelegator = kitAwareClassLoaderDelegator;
    poundingScheduler.scheduleWithFixedDelay(() -> poundingMap.entrySet().parallelStream().forEach(entryConsumer -> {
      try {
        Object cache = getCache(entryConsumer.getKey());
        if (cache != null && entryConsumer.getValue() > 0) {
          pound(cache, entryConsumer.getValue());
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }), 1000, 100, TimeUnit.MILLISECONDS);
  }

  private void pound(Object cache, Integer intensity) {
    IntStream.range(0, intensity).forEach(value -> {
      put(cache, ThreadLocalRandom.current().nextLong(0, intensity * 1000), longString(intensity));
      remove(cache, ThreadLocalRandom.current().nextLong(0, intensity * 100));
      IntStream.range(0, 3).forEach(getIterationValue -> get(cache, ThreadLocalRandom.current().nextLong(0, intensity * 1000)));
    });
  }

  private Object get(Object cache, long key) {
    try {
      Class<?> cacheClass = loadClass("org.ehcache.core.Ehcache");
      Method getCacheMethod = cacheClass.getMethod("get", Object.class);
      return getCacheMethod.invoke(cache, key);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private String longString(Integer intensity) {
    return new BigInteger(intensity * 10, random).toString(16);
  }

  private void put(Object cache, long key, String value) {
    try {
      Class<?> cacheClass = loadClass("org.ehcache.core.Ehcache");
      Method putCacheMethod = cacheClass.getMethod("put", Object.class, Object.class);
      putCacheMethod.invoke(cache, key, value);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void remove(Object cache, long key) {
    try {
      Class<?> cacheClass = loadClass("org.ehcache.core.Ehcache");
      Method removeCacheMethod = cacheClass.getMethod("remove", Object.class);
      removeCacheMethod.invoke(cache, key);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private Object getCache(String cacheAlias) {
    if (cacheManager == null) {
      return null;
    }
    try {
      Method getCacheMethod = ehCacheManagerClass.getMethod("getCache", String.class, Class.class, Class.class);
      return getCacheMethod.invoke(cacheManager, cacheAlias, Long.class, String.class);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private Class loadClass(String className) throws ClassNotFoundException {
    return kitAwareClassLoaderDelegator.getUrlClassLoader().loadClass(className);
  }

  @Override
  public Collection<String> retrieveCacheNames() {
    try {
      Thread.currentThread().setContextClassLoader(kitAwareClassLoaderDelegator.getUrlClassLoader());

      Class<?> configurationClass = loadClass("org.ehcache.config.Configuration");

      Method getRuntimeConfigurationMethod = ehCacheManagerClass.getMethod("getRuntimeConfiguration");
      Object runtimeConfiguration = getRuntimeConfigurationMethod.invoke(cacheManager);
      Method readableStringMethod = configurationClass.getMethod("getCacheConfigurations");
      Map cacheConfigurations = (Map) readableStringMethod.invoke(runtimeConfiguration);
      return cacheConfigurations.keySet();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void createCache(String alias, CacheConfiguration cacheConfiguration) {
    try {
      Thread.currentThread().setContextClassLoader(kitAwareClassLoaderDelegator.getUrlClassLoader());
      Class<?> cacheConfigurationClass = loadClass("org.ehcache.config.CacheConfiguration");
      Method createCacheMethod = ehCacheManagerClass.getMethod("createCache", String.class, cacheConfigurationClass);
      createCacheMethod.invoke(cacheManager, alias, defaultCacheConfigurationHeapOffHeapDedicatedClustered(cacheConfiguration));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void close() {
    try {
      Thread.currentThread().setContextClassLoader(kitAwareClassLoaderDelegator.getUrlClassLoader());
      Method closeMethod = ehCacheManagerClass.getMethod("close");
      closeMethod.invoke(cacheManager);
      poundingMap.clear();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void destroy() {
    try {
      Thread.currentThread().setContextClassLoader(kitAwareClassLoaderDelegator.getUrlClassLoader());
      Method destroyMethod = ehCacheManagerClass.getMethod("destroy");
      destroyMethod.invoke(cacheManager);
      poundingMap.clear();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void destroyCache(String alias) {
    try {
      Thread.currentThread().setContextClassLoader(kitAwareClassLoaderDelegator.getUrlClassLoader());
      Method destroyCacheMethod = ehCacheManagerClass.getMethod("destroyCache", String.class);
      destroyCacheMethod.invoke(cacheManager, alias);
      poundingMap.remove(alias);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void removeCache(String alias) {
    try {
      Thread.currentThread().setContextClassLoader(kitAwareClassLoaderDelegator.getUrlClassLoader());
      Method removeCacheMethod = ehCacheManagerClass.getMethod("removeCache", String.class);
      removeCacheMethod.invoke(cacheManager, alias);
      poundingMap.remove(alias);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void clearCache(String alias) {
    try {
      Thread.currentThread().setContextClassLoader(kitAwareClassLoaderDelegator.getUrlClassLoader());
      Object cache = getCache(alias);
      Class ehCacheClass = loadClass("org.ehcache.core.Ehcache");
      Method clearCacheMethod = ehCacheClass.getMethod("clear");
      clearCacheMethod.invoke(cache);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public String retrieveHumanReadableConfiguration() {
    try {
      Thread.currentThread().setContextClassLoader(kitAwareClassLoaderDelegator.getUrlClassLoader());

      Class<?> humanReadableClass = loadClass("org.ehcache.core.HumanReadable");

      Method getRuntimeConfigurationMethod = ehCacheManagerClass.getMethod("getRuntimeConfiguration");
      Object runtimeConfiguration = getRuntimeConfigurationMethod.invoke(cacheManager);
      Method readableStringMethod = humanReadableClass.getMethod("readableString");
      return (String) readableStringMethod.invoke(runtimeConfiguration);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void initializeCacheManager(String terracottaServerUrl, String cmName, String diskPersistenceLocation, String defaultOffheapResource, String serverDiskResource, String securityPath) {
    try {
      Thread.currentThread().setContextClassLoader(kitAwareClassLoaderDelegator.getUrlClassLoader());
      Object clusteringServiceConfigurationBuilder;
      if (terracottaServerUrl != null) {
        URI clusterUri = URI.create("terracotta://" + terracottaServerUrl + "/" + cmName);
        clusteringServiceConfigurationBuilder = constructClusteringServiceConfigurationBuilder(cmName, clusterUri, kitAwareClassLoaderDelegator.isEEKit(), defaultOffheapResource, serverDiskResource, securityPath);
      } else {
        clusteringServiceConfigurationBuilder = null;
      }

      Object cacheManagerPersistenceConfiguration;
      if (diskPersistenceLocation != null) {
        File tinyPounderDiskPersistenceLocationFolder = new File(diskPersistenceLocation);
        if (tinyPounderDiskPersistenceLocationFolder.exists()) {
          try {
            deleteFolder(diskPersistenceLocation);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
        cacheManagerPersistenceConfiguration = constructCacheManagerPersistenceConfiguration(tinyPounderDiskPersistenceLocationFolder);
      } else {
        cacheManagerPersistenceConfiguration = null;
      }
      Object cacheManager;
      Object defaultManagementRegistryConfiguration;
      try {
        defaultManagementRegistryConfiguration = constructDefaultManagementRegistryConfiguration(cmName);
      } catch (Exception e) {
        defaultManagementRegistryConfiguration = null;
      }
      cacheManager = constructCacheManagerBuilder(clusteringServiceConfigurationBuilder, cacheManagerPersistenceConfiguration, defaultManagementRegistryConfiguration);
      ehCacheManagerClass = loadClass("org.ehcache.core.EhcacheManager");
      Method initMethod = ehCacheManagerClass.getMethod("init");
      initMethod.invoke(cacheManager);
      this.cacheManager = cacheManager;
      this.defaultOffheapResource = defaultOffheapResource;
    } catch (InvocationTargetException e) {
      // Unwrap the actual exception from reflection
      Throwable cause = e.getCause();
      if (cause != null) {
        throw new RuntimeException("Failed to initialize cache manager: " + cause.getMessage(), cause);
      } else {
        throw new RuntimeException("Failed to initialize cache manager: " + e.getMessage(), e);
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to initialize cache manager: " + e.getMessage(), e);
    }
  }

  private Object constructDefaultManagementRegistryConfiguration(String cmName) throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
    Class<?> defaultManagementRegistryConfigurationClass = loadClass("org.ehcache.management.registry.DefaultManagementRegistryConfiguration");
    Constructor defaultManagementRegistryConfigurationConstructor = defaultManagementRegistryConfigurationClass.getConstructor();
    Object defaultManagementRegistryConfiguration = defaultManagementRegistryConfigurationConstructor.newInstance();
    Method addTagMethod = defaultManagementRegistryConfigurationClass.getMethod("addTag", String.class);
    defaultManagementRegistryConfiguration = addTagMethod.invoke(defaultManagementRegistryConfiguration, "tiny");
    defaultManagementRegistryConfiguration = addTagMethod.invoke(defaultManagementRegistryConfiguration, "pounder");
    defaultManagementRegistryConfiguration = addTagMethod.invoke(defaultManagementRegistryConfiguration, "client");

    Method setCacheManagerAliasMethod = defaultManagementRegistryConfigurationClass.getMethod("setCacheManagerAlias", String.class);
    defaultManagementRegistryConfiguration = setCacheManagerAliasMethod.invoke(defaultManagementRegistryConfiguration, cmName);
    return defaultManagementRegistryConfiguration;
  }

  private Object constructCacheManagerPersistenceConfiguration(File tinyPounderDiskPersistenceLocationFolder) throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
    Class<?> cacheManagerPersistenceConfigurationClass = loadClass("org.ehcache.impl.config.persistence.CacheManagerPersistenceConfiguration");
    Constructor cacheManagerPersistenceConfigurationConstructor = cacheManagerPersistenceConfigurationClass.getConstructor(File.class);
    return cacheManagerPersistenceConfigurationConstructor.newInstance(tinyPounderDiskPersistenceLocationFolder);
  }

  private Object constructCacheManagerBuilder(Object enterpriseClusteringServiceConfigurationBuilder,
                                              Object cacheManagerPersistenceConfiguration,
                                              Object defaultManagementRegistryConfiguration) throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    Class<?> cacheManagerBuilderClass = loadClass("org.ehcache.config.builders.CacheManagerBuilder");
    Method newCacheManagerBuilderMethod = cacheManagerBuilderClass.getMethod("newCacheManagerBuilder");
    Class<?> builderClass = loadClass("org.ehcache.config.Builder");
    Method withBuilderMethod = cacheManagerBuilderClass.getMethod("with", builderClass);
    Class<?> cacheManagerConfigurationClass = loadClass("org.ehcache.config.builders.CacheManagerConfiguration");
    Method withConfigurationMethod = cacheManagerBuilderClass.getMethod("with", cacheManagerConfigurationClass);
    Class<?> serviceCreationConfigurationClass = loadClass("org.ehcache.spi.service.ServiceCreationConfiguration");

    Object cacheManagerBuilder = newCacheManagerBuilderMethod.invoke(null);
    if (enterpriseClusteringServiceConfigurationBuilder != null) {
      cacheManagerBuilder = withBuilderMethod.invoke(cacheManagerBuilder, enterpriseClusteringServiceConfigurationBuilder);
    }
    if (cacheManagerPersistenceConfiguration != null) {
      cacheManagerBuilder = withConfigurationMethod.invoke(cacheManagerBuilder, cacheManagerPersistenceConfiguration);
    }
    if (defaultManagementRegistryConfiguration != null) {
      Method usingMethod = cacheManagerBuilderClass.getMethod("using", serviceCreationConfigurationClass);
      cacheManagerBuilder = usingMethod.invoke(cacheManagerBuilder, defaultManagementRegistryConfiguration);
    }
    Method buildMethod = cacheManagerBuilderClass.getMethod("build");
    return buildMethod.invoke(cacheManagerBuilder);
  }

  private Object constructClusteringServiceConfigurationBuilder(String clusterTierManagerName, URI clusterUri, boolean eeKit, String defaultOffheapResource, String serverDiskResource, String securityPath) throws IllegalAccessException, InvocationTargetException, ClassNotFoundException, NoSuchMethodException {
    Class<?> memoryUnitClass = loadClass("org.ehcache.config.units.MemoryUnit");
    Method valueOfMethod = memoryUnitClass.getMethod("valueOf", String.class);
    Object mB = valueOfMethod.invoke(null, "MB");

    Object timeoutsInstance = null;
    Class<?> timeoutsClass = null;
    try {
      timeoutsClass = loadClass("org.ehcache.clustered.client.config.Timeouts");
      Constructor timeoutsClassConstructor = timeoutsClass.getConstructor(new Class[]{Duration.class, Duration.class, Duration.class});
      timeoutsInstance = timeoutsClassConstructor.newInstance(Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofSeconds(120));
    } catch (Exception e) {
      // nevermind, the api wrt timeouts is fairly recent, 3.5.x, so if running an old ehcache, we won't configure timeouts; oh well...
    }

    if (eeKit) {
      Class<?> enterpriseServerSideConfigurationBuilderClass = loadClass("com.terracottatech.ehcache.clustered.client.config.builders.EnterpriseServerSideConfigurationBuilder");
      Class<?> enterpriseClusteringServiceConfigurationBuilderClass = loadClass("com.terracottatech.ehcache.clustered.client.config.builders.EnterpriseClusteringServiceConfigurationBuilder");

      Method autoCreateMethod = enterpriseClusteringServiceConfigurationBuilderClass.getMethod("autoCreate");
      Method defaultServerResourceMethod = enterpriseServerSideConfigurationBuilderClass.getMethod("defaultServerResource", String.class);
      Method resourcePoolMethod4 = enterpriseServerSideConfigurationBuilderClass.getMethod("resourcePool", String.class, long.class, memoryUnitClass, String.class);
      Method resourcePoolMethod3 = enterpriseServerSideConfigurationBuilderClass.getMethod("resourcePool", String.class, long.class, memoryUnitClass);
      Method restartableMethod = enterpriseServerSideConfigurationBuilderClass.getMethod("restartable", String.class);

      Object enterpriseClusteringServiceConfigurationBuilder;
      if (securityPath != null) {
        Method enterpriseClusterMethod = enterpriseClusteringServiceConfigurationBuilderClass.getMethod("enterpriseSecureCluster", URI.class, Path.class);
        enterpriseClusteringServiceConfigurationBuilder = enterpriseClusterMethod.invoke(null, clusterUri.resolve(clusterTierManagerName), Paths.get(securityPath));
      } else {
        Method enterpriseClusterMethod = enterpriseClusteringServiceConfigurationBuilderClass.getMethod("enterpriseCluster", URI.class);
        enterpriseClusteringServiceConfigurationBuilder = enterpriseClusterMethod.invoke(null, clusterUri.resolve(clusterTierManagerName));
      }

      if (timeoutsClass != null && timeoutsInstance != null) {
        Method timeoutsMethod = enterpriseClusteringServiceConfigurationBuilderClass.getMethod("timeouts", timeoutsClass);
        enterpriseClusteringServiceConfigurationBuilder = timeoutsMethod.invoke(enterpriseClusteringServiceConfigurationBuilder, timeoutsInstance);
      }

      Object enterpriseServerSideConfigurationBuilder = autoCreateMethod.invoke(enterpriseClusteringServiceConfigurationBuilder);
      enterpriseServerSideConfigurationBuilder = defaultServerResourceMethod.invoke(enterpriseServerSideConfigurationBuilder, defaultOffheapResource);
      enterpriseServerSideConfigurationBuilder = resourcePoolMethod4.invoke(enterpriseServerSideConfigurationBuilder, "resource-pool-a", 128L, mB, defaultOffheapResource);
      enterpriseServerSideConfigurationBuilder = resourcePoolMethod3.invoke(enterpriseServerSideConfigurationBuilder, "resource-pool-b", 64L, mB);
      return restartableMethod.invoke(enterpriseServerSideConfigurationBuilder, serverDiskResource);
    } else {

      Class<?> serverSideConfigurationBuilderClass = loadClass("org.ehcache.clustered.client.config.builders.ServerSideConfigurationBuilder");
      Class<?> clusteringServiceConfigurationBuilderClass = loadClass("org.ehcache.clustered.client.config.builders.ClusteringServiceConfigurationBuilder");
      Method clusterMethod = clusteringServiceConfigurationBuilderClass.getMethod("cluster", URI.class);

      Method autoCreateMethod = clusteringServiceConfigurationBuilderClass.getMethod("autoCreate");
      Method defaultServerResourceMethod = serverSideConfigurationBuilderClass.getMethod("defaultServerResource", String.class);
      Method resourcePoolMethod4 = serverSideConfigurationBuilderClass.getMethod("resourcePool", String.class, long.class, memoryUnitClass, String.class);
      Method resourcePoolMethod3 = serverSideConfigurationBuilderClass.getMethod("resourcePool", String.class, long.class, memoryUnitClass);

      Object clusteringServiceConfigurationBuilder = clusterMethod.invoke(null, clusterUri.resolve(clusterTierManagerName));
      if (timeoutsClass != null && timeoutsInstance != null) {
        Method timeoutsMethod = clusteringServiceConfigurationBuilderClass.getMethod("timeouts", timeoutsClass);
        clusteringServiceConfigurationBuilder = timeoutsMethod.invoke(clusteringServiceConfigurationBuilder, timeoutsInstance);
      }
      Object serverSideConfigurationBuilder = autoCreateMethod.invoke(clusteringServiceConfigurationBuilder);
      serverSideConfigurationBuilder = defaultServerResourceMethod.invoke(serverSideConfigurationBuilder, defaultOffheapResource);
      serverSideConfigurationBuilder = resourcePoolMethod4.invoke(serverSideConfigurationBuilder, "resource-pool-a", 128L, mB, defaultOffheapResource);
      serverSideConfigurationBuilder = resourcePoolMethod3.invoke(serverSideConfigurationBuilder, "resource-pool-b", 64L, mB);
      return serverSideConfigurationBuilder;
    }
  }

  private Object defaultCacheConfigurationHeapOffHeapDedicatedClustered(CacheConfiguration cacheConfiguration) throws Exception {
    Class<?> cacheConfigurationBuilderClass = loadClass("org.ehcache.config.builders.CacheConfigurationBuilder");
    Class<?> builderClass = loadClass("org.ehcache.config.Builder");
    Method newCacheConfigurationBuilderMethod = cacheConfigurationBuilderClass.getMethod("newCacheConfigurationBuilder", Class.class, Class.class, builderClass);
    Object resourcePoolsBuilder = defaultCacheConfigurationCacheResourcePoolBuilder(kitAwareClassLoaderDelegator.isEEKit(), cacheConfiguration);
    Object cacheConfigurationBuilder = newCacheConfigurationBuilderMethod.invoke(null, Long.class, String.class, resourcePoolsBuilder);

    try {
      Class<?> storeStatisticsConfigurationClass = loadClass("org.ehcache.core.config.store.StoreStatisticsConfiguration");
      Object storeStatisticsConfiguration = storeStatisticsConfigurationClass.getConstructor(boolean.class).newInstance(true);
      Method add = cacheConfigurationBuilderClass.getMethod("add", loadClass("org.ehcache.spi.service.ServiceConfiguration"));
      add.invoke(cacheConfigurationBuilder, storeStatisticsConfiguration);
    } catch (Exception e) {
      System.out.println("Unable to call: add(new StoreStatisticsConfiguration(true))");
    }

    Method buildMethod = cacheConfigurationBuilderClass.getMethod("build");
    return buildMethod.invoke(cacheConfigurationBuilder);
  }

  private Object defaultCacheConfigurationCacheResourcePoolBuilder(boolean eeKit, CacheConfiguration cacheConfiguration) throws Exception {
    Class<?> resourcePoolsBuilderClass = loadClass("org.ehcache.config.builders.ResourcePoolsBuilder");
    Class<?> resourceUnitClass = loadClass("org.ehcache.config.ResourceUnit");
    Class<?> memoryUnitClass = loadClass("org.ehcache.config.units.MemoryUnit");
    Class<?> entryUnitClass = loadClass("org.ehcache.config.units.EntryUnit");
    Method memoryUnitClassMethod = memoryUnitClass.getMethod("valueOf", String.class);
    Method entryUnitClassMethod = entryUnitClass.getMethod("valueOf", String.class);
    Method newResourcePoolsBuilder = resourcePoolsBuilderClass.getMethod("newResourcePoolsBuilder");
    Object resourcePoolsBuilder = newResourcePoolsBuilder.invoke(null);

    if (cacheConfiguration.getOnHeapSize() > 0) {
      Method heapMethod = resourcePoolsBuilderClass.getMethod("heap", long.class, resourceUnitClass);
      Object onHeapSizeUnit;
      if (cacheConfiguration.getOnHeapSizeUnit().equals("ENTRIES")) {
        onHeapSizeUnit = entryUnitClassMethod.invoke(null, cacheConfiguration.getOnHeapSizeUnit());
      } else {
        onHeapSizeUnit = memoryUnitClassMethod.invoke(null, cacheConfiguration.getOnHeapSizeUnit());
      }
      resourcePoolsBuilder = heapMethod.invoke(resourcePoolsBuilder, cacheConfiguration.getOnHeapSize(), onHeapSizeUnit);
    }
    if (cacheConfiguration.getOffHeapSize() > 0) {
      Object offHeapSizeUnit = memoryUnitClassMethod.invoke(null, cacheConfiguration.getOffHeapSizeUnit());
      Method offHeapMethod = resourcePoolsBuilderClass.getMethod("offheap", long.class, memoryUnitClass);
      resourcePoolsBuilder = offHeapMethod.invoke(resourcePoolsBuilder, cacheConfiguration.getOffHeapSize(), offHeapSizeUnit);
    }
    if (cacheConfiguration.getDiskSize() > 0) {
      Object diskSizeUnit = memoryUnitClassMethod.invoke(null, cacheConfiguration.getDiskSizeUnit());
      Method diskMethod = resourcePoolsBuilderClass.getMethod("disk", long.class, memoryUnitClass);
      resourcePoolsBuilder = diskMethod.invoke(resourcePoolsBuilder, cacheConfiguration.getDiskSize(), diskSizeUnit);
    }

    Class<?> resourcePoolClass = loadClass("org.ehcache.config.ResourcePool");
    Method withMethod = resourcePoolsBuilderClass.getMethod("with", resourcePoolClass);

    if (cacheConfiguration.getClusteredTierType().equals(DEDICATED) && cacheConfiguration.getClusteredDedicatedSize() > 0) {
      Object clusteredDedicatedResourcePoolImpl;
      Object clusteredDedicatedSizeUnit = memoryUnitClassMethod.invoke(null, cacheConfiguration.getClusteredDedicatedUnit());
      if (eeKit) {
        Class<?> enterpriseClusteredDedicatedResourcePoolImplClass = loadClass("com.terracottatech.ehcache.clustered.client.internal.config.EnterpriseClusteredDedicatedResourcePoolImpl");
        Constructor<?> enterpriseClusteredDedicatedResourcePoolImplConstructor = enterpriseClusteredDedicatedResourcePoolImplClass.getConstructor(String.class, long.class, memoryUnitClass);
        clusteredDedicatedResourcePoolImpl = enterpriseClusteredDedicatedResourcePoolImplConstructor.newInstance(defaultOffheapResource, cacheConfiguration.getClusteredDedicatedSize(), clusteredDedicatedSizeUnit);
      } else {
        Class<?> clusteredDedicatedResourcePoolImplClass = loadClass("org.ehcache.clustered.client.internal.config.DedicatedClusteredResourcePoolImpl");
        Constructor<?> enterpriseClusteredDedicatedResourcePoolImplConstructor = clusteredDedicatedResourcePoolImplClass.getConstructor(String.class, long.class, memoryUnitClass);
        clusteredDedicatedResourcePoolImpl = enterpriseClusteredDedicatedResourcePoolImplConstructor.newInstance(defaultOffheapResource, cacheConfiguration.getClusteredDedicatedSize(), clusteredDedicatedSizeUnit);
      }
      resourcePoolsBuilder = withMethod.invoke(resourcePoolsBuilder, clusteredDedicatedResourcePoolImpl);
    } else if (cacheConfiguration.getClusteredTierType().equals(SHARED) && cacheConfiguration.getClusteredSharedPoolName() != null) {
      Object clusteredsharedResourcePoolImpl;
      if (eeKit) {
        Class<?> enterpriseClusteredDedicatedResourcePoolImplClass = loadClass("com.terracottatech.ehcache.clustered.client.internal.config.EnterpriseClusteredSharedResourcePoolImpl");
        Constructor<?> enterpriseClusteredDedicatedResourcePoolImplConstructor = enterpriseClusteredDedicatedResourcePoolImplClass.getConstructor(String.class);
        clusteredsharedResourcePoolImpl = enterpriseClusteredDedicatedResourcePoolImplConstructor.newInstance(cacheConfiguration.getClusteredSharedPoolName());
      } else {
        Class<?> clusteredDedicatedResourcePoolImplClass = loadClass("org.ehcache.clustered.client.internal.config.SharedClusteredResourcePoolImpl");
        Constructor<?> enterpriseClusteredDedicatedResourcePoolImplConstructor = clusteredDedicatedResourcePoolImplClass.getConstructor(String.class);
        clusteredsharedResourcePoolImpl = enterpriseClusteredDedicatedResourcePoolImplConstructor.newInstance(cacheConfiguration.getClusteredSharedPoolName());
      }
      resourcePoolsBuilder = withMethod.invoke(resourcePoolsBuilder, clusteredsharedResourcePoolImpl);
    }

    return resourcePoolsBuilder;
  }

  private void deleteFolder(String tinyPounderDiskPersistenceLocation) throws IOException {
    Path rootPath = Paths.get(tinyPounderDiskPersistenceLocation);
    Files.walk(rootPath, FileVisitOption.FOLLOW_LINKS)
        .sorted(Comparator.reverseOrder())
        .map(Path::toFile)
        .forEach(File::delete);
  }

  @Override
  public boolean isCacheManagerAlive() {
    return !getStatus().equals("UNINITIALIZED") && !getStatus().equals(NO_CACHE_MANAGER);
  }

  @Override
  public String getStatus() {

    if (cacheManager == null) {
      return NO_CACHE_MANAGER;
    }

    try {
      Thread.currentThread().setContextClassLoader(kitAwareClassLoaderDelegator.getUrlClassLoader());
      Method getStatusMethod = ehCacheManagerClass.getMethod("getStatus");
      Object status = getStatusMethod.invoke(cacheManager);
      return status.toString();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void updatePoundingIntensity(String cacheAlias, int poundingIntensity) {
    poundingMap.put(cacheAlias, poundingIntensity);
  }

  @Override
  public int retrievePoundingIntensity(String cacheAlias) {
    return poundingMap.getOrDefault(cacheAlias, 0);
  }
}

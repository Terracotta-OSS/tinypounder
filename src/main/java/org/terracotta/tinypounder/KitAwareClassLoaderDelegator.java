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


import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class KitAwareClassLoaderDelegator {

  private ClassLoader urlClassLoader = Thread.currentThread().getContextClassLoader();

  public ClassLoader getUrlClassLoader() {
    return urlClassLoader;
  }

  private static URL[] discoverKitClientJars(String kitPath) throws Exception {
    // this map helps avoiding errors like this one by avoiding putting duplicated jars in classpath:
    // MULTIPLE instances of org.terracotta.lease.LeaseAcquirerClientService found, ignoring:file:/Users/mathieu/Downloads/terracotta-db-10.3.0-SNAPSHOT/client/lib/terracotta-common-client-10.3.0-SNAPSHOT.jar keeping:file:/Users/mathieu/Downloads/terracotta-db-10.3.0-SNAPSHOT/client/ehcache/terracotta-ehcache-client-10.3.0-SNAPSHOT.jar using classloader:java.net.FactoryURLClassLoader@6505414e
    Map<String, File> libraries = new LinkedHashMap<>();
    try {
      Files.walk(Paths.get(kitPath, "/client/ehcache"), 4, FileVisitOption.FOLLOW_LINKS)
          .map(Path::toFile)
          .filter(file -> file.getName().endsWith(".jar"))
          .forEach(file -> libraries.put(hash(file), file.getAbsoluteFile()));
    } catch (Exception e) {
      // that's fine, it's probably just a kit without ehcache
    }
    try {
      Files.walk(Paths.get(kitPath, "/client/store"), 4, FileVisitOption.FOLLOW_LINKS)
          .map(Path::toFile)
          .filter(file -> file.getName().endsWith(".jar"))
          .forEach(file -> libraries.put(hash(file), file.getAbsoluteFile()));
    } catch (IOException e) {
      // that's fine, it's probably just a kit without tc store
    }
    try {
      Files.walk(Paths.get(kitPath, "/client/lib"), 4, FileVisitOption.FOLLOW_LINKS)
          .map(Path::toFile)
          .filter(file -> file.getName().endsWith(".jar"))
          .forEach(file -> libraries.put(hash(file), file.getAbsoluteFile()));
    } catch (IOException e) {
      // it's possible that the client libs are elsewhere...
      Files.walk(Paths.get(kitPath, "../common/lib"), 4, FileVisitOption.FOLLOW_LINKS)
          .map(Path::toFile)
          .filter(file -> file.getName().endsWith("-client.jar"))
          .forEach(file -> libraries.put(hash(file), file.getAbsoluteFile()));
    }
    return libraries.values().stream().map(file -> {
      try {
        return file.toURI().toURL();
      } catch (MalformedURLException e) {
        throw new AssertionError(e);
      }
    }).toArray(URL[]::new);
  }

  private static String hash(File file) {
    try {
      byte[] digest = MessageDigest.getInstance("MD5").digest(Files.readAllBytes(file.toPath()));
      StringBuilder hexString = new StringBuilder();
      for (byte b : digest) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) hexString.append('0');
        hexString.append(hex);
      }
      return hexString.toString().toUpperCase();
    } catch (Exception e) {
      return file.getName();
    }
  }

  public boolean isDynamicConfig(String kitPath) {
    return Paths.get(kitPath, "tools", "bin", "config-tool.sh").toFile().exists();
  }

  public boolean containsTerracottaStore() {
    try {
      urlClassLoader.loadClass("com.terracottatech.store.manager.DatasetManager");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  public boolean containsEhcache() {
    try {
      urlClassLoader.loadClass("org.ehcache.CacheManager");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  public boolean isEEKit() {
    try {
      urlClassLoader.loadClass("com.terracottatech.ehcache.clustered.client.internal.config.EnterpriseClusteredDedicatedResourcePoolImpl");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  public boolean verifySecurityPath(String securityPath) {
    if (securityPath != null && !securityPath.isEmpty()) {
      Path path = Paths.get(securityPath);
      File securityDirectory = path.toFile();
      return securityDirectory.exists()
          && securityDirectory.isDirectory()
          && Arrays.stream(securityDirectory.list()).anyMatch(s -> s.contains("access-control") || s.contains("identity") || s.contains("trusted-authority"));
    }
    return false;
  }

  public void setAndVerifyKitPathAndClassLoader(String kitPath) {
    if (kitPath != null && !kitPath.isEmpty()) {
      try {
        URL[] urls = discoverKitClientJars(kitPath);
        urlClassLoader = URLClassLoader.newInstance(urls, Thread.currentThread().getContextClassLoader());
      } catch (Exception e) {
        throw new RuntimeException("Please make sure the kitPath is properly set, current value is : " + kitPath, e);
      }
    }
  }
}

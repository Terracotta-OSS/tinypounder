package org.terracotta.tinypounder.servlet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terracotta.tinypounder.CacheManagerBusiness;
import org.terracotta.tinypounder.CacheManagerBusinessReflectionImpl;
import org.terracotta.tinypounder.DatasetManagerBusinessReflectionImpl;
import org.terracotta.tinypounder.KitAwareClassLoaderDelegator;
import org.terracotta.tinypounder.Settings;
import org.terracotta.tinypounder.TerracottaServerBusiness;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Servlet context listener for TinyPounder application initialization and cleanup.
 * Replaces Spring Boot's application context and bean management.
 */
@WebListener
public class TinyPounderContextListener implements ServletContextListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(TinyPounderContextListener.class);

  public static final String SETTINGS_ATTR = "tinypounder.settings";
  public static final String KIT_CLASSLOADER_ATTR = "tinypounder.kitClassLoader";
  public static final String CACHE_MANAGER_BUSINESS_ATTR = "tinypounder.cacheManagerBusiness";
  public static final String DATASET_MANAGER_BUSINESS_ATTR = "tinypounder.datasetManagerBusiness";
  public static final String SERVER_BUSINESS_ATTR = "tinypounder.serverBusiness";
  public static final String EXECUTOR_SERVICE_ATTR = "tinypounder.executorService";

  private ScheduledExecutorService executorService;

  @Override
  public void contextInitialized(ServletContextEvent sce) {
    LOGGER.info("Initializing TinyPounder application...");
    ServletContext context = sce.getServletContext();

    try {
      // Create executor service
      executorService = Executors.newScheduledThreadPool(
          Runtime.getRuntime().availableProcessors(),
          r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
          }
      );
      context.setAttribute(EXECUTOR_SERVICE_ATTR, executorService);

      // Initialize Settings
      Settings settings = new Settings();
      settings.load();
      context.setAttribute(SETTINGS_ATTR, settings);
      LOGGER.info("Settings loaded from: {}", settings.getConfigFile());
      
      // Add JVM shutdown hook to save settings on Ctrl+C (SIGINT)
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        LOGGER.info("JVM shutdown hook triggered - saving settings...");
        try {
          settings.save();
          LOGGER.info("Settings saved successfully during shutdown");
        } catch (Exception e) {
          LOGGER.error("Failed to save settings during shutdown", e);
        }
      }, "TinyPounder-Settings-Saver"));

      // Initialize KitAwareClassLoaderDelegator
      KitAwareClassLoaderDelegator kitClassLoader = new KitAwareClassLoaderDelegator();
      if (settings.getKitPath() != null) {
        try {
          kitClassLoader.setAndVerifyKitPathAndClassLoader(settings.getKitPath());
          LOGGER.info("Kit loaded from: {}", settings.getKitPath());
        } catch (Exception e) {
          LOGGER.warn("Failed to load kit from configured path: {}", settings.getKitPath(), e);
        }
      }
      context.setAttribute(KIT_CLASSLOADER_ATTR, kitClassLoader);

      // Initialize business objects
      CacheManagerBusiness cacheManagerBusiness = new CacheManagerBusinessReflectionImpl(kitClassLoader, executorService);
      context.setAttribute(CACHE_MANAGER_BUSINESS_ATTR, cacheManagerBusiness);

      DatasetManagerBusinessReflectionImpl datasetManagerBusiness = 
          new DatasetManagerBusinessReflectionImpl(kitClassLoader, executorService);
      context.setAttribute(DATASET_MANAGER_BUSINESS_ATTR, datasetManagerBusiness);

      TerracottaServerBusiness serverBusiness = new TerracottaServerBusiness();
      context.setAttribute(SERVER_BUSINESS_ATTR, serverBusiness);

      LOGGER.info("TinyPounder application initialized successfully");
      LOGGER.info("Access the application at: http://localhost:{}", 
          context.getInitParameter("server.port") != null ? 
          context.getInitParameter("server.port") : "9490");

    } catch (Exception e) {
      LOGGER.error("Failed to initialize TinyPounder application", e);
      throw new RuntimeException("Application initialization failed", e);
    }
  }

  @Override
  public void contextDestroyed(ServletContextEvent sce) {
    LOGGER.info("Shutting down TinyPounder application...");
    ServletContext context = sce.getServletContext();

    try {
      // Save settings
      Settings settings = (Settings) context.getAttribute(SETTINGS_ATTR);
      if (settings != null) {
        settings.save();
        LOGGER.info("Settings saved");
      }

      // Close cache manager
      CacheManagerBusiness cacheManagerBusiness = 
          (CacheManagerBusiness) context.getAttribute(CACHE_MANAGER_BUSINESS_ATTR);
      if (cacheManagerBusiness != null && cacheManagerBusiness.isCacheManagerAlive()) {
        try {
          cacheManagerBusiness.close();
          LOGGER.info("Cache manager closed");
        } catch (Exception e) {
          LOGGER.warn("Error closing cache manager", e);
        }
      }

      // Close dataset manager
      DatasetManagerBusinessReflectionImpl datasetManagerBusiness = 
          (DatasetManagerBusinessReflectionImpl) context.getAttribute(DATASET_MANAGER_BUSINESS_ATTR);
      if (datasetManagerBusiness != null && datasetManagerBusiness.isDatasetManagerAlive()) {
        try {
          datasetManagerBusiness.close();
          LOGGER.info("Dataset manager closed");
        } catch (Exception e) {
          LOGGER.warn("Error closing dataset manager", e);
        }
      }

      // Shutdown executor service
      if (executorService != null) {
        executorService.shutdownNow();
        LOGGER.info("Executor service shut down");
      }

      LOGGER.info("TinyPounder application shut down successfully");

    } catch (Exception e) {
      LOGGER.error("Error during application shutdown", e);
    }
  }
}

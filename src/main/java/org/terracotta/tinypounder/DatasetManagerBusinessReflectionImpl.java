package org.terracotta.tinypounder;



import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class DatasetManagerBusinessReflectionImpl {

  private final ConcurrentMap<String, Integer> poundingMap = new ConcurrentHashMap<>();
  private static final String NO_DATASET_MANAGER = "NO DATASET MANAGER";
  private final KitAwareClassLoaderDelegator kitAwareClassLoaderDelegator;
  
  public DatasetManagerBusinessReflectionImpl(KitAwareClassLoaderDelegator kitAwareClassLoaderDelegator) {
    this.kitAwareClassLoaderDelegator = kitAwareClassLoaderDelegator;
  }
  private Class<?> datasetManagerClass;
  private Object datasetManager;
  private final Map<String, Map<String, Object>> datasetInstancesByDatasetName = new TreeMap<>();
  private final static String DATASET_INSTANCE_PATTERN = "^(.*)-[0-9]*$";
  private Random random = new Random();
  private Object stringCellDefinition;
  private Class<?> cellDefinitionClass;
  private Class<?> typeClass;
  private Set<String> customCells = (new ConcurrentHashMap<>()).newKeySet();

  public DatasetManagerBusinessReflectionImpl(KitAwareClassLoaderDelegator kitAwareClassLoaderDelegator, ScheduledExecutorService poundingScheduler) throws Exception {
    this.kitAwareClassLoaderDelegator = kitAwareClassLoaderDelegator;
    poundingScheduler.scheduleWithFixedDelay(() -> poundingMap.entrySet().parallelStream().forEach(entryConsumer -> {
      try {
        Object datasetInstance = retrieveDatasetInstance(entryConsumer.getKey());
        if (datasetInstance != null && entryConsumer.getValue() > 0) {
          pound(datasetInstance, entryConsumer.getValue());
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }), 1000, 100, TimeUnit.MILLISECONDS);
  }

  private void pound(Object datasetInstance, Integer intensity) {
    IntStream.range(0, intensity).forEach(value -> {
      insert(datasetInstance, ThreadLocalRandom.current().nextLong(0, intensity * 1000), intensity);
      update(datasetInstance, ThreadLocalRandom.current().nextLong(0, intensity * 1000), intensity);
      delete(datasetInstance, ThreadLocalRandom.current().nextLong(0, intensity * 1000));
      stream(datasetInstance);
      retrieve(datasetInstance, ThreadLocalRandom.current().nextLong(0, intensity * 1000));
    });
    try {
      generateRandomFailure(datasetInstance);
    } catch (Exception e) {
      // of course, we were generating a failure !
    }
  }

  private void generateRandomFailure(Object datasetInstance) {
    int nextInt = ThreadLocalRandom.current().nextInt(0, 5);
    switch (nextInt) {
      case 0:
        insert(datasetInstance, null, 0);
        break;
      case 1:
        update(datasetInstance, null, 0);
        break;
      case 2:
        retrieve(datasetInstance, null);
        break;
      case 3:
        delete(datasetInstance, null);
        break;
      case 4:
        recordsFailure(datasetInstance);
        break;
    }

  }

  private Object swapUnderlying(Object datasetReader, Object datasetReaderToReplaceWith) {
    // special thanks to Henri ;-)  !
    try {
      Thread.currentThread().setContextClassLoader(kitAwareClassLoaderDelegator.getUrlClassLoader());
      Class<?> statisticsDatasetReaderClass = loadClass("com.terracottatech.store.statistics.StatisticsDatasetReader");
      Field underlying = statisticsDatasetReaderClass.getDeclaredField("underlying");
      underlying.setAccessible(true);
      Object current = underlying.get(datasetReader);
      underlying.set(datasetReader, datasetReaderToReplaceWith);
      return current;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }


  private void recordsFailure(Object datasetInstance) {
    // dataset.records()
    try {
      Thread.currentThread().setContextClassLoader(kitAwareClassLoaderDelegator.getUrlClassLoader());
      Object datasetWriterReader = retrieveDatasetWriterReader(datasetInstance);
      Object formerUnderlying = swapUnderlying(datasetWriterReader, null);
      try {
        Class<?> datasetWriterReaderClass = loadClass("com.terracottatech.store.DatasetWriterReader");
        Method recordsMethod = datasetWriterReaderClass.getMethod("records");
        recordsMethod.invoke(datasetWriterReader);
      } catch (Exception e) {
        // well, playing with fire, you get burnt ! we need to reswap now !
      }
      swapUnderlying(datasetWriterReader, formerUnderlying);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void stream(Object datasetInstance) {
    // dataset.records().filter(stringCellDefinition.value().is("0)).count()
    try {
      Thread.currentThread().setContextClassLoader(kitAwareClassLoaderDelegator.getUrlClassLoader());
      Object datasetWriterReader = retrieveDatasetWriterReader(datasetInstance);
      Class<?> datasetWriterReaderClass = loadClass("com.terracottatech.store.DatasetWriterReader");
      Method recordsMethod = datasetWriterReaderClass.getMethod("records");
      Object records = recordsMethod.invoke(datasetWriterReader);

      Class<?> mutableRecordStreamClass = loadClass("com.terracottatech.store.stream.MutableRecordStream");
      Method filterMethod = mutableRecordStreamClass.getMethod("filter", Predicate.class);
      Method countMethod = mutableRecordStreamClass.getMethod("count");

      Method valueMethod = cellDefinitionClass.getMethod("value");
      Object value = valueMethod.invoke(stringCellDefinition);

      Class<?> buildableOptionalFunctionClass = loadClass("com.terracottatech.store.function.BuildableStringOptionalFunction");
      Method isMethod = buildableOptionalFunctionClass.getMethod("is", Object.class);
      Object isPredicate = isMethod.invoke(value, "0");

      Object filter = filterMethod.invoke(records, isPredicate);
      countMethod.invoke(filter);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void delete(Object datasetInstance, Long key) {
    try {
      Thread.currentThread().setContextClassLoader(kitAwareClassLoaderDelegator.getUrlClassLoader());
      Object datasetWriterReader = retrieveDatasetWriterReader(datasetInstance);
      Class<?> datasetWriterReaderClass = loadClass("com.terracottatech.store.DatasetWriterReader");
      Method deleteMethod = datasetWriterReaderClass.getMethod("delete", Comparable.class);
      deleteMethod.invoke(datasetWriterReader, longToKeyType(getKeyTypeJDK(datasetInstance), key));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void update(Object datasetInstance, Long key, Integer value) {
    try {
      Thread.currentThread().setContextClassLoader(kitAwareClassLoaderDelegator.getUrlClassLoader());
      Object datasetWriterReader = retrieveDatasetWriterReader(datasetInstance);
      Class<?> datasetWriterReaderClass = loadClass("com.terracottatech.store.DatasetWriterReader");

      Class<?> updateOperationClass = loadClass("com.terracottatech.store.UpdateOperation");
      Method writeMethod = updateOperationClass.getMethod("write", String.class, Object.class);
      Object writeOperation = writeMethod.invoke(null, "myStringCell", longString(value));

      Method updateMethod = datasetWriterReaderClass.getMethod("update", Comparable.class, updateOperationClass);

      updateMethod.invoke(datasetWriterReader, longToKeyType(getKeyTypeJDK(datasetInstance), key), writeOperation);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void retrieve(Object datasetInstance, Long key) {
    try {
      Thread.currentThread().setContextClassLoader(kitAwareClassLoaderDelegator.getUrlClassLoader());
      Object datasetWriterReader = retrieveDatasetWriterReader(datasetInstance);
      Class<?> datasetWriterReaderClass = loadClass("com.terracottatech.store.DatasetWriterReader");
      Method getMethod = datasetWriterReaderClass.getMethod("get", Comparable.class);
      getMethod.invoke(datasetWriterReader, longToKeyType(getKeyTypeJDK(datasetInstance), key));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private Object retrieveDatasetWriterReader(Object datasetInstance) throws Exception {
    Class<?> internalDatasetClass = loadClass("com.terracottatech.store.internal.ManagementInternalDataset");
    Method writerReader = internalDatasetClass.getMethod("writerReader");
    return writerReader.invoke(datasetInstance);
  }

  private Object retrieveDatasetReader(Object datasetInstance) throws Exception {
    Class<?> internalDatasetClass = loadClass("com.terracottatech.store.internal.ManagementInternalDataset");
    Method reader = internalDatasetClass.getMethod("reader");
    return reader.invoke(datasetInstance);
  }

  private Object longToKeyType(Class keyTypeJDK, Long internalKey) {
    Object key;
    if (keyTypeJDK.equals(String.class)) {
      key = Long.toString(internalKey);
    } else if (keyTypeJDK.equals(Integer.class)) {
      key = internalKey.intValue();
    } else if (keyTypeJDK.equals(Long.class)) {
      key = internalKey;
    } else if (keyTypeJDK.equals(Double.class)) {
      key = internalKey.doubleValue();
    } else if (keyTypeJDK.equals(Boolean.class)) {
      key = internalKey % 2 == 0;
    } else if (keyTypeJDK.equals(Character.class)) {
      key = Long.toString(internalKey).charAt(0);
    } else {
      key = null;
    }
    return key;
  }

  private Class<?> getKeyTypeJDK(Object datasetInstance) throws Exception {
    Class<?> datasetReaderClass = loadClass("com.terracottatech.store.DatasetReader");
    Method getKeyTypeMethod = datasetReaderClass.getMethod("getKeyType");
    Object datasetReader = retrieveDatasetReader(datasetInstance);
    Object keyType = getKeyTypeMethod.invoke(datasetReader);
    Method getJDKTypeMethod = typeClass.getDeclaredMethod("getJDKType");
    return (Class<?>) getJDKTypeMethod.invoke(keyType);
  }

  private void insert(Object datasetInstance, Long key, Integer value) {
    try {
      Thread.currentThread().setContextClassLoader(kitAwareClassLoaderDelegator.getUrlClassLoader());
      Object datasetWriterReader = retrieveDatasetWriterReader(datasetInstance);
      Class<?> datasetWriterReaderClass = loadClass("com.terracottatech.store.DatasetWriterReader");

      Class<?> cellClass = loadClass("com.terracottatech.store.Cell");
      Class cellClasses = Array.newInstance(cellClass, 0).getClass();
      Method addMethod = datasetWriterReaderClass.getMethod("add", Comparable.class, cellClasses);

      Object[] generatedCells;
      if (key == null || key % 2 == 0) {
        generatedCells = generateOneCell(value, cellClass);
      } else {
        generatedCells = generateTwoCells(value, cellClass);
      }
      Object[] customCells = generateCustomCells(value);
      Object[] cells = (Object[]) Array.newInstance(cellClass, generatedCells.length + customCells.length);
      int i = 0;
      for (Object c : generatedCells) {
        cells[i++] = c;
      }
      for (Object c : customCells) {
        cells[i++] = c;
      }
      addMethod.invoke(datasetWriterReader, longToKeyType(getKeyTypeJDK(datasetInstance), key), cells);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private Object[] generateTwoCells(Integer value, Class<?> cellClass) throws Exception {
    String valueStr = longString(value);
    Method newCellMethod = cellDefinitionClass.getMethod("newCell", Object.class);
    Object stringCellValue = newCellMethod.invoke(stringCellDefinition, valueStr);

    Method defineBytesMethod = cellDefinitionClass.getMethod("defineBytes", String.class);
    Object bytesCellDefinition = defineBytesMethod.invoke(null, "myBytesCell");
    Object bytesCellValue = newCellMethod.invoke(bytesCellDefinition, valueStr.getBytes());

    Object[] cells = (Object[]) Array.newInstance(cellClass, 2);
    cells[0] = stringCellValue;
    cells[1] = bytesCellValue;
    return cells;
  }

  private Object[] generateOneCell(Integer value, Class<?> cellClass) throws Exception {
    String valueStr = longString(value);
    Class<?> cellDefinitionClass = loadClass("com.terracottatech.store.definition.CellDefinition");
    Method newCellMethod = cellDefinitionClass.getMethod("newCell", Object.class);
    Method defineBytesMethod = cellDefinitionClass.getMethod("defineBytes", String.class);
    Object bytesCellDefinition = defineBytesMethod.invoke(null, "myBytesCell");
    Object bytesCellValue = newCellMethod.invoke(bytesCellDefinition, valueStr.getBytes());

    Object[] cells = (Object[]) Array.newInstance(cellClass, 1);
    cells[0] = bytesCellValue;
    return cells;
  }

  private Object[] generateCustomCells(Integer value) throws Exception {
    String valueStr = longString(value);
    Method newCellMethod = cellDefinitionClass.getMethod("newCell", Object.class);
    List<Object> cells = new ArrayList<>();
    for (String customCellStr : customCells) {
      String[] splitted = customCellStr.split(":");
      String cellName = splitted[0];
      String cellType = splitted[1];
      switch(cellType) {
        case "STRING":
          Object stringCellDefinition = cellDefinitionClass
            .getMethod("defineString", String.class).invoke(null, cellName);
          cells.add(newCellMethod.invoke(stringCellDefinition, valueStr));
          break;
        case "INT":
          Object intCellDefinition = cellDefinitionClass
            .getMethod("defineInt", String.class).invoke(null, cellName);
          cells.add(newCellMethod.invoke(intCellDefinition, ThreadLocalRandom.current().nextInt(0, value * 1000)));
          break;
        case "LONG":
          Object longCellDefinition = cellDefinitionClass
            .getMethod("defineLong", String.class).invoke(null, cellName);
          cells.add(newCellMethod.invoke(longCellDefinition, ThreadLocalRandom.current().nextLong(0, value * 1000)));
          break;
        case "DOUBLE":
          Object doubleCellDefinition = cellDefinitionClass
            .getMethod("defineDouble", String.class).invoke(null, cellName);
          cells.add(newCellMethod.invoke(doubleCellDefinition, ThreadLocalRandom.current().nextDouble(0, value * 1000)));
          break;
        case "BOOL":
          Object boolCellDefinition = cellDefinitionClass
            .getMethod("defineBool", String.class).invoke(null, cellName);
          cells.add(newCellMethod.invoke(boolCellDefinition, ThreadLocalRandom.current().nextInt(0, value * 1000) % 2 == 0));
          break;
        case "CHAR":
          Object charCellDefinition = cellDefinitionClass
            .getMethod("defineChar", String.class).invoke(null, cellName);
          cells.add(newCellMethod.invoke(charCellDefinition, valueStr.charAt(0)));
          break;
        case "BYTES":
          Object bytesCellDefinition = cellDefinitionClass
            .getMethod("defineBytes", String.class).invoke(null, cellName);
          cells.add(newCellMethod.invoke(bytesCellDefinition, valueStr.getBytes()));
          break;
        default:
          throw new RuntimeException("Cannot recognize cell type: " + cellType);
      }
    }
    return cells.toArray();
  }

  public void addCustomCell(String cellStr) {
    String[] splitted = cellStr.split(":");
    if (splitted.length == 2) {
      String cellType = splitted[1];
      Set<String> TYPES = new HashSet<>(Arrays.asList("STRING", "INT", "LONG", "DOUBLE", "BOOL", "CHAR", "BYTES"));
      if (TYPES.contains(cellType)) {
        customCells.add(cellStr);
      }
    }
  }

  public void removeCustomCell(String cellStr) {
    customCells.remove(cellStr);
  }

  private String longString(Integer intensity) {
    return new BigInteger(intensity * 10, random).toString(16);
  }

  public void updatePoundingIntensity(String datasetInstanceName, int poundingIntensity) {
    poundingMap.put(datasetInstanceName, poundingIntensity);
  }

  public int retrievePoundingIntensity(String datasetInstanceName) {
    return poundingMap.getOrDefault(datasetInstanceName, 0);
  }

  private Object retrieveDatasetInstance(String datasetInstanceName) {
    return datasetInstancesByDatasetName.get(retrieveDatasetName(datasetInstanceName)).get(datasetInstanceName);
  }


  public String getStatus() {
    if (datasetManager == null) {
      return NO_DATASET_MANAGER;
    } else {
      try {
        Thread.currentThread().setContextClassLoader(kitAwareClassLoaderDelegator.getUrlClassLoader());
        // try listing datasets, if we can't, that means it's not available
        Thread.currentThread().setContextClassLoader(kitAwareClassLoaderDelegator.getUrlClassLoader());
        Method listDatasetsMethod = datasetManagerClass.getMethod("listDatasets");
        listDatasetsMethod.invoke(datasetManager);
      } catch (Exception e) {
        return "CLOSED";
      }
    }
    return "INITIALIZED";
  }

  public void initializeDatasetManager(String terracottaServerUrl, String securityPath) {
    try {
      Thread.currentThread().setContextClassLoader(kitAwareClassLoaderDelegator.getUrlClassLoader());
      initCommonObjectsAndClasses();
      if (terracottaServerUrl != null) {
        URI clusterUri = URI.create("terracotta://" + terracottaServerUrl);

        datasetManagerClass = loadClass("com.terracottatech.store.manager.DatasetManager");
        Object clusteredDatasetManagerBuilder;
        if (securityPath != null) {
          Method clusteredMethod = datasetManagerClass.getMethod("secureClustered", URI.class, Path.class);
          clusteredDatasetManagerBuilder = clusteredMethod.invoke(null, clusterUri, Paths.get(securityPath));
        } else {
          Method clusteredMethod = datasetManagerClass.getMethod("clustered", URI.class);
          clusteredDatasetManagerBuilder = clusteredMethod.invoke(null, clusterUri);
        }

        Class<?> clusteredDatasetManagerBuilderClass = loadClass("com.terracottatech.store.client.builder.datasetmanager.clustered.ClusteredDatasetManagerBuilderImpl");

        try {
          Method addTagsMethod = clusteredDatasetManagerBuilderClass.getMethod("withClientTags", String[].class);
          clusteredDatasetManagerBuilder = addTagsMethod.invoke(clusteredDatasetManagerBuilder, (Object) new String[]{"tiny", "pounder", "client"});

          Method aliasMethod = clusteredDatasetManagerBuilderClass.getMethod("withClientAlias", String.class);
          clusteredDatasetManagerBuilder = aliasMethod.invoke(clusteredDatasetManagerBuilder, "TinyPounderDataset");
        } catch (Exception e) {
          // catch and ignore to support earlier versions
        }

        Method buildMethod = clusteredDatasetManagerBuilderClass.getMethod("build");
        datasetManager = buildMethod.invoke(clusteredDatasetManagerBuilder);
      } else {
        datasetManager = null;
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void initCommonObjectsAndClasses() throws Exception {
    typeClass = loadClass("com.terracottatech.store.Type");

    cellDefinitionClass = loadClass("com.terracottatech.store.definition.CellDefinition");
    Method defineStringMethod = cellDefinitionClass.getMethod("defineString", String.class);
    stringCellDefinition = defineStringMethod.invoke(null, "myStringCell");
  }

  public void close() {
    try {
      Thread.currentThread().setContextClassLoader(kitAwareClassLoaderDelegator.getUrlClassLoader());
      Method closeMethod = datasetManagerClass.getMethod("close");
      closeMethod.invoke(datasetManager);
      poundingMap.clear();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public boolean isDatasetManagerAlive() {
    return !getStatus().equals("CLOSED") && !getStatus().equals(NO_DATASET_MANAGER);
  }

  private Class loadClass(String className) throws ClassNotFoundException {
    return kitAwareClassLoaderDelegator.getUrlClassLoader().loadClass(className);
  }

  public Collection<String> retrieveDatasetNames() {
    try {
      Thread.currentThread().setContextClassLoader(kitAwareClassLoaderDelegator.getUrlClassLoader());
      Method listDatasetsMethod = datasetManagerClass.getMethod("listDatasets");
      Map<String, Object> datasetsByName = (Map<String, Object>) listDatasetsMethod.invoke(datasetManager);
      return datasetsByName.keySet();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void createDataset(String datasetName, DatasetConfiguration datasetConfiguration) {
    try {
      Thread.currentThread().setContextClassLoader(kitAwareClassLoaderDelegator.getUrlClassLoader());
      Class<?> indexSettingsClass = loadClass("com.terracottatech.store.indexing.IndexSettings");
      Field btreeField = indexSettingsClass.getDeclaredField("BTREE");
      Object btree = btreeField.get(null);
      Method datasetConfigurationMethod = datasetManagerClass.getMethod("datasetConfiguration");
      Object datasetConfigurationBuilder = datasetConfigurationMethod.invoke(datasetManager);

      Class<?> datasetConfigurationClass = loadClass("com.terracottatech.store.configuration.DatasetConfiguration");
      Class<?> datasetConfigurationBuilderClass = loadClass("com.terracottatech.store.configuration.DatasetConfigurationBuilder");


      Method offHeapMethod = datasetConfigurationBuilderClass.getMethod("offheap", String.class);
      Method diskMethod = datasetConfigurationBuilderClass.getMethod("disk", String.class);
      Method buildMethod = datasetConfigurationBuilderClass.getMethod("build");

      if (datasetConfiguration.useIndex()) {
        Method indexMethod = datasetConfigurationBuilderClass.getMethod("index", cellDefinitionClass, indexSettingsClass);
        datasetConfigurationBuilder = indexMethod.invoke(datasetConfigurationBuilder, stringCellDefinition, btree);
      }
      if (datasetConfiguration.getOffheapResourceName() != null) {
        datasetConfigurationBuilder = offHeapMethod.invoke(datasetConfigurationBuilder, datasetConfiguration.getOffheapResourceName());
      }
      if (datasetConfiguration.getDiskResourceName() != null) {
        datasetConfigurationBuilder = diskMethod.invoke(datasetConfigurationBuilder, datasetConfiguration.getDiskResourceName());
      }

      Object datasetConfigurationBuilt = buildMethod.invoke(datasetConfigurationBuilder);

      Method createDatasetMethod;
      try {
        createDatasetMethod = datasetManagerClass.getMethod("createDataset", String.class, typeClass, datasetConfigurationClass);
        Object datasetInstance = createDatasetMethod.invoke(datasetManager, datasetName, toKeyType(datasetConfiguration.getKeyType()), datasetConfigurationBuilt);
        String instanceName = getInstanceName(datasetInstance);
        Map<String, Object> instancesByName = new TreeMap<>();
        instancesByName.put(instanceName, datasetInstance);
        datasetInstancesByDatasetName.put(datasetName, instancesByName);
      } catch (NoSuchMethodException e) {
        // use new api
        createDatasetMethod = datasetManagerClass.getMethod("newDataset", String.class, typeClass, datasetConfigurationClass);
        boolean datasetCreated = (boolean) createDatasetMethod.invoke(datasetManager, datasetName, toKeyType(datasetConfiguration.getKeyType()), datasetConfigurationBuilt);
        createDatasetInstance(datasetName);
      }

    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private Object toKeyType(String keyTypeStr) throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
    return loadClass("com.terracottatech.store.Type").getDeclaredField(keyTypeStr).get(null);
  }

  private String getInstanceName(Object datasetInstance) throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    Class<?> manageableDatasetClass = loadClass("com.terracottatech.store.internal.ManagementInternalDataset");
    Method getStatisticsMethod = manageableDatasetClass.getMethod("getStatistics");
    Object datasetStatistics = getStatisticsMethod.invoke(datasetInstance);
    Class<?> datasetStatisticsClass = loadClass("com.terracottatech.store.statistics.DatasetStatistics");
    Method getInstanceNameMethod = datasetStatisticsClass.getMethod("getInstanceName");
    return (String) getInstanceNameMethod.invoke(datasetStatistics);
  }

  public void destroyDataset(String datasetName) {
    try {
      Thread.currentThread().setContextClassLoader(kitAwareClassLoaderDelegator.getUrlClassLoader());
      Method destroyDatasetMethod = datasetManagerClass.getMethod("destroyDataset", String.class);
      destroyDatasetMethod.invoke(datasetManager, datasetName);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public Set<String> getDatasetInstanceNames(String datasetName) {
    try {
      Thread.currentThread().setContextClassLoader(kitAwareClassLoaderDelegator.getUrlClassLoader());

      Class<?> internalDatasetManagerClass = loadClass("com.terracottatech.store.internal.ManagementInternalDatasetManager");
      Class<?> statisticsServiceClass = loadClass("com.terracottatech.store.statistics.StatisticsService");
      Method getStatisticsServiceMethod = internalDatasetManagerClass.getMethod("getStatisticsService");
      Method getDatasetInstanceNamesMethod = statisticsServiceClass.getMethod("getDatasetInstanceNames");

      Object statisticsService = getStatisticsServiceMethod.invoke(datasetManager);
      Collection<String> datasetInstanceNames = (Collection<String>) getDatasetInstanceNamesMethod.invoke(statisticsService);
      
      // Filter instances for this dataset and ensure they're tracked locally
      Set<String> matchingInstances = datasetInstanceNames.stream()
        .filter(instanceName -> {
          String retrievedDatasetName = retrieveDatasetName(instanceName);
          return datasetName.equals(retrievedDatasetName);
        })
        .collect(Collectors.toSet());
      
      // Ensure local tracking map exists for this dataset
      datasetInstancesByDatasetName.computeIfAbsent(datasetName, k -> new TreeMap<>());
      
      // Add any instances found in cluster that aren't tracked locally
      for (String instanceName : matchingInstances) {
        if (!datasetInstancesByDatasetName.get(datasetName).containsKey(instanceName)) {
          try {
            // Get the dataset instance from the manager
            Method listDatasetsMethod = datasetManagerClass.getMethod("listDatasets");
            Map<String, Object> datasetsByName = (Map<String, Object>) listDatasetsMethod.invoke(datasetManager);
            Method getDatasetMethod = datasetManagerClass.getMethod("getDataset", String.class, typeClass);
            Object datasetInstance = getDatasetMethod.invoke(datasetManager, datasetName, datasetsByName.get(datasetName));
            
            // Track it locally
            datasetInstancesByDatasetName.get(datasetName).put(instanceName, datasetInstance);
          } catch (Exception e) {
            // If we can't get the instance, just skip it
            System.err.println("Warning: Could not track existing dataset instance " + instanceName + ": " + e.getMessage());
          }
        }
      }
      
      return matchingInstances;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private String retrieveDatasetName(String datasetInstanceName) {
    Pattern pattern = Pattern.compile(DATASET_INSTANCE_PATTERN);
    Matcher matcher = pattern.matcher(datasetInstanceName);
    if (matcher.matches() && (matcher.groupCount() > 0)) {
      return matcher.group(1);
    }
    return "";
  }

  public void closeDatasetInstance(String datasetName, String instanceName) {
    poundingMap.remove(instanceName);
    Object datasetInstance = datasetInstancesByDatasetName.get(datasetName).get(instanceName);
    try {
      Thread.currentThread().setContextClassLoader(kitAwareClassLoaderDelegator.getUrlClassLoader());
      Class<?> datasetClass = loadClass("com.terracottatech.store.Dataset");
      Method closeMethod = datasetClass.getMethod("close");
      closeMethod.invoke(datasetInstance);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public String createDatasetInstance(String datasetName) {
    try {
      Thread.currentThread().setContextClassLoader(kitAwareClassLoaderDelegator.getUrlClassLoader());
      Method listDatasetsMethod = datasetManagerClass.getMethod("listDatasets");
      Map<String, Object> datasetsByName = (Map<String, Object>) listDatasetsMethod.invoke(datasetManager);
      Method getDatasetMethod = datasetManagerClass.getMethod("getDataset", String.class, typeClass);
      Object datasetInstance = getDatasetMethod.invoke(datasetManager, datasetName, datasetsByName.get(datasetName));
      String instanceName = getInstanceName(datasetInstance);

      datasetInstancesByDatasetName.computeIfAbsent(datasetName, k -> new TreeMap<>());

      datasetInstancesByDatasetName.get(datasetName).put(instanceName, datasetInstance);
      return instanceName;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

  }
}

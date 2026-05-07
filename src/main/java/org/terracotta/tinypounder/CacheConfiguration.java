package org.terracotta.tinypounder;

/**
 * Configuration for cache tiers including heap, offheap, disk, and clustered resources.
 * Converted to Java 21 record for immutability and reduced boilerplate.
 * Explicit getters provided for backward compatibility with existing code.
 */
public record CacheConfiguration(
    long onHeapSize,
    String onHeapSizeUnit,
    long offHeapSize,
    String offHeapSizeUnit,
    long diskSize,
    String diskSizeUnit,
    long clusteredDedicatedSize,
    String clusteredDedicatedUnit,
    String clusteredSharedPoolName,
    ClusterTierType clusteredTierType
) {
  
  // Explicit getters for backward compatibility
  public long getOnHeapSize() { return onHeapSize; }
  public String getOnHeapSizeUnit() { return onHeapSizeUnit; }
  public long getOffHeapSize() { return offHeapSize; }
  public String getOffHeapSizeUnit() { return offHeapSizeUnit; }
  public long getDiskSize() { return diskSize; }
  public String getDiskSizeUnit() { return diskSizeUnit; }
  public long getClusteredDedicatedSize() { return clusteredDedicatedSize; }
  public String getClusteredDedicatedUnit() { return clusteredDedicatedUnit; }
  public String getClusteredSharedPoolName() { return clusteredSharedPoolName; }
  public ClusterTierType getClusteredTierType() { return clusteredTierType; }
  
  public enum ClusterTierType {
    NONE,
    DEDICATED,
    SHARED
  }
}

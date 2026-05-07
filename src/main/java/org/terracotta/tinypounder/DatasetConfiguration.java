package org.terracotta.tinypounder;

/**
 * Configuration for dataset resources including key type and storage resources.
 * Converted to Java 21 record for immutability and reduced boilerplate.
 * Explicit getters provided for backward compatibility with existing code.
 */
public record DatasetConfiguration(
    String keyType,
    String offheapResourceName,
    String diskResourceName,
    boolean useIndex
) {
  
  // Explicit getters for backward compatibility
  public String getKeyType() { return keyType; }
  public String getOffheapResourceName() { return offheapResourceName; }
  public String getDiskResourceName() { return diskResourceName; }
  public boolean useIndex() { return useIndex; }
}

package br.com.betufu.platform.persistence;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class PrivateObjectStorageProbe {

  private final Environment environment;

  public PrivateObjectStorageProbe(Environment environment) {
    this.environment = environment;
  }

  public PrivateObjectStorageStatus checkStorageStatus() {
    String bucket = environment.getProperty("BETUFU_S3_BUCKET", "betufu-private");
    String endpoint = environment.getProperty("BETUFU_S3_ENDPOINT", "http://127.0.0.1:8333");
    String accessKey = environment.getProperty("BETUFU_S3_ACCESS_KEY_ID");

    boolean configured = bucket != null && !bucket.isBlank();
    boolean authenticatedConfigured = accessKey != null && !accessKey.isBlank();

    return new PrivateObjectStorageStatus(configured, bucket, authenticatedConfigured);
  }

  public record PrivateObjectStorageStatus(
      boolean configured, String bucketName, boolean privateAccessEnforced) {}
}

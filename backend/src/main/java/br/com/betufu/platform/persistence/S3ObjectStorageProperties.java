package br.com.betufu.platform.persistence;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "betufu.s3")
public class S3ObjectStorageProperties {

  private boolean enabled;
  private String endpoint;
  private String region = "us-east-1";
  private String bucket;
  private String accessKeyId;
  private String secretAccessKey;
  private boolean forcePathStyle = true;

  public boolean enabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String endpoint() {
    return endpoint;
  }

  public void setEndpoint(String endpoint) {
    this.endpoint = endpoint;
  }

  public String region() {
    return region;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  public String bucket() {
    return bucket;
  }

  public void setBucket(String bucket) {
    this.bucket = bucket;
  }

  public String accessKeyId() {
    return accessKeyId;
  }

  public void setAccessKeyId(String accessKeyId) {
    this.accessKeyId = accessKeyId;
  }

  public String secretAccessKey() {
    return secretAccessKey;
  }

  public void setSecretAccessKey(String secretAccessKey) {
    this.secretAccessKey = secretAccessKey;
  }

  public boolean forcePathStyle() {
    return forcePathStyle;
  }

  public void setForcePathStyle(boolean forcePathStyle) {
    this.forcePathStyle = forcePathStyle;
  }

  public void validate() {
    if (!enabled) {
      return;
    }
    if (endpoint == null
        || !endpoint.startsWith("https://")
        || bucket == null
        || bucket.isBlank()
        || region == null
        || region.isBlank()
        || accessKeyId == null
        || accessKeyId.isBlank()
        || secretAccessKey == null
        || secretAccessKey.isBlank()) {
      throw new IllegalStateException(
          "BETUFU S3 requires private endpoint, bucket and credentials");
    }
  }
}

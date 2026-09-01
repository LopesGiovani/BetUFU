package br.com.betufu.platform.persistence;

import java.net.URI;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
@Profile("runtime | integrated")
@EnableConfigurationProperties(S3ObjectStorageProperties.class)
@NamedInterface("storage")
public final class S3ObjectStorageAdapter {

  private final S3ObjectStorageProperties properties;
  private final S3Client client;

  public S3ObjectStorageAdapter(S3ObjectStorageProperties properties) {
    this.properties = Objects.requireNonNull(properties, "properties must not be null");
    properties.validate();
    this.client = properties.enabled() ? createClient(properties) : null;
  }

  public void put(String objectKey, byte[] bytes, String contentType) {
    requireClient();
    client.putObject(
        PutObjectRequest.builder()
            .bucket(properties.bucket())
            .key(requireKey(objectKey))
            .contentType(contentType)
            .contentLength((long) bytes.length)
            .build(),
        RequestBody.fromBytes(bytes));
  }

  public void putIfAbsent(String objectKey, byte[] bytes, String contentType) {
    requireClient();
    client.putObject(
        PutObjectRequest.builder()
            .bucket(properties.bucket())
            .key(requireKey(objectKey))
            .contentType(contentType)
            .contentLength((long) bytes.length)
            .ifNoneMatch("*")
            .build(),
        RequestBody.fromBytes(bytes));
  }

  public Optional<StoredObject> get(String objectKey) {
    requireClient();
    try {
      var response =
          client.getObjectAsBytes(
              GetObjectRequest.builder()
                  .bucket(properties.bucket())
                  .key(requireKey(objectKey))
                  .build());
      return Optional.of(
          new StoredObject(
              response.asByteArray(),
              response.response().contentType(),
              response.response().eTag()));
    } catch (NoSuchKeyException e) {
      return Optional.empty();
    }
  }

  public Optional<String> head(String objectKey) {
    requireClient();
    try {
      return Optional.ofNullable(
          client
              .headObject(
                  HeadObjectRequest.builder()
                      .bucket(properties.bucket())
                      .key(requireKey(objectKey))
                      .build())
              .eTag());
    } catch (software.amazon.awssdk.services.s3.model.S3Exception e) {
      if (e.statusCode() == 404) {
        return Optional.empty();
      }
      throw e;
    }
  }

  public void delete(String objectKey) {
    requireClient();
    client.deleteObject(
        DeleteObjectRequest.builder()
            .bucket(properties.bucket())
            .key(requireKey(objectKey))
            .build());
  }

  private static S3Client createClient(S3ObjectStorageProperties properties) {
    return S3Client.builder()
        .endpointOverride(URI.create(properties.endpoint()))
        .region(Region.of(properties.region()))
        .forcePathStyle(properties.forcePathStyle())
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKeyId(), properties.secretAccessKey())))
        .httpClient(UrlConnectionHttpClient.builder().build())
        .build();
  }

  private void requireClient() {
    if (client == null) {
      throw new IllegalStateException("BETUFU S3 storage is not configured");
    }
  }

  private static String requireKey(String objectKey) {
    if (objectKey == null
        || objectKey.isBlank()
        || objectKey.startsWith("/")
        || objectKey.contains("..")) {
      throw new IllegalArgumentException("invalid object key");
    }
    return objectKey;
  }

  @NamedInterface("storage")
  public record StoredObject(byte[] bytes, String contentType, String etag) {

    public StoredObject {
      bytes = Arrays.copyOf(Objects.requireNonNull(bytes, "bytes must not be null"), bytes.length);
      contentType = Objects.requireNonNull(contentType, "contentType must not be null");
      etag = Objects.requireNonNull(etag, "etag must not be null");
    }

    @Override
    public byte[] bytes() {
      return Arrays.copyOf(bytes, bytes.length);
    }
  }
}

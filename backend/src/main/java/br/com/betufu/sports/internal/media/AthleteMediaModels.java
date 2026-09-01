package br.com.betufu.sports.internal.media;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class AthleteMediaModels {

  private AthleteMediaModels() {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record AthleteMediaResponse(
      UUID athleteId,
      boolean present,
      String contentType,
      Integer byteSize,
      Integer width,
      Integer height,
      long mediaVersion,
      Instant updatedAt) {

    public AthleteMediaResponse {
      Objects.requireNonNull(athleteId, "athleteId must not be null");
      Objects.requireNonNull(updatedAt, "updatedAt must not be null");
      if (!present
          && (contentType != null || byteSize != null || width != null || height != null)) {
        throw new IllegalArgumentException("empty media response must not contain metadata");
      }
    }
  }

  public record MediaReference(
      UUID athleteId,
      String objectKey,
      String contentType,
      int byteSize,
      int width,
      int height,
      String etag,
      long mediaVersion,
      long lockVersion,
      Instant createdAt,
      Instant updatedAt) {

    public MediaReference {
      Objects.requireNonNull(athleteId, "athleteId must not be null");
      Objects.requireNonNull(objectKey, "objectKey must not be null");
      Objects.requireNonNull(contentType, "contentType must not be null");
      Objects.requireNonNull(etag, "etag must not be null");
      Objects.requireNonNull(createdAt, "createdAt must not be null");
      Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }
  }

  public record ProcessedImage(byte[] bytes, String contentType, int width, int height) {

    public ProcessedImage {
      Objects.requireNonNull(bytes, "bytes must not be null");
      Objects.requireNonNull(contentType, "contentType must not be null");
      bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
      return bytes.clone();
    }
  }

  private static byte[] copy(byte[] bytes) {
    return bytes == null ? null : bytes.clone();
  }

  public record StoredObject(byte[] bytes, String contentType, String etag) {

    public StoredObject {
      Objects.requireNonNull(bytes, "bytes must not be null");
      Objects.requireNonNull(contentType, "contentType must not be null");
      Objects.requireNonNull(etag, "etag must not be null");
      bytes = copy(bytes);
    }

    @Override
    public byte[] bytes() {
      return copy(bytes);
    }
  }
}

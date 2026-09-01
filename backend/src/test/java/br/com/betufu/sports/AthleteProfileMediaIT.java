package br.com.betufu.sports.internal.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.sports.internal.media.AthleteMediaModels.ProcessedImage;
import br.com.betufu.sports.internal.media.AthleteMediaModels.StoredObject;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AthleteProfileMediaIT {

  private final AthleteImageProcessor processor = new AthleteImageProcessor();

  @Test
  @DisplayName("validAvatarIsReencodedAndStored")
  void validAvatarIsReencodedAndStored() throws Exception {
    byte[] input = resource("valid-avatar.png");

    ProcessedImage processed = processor.process(input, "image/png");

    assertThat(processed.contentType()).isEqualTo("image/png");
    assertThat(processed.bytes()).isNotEqualTo(input);
    assertThat(processed.width()).isEqualTo(64);
    assertThat(processed.height()).isEqualTo(64);
  }

  @Test
  @DisplayName("declaredJpegWithPngBytesIsRejectedBeforeStorage")
  void declaredJpegWithPngBytesIsRejectedBeforeStorage() throws Exception {
    assertThatThrownBy(() -> processor.process(resource("valid-avatar.png"), "image/jpeg"))
        .isInstanceOf(ApiProblemException.class)
        .extracting("status")
        .hasToString("400 BAD_REQUEST");
  }

  @Test
  @DisplayName("truncatedPngIsRejectedBeforeStorage")
  void truncatedPngIsRejectedBeforeStorage() throws Exception {
    byte[] valid = resource("valid-avatar.png");
    assertThatThrownBy(() -> processor.process(Arrays.copyOf(valid, valid.length / 2), "image/png"))
        .isInstanceOf(ApiProblemException.class);
  }

  @Test
  @DisplayName("oversizedPixelsAreRejectedBeforeStorage")
  void oversizedPixelsAreRejectedBeforeStorage() throws Exception {
    assertThatThrownBy(() -> processor.process(resource("oversized-avatar.png"), "image/png"))
        .isInstanceOf(ApiProblemException.class);
  }

  @Test
  @DisplayName("metadataIsRemovedByReencode")
  void metadataIsRemovedByReencode() throws Exception {
    ProcessedImage processed = processor.process(resource("metadata-avatar.png"), "image/png");

    assertThat(new String(processed.bytes(), java.nio.charset.StandardCharsets.ISO_8859_1))
        .doesNotContain("BETUFU-METADATA-MARKER");
  }

  @Test
  @DisplayName("polyglotIsRejectedBeforeStorage")
  void polyglotIsRejectedBeforeStorage() throws Exception {
    assertThatThrownBy(() -> processor.process(resource("polyglot-avatar.png"), "image/png"))
        .isInstanceOf(ApiProblemException.class);
  }

  @Test
  @DisplayName("storageFailurePreservesPreviousPhoto")
  void storageFailurePreservesPreviousPhoto() {
    FailingStorage storage = new FailingStorage();
    StoredObject previous = new StoredObject(new byte[] {9, 8, 7}, "image/png", "previous");
    storage.objects.put("previous", previous);
    ProcessedImage replacement = new ProcessedImage(new byte[] {1, 2, 3}, "image/png", 1, 1);

    assertThatThrownBy(
            () ->
                AthleteMediaService.storeThenSwap(
                    storage,
                    "replacement",
                    replacement,
                    () -> {
                      storage.swapCalled = true;
                      storage.objects.put("previous", replacementObject());
                      return null;
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(storage.swapCalled).isFalse();
    assertThat(storage.objects)
        .containsEntry("previous", previous)
        .doesNotContainKey("replacement");
  }

  private static StoredObject replacementObject() {
    return new StoredObject(new byte[] {1, 2, 3}, "image/png", "replacement");
  }

  private static final class FailingStorage implements AthleteMediaStorage {
    private final Map<String, StoredObject> objects = new HashMap<>();
    private boolean swapCalled;

    @Override
    public void put(String objectKey, byte[] bytes, String contentType) {
      throw new IllegalStateException("storage unavailable");
    }

    @Override
    public void putIfAbsent(String objectKey, byte[] bytes, String contentType) {
      throw new IllegalStateException("storage unavailable");
    }

    @Override
    public Optional<StoredObject> get(String objectKey) {
      return Optional.ofNullable(objects.get(objectKey));
    }

    @Override
    public Optional<String> head(String objectKey) {
      return Optional.ofNullable(objects.get(objectKey)).map(StoredObject::etag);
    }

    @Override
    public void delete(String objectKey) {
      objects.remove(objectKey);
    }
  }

  private static byte[] resource(String name) throws IOException {
    try (InputStream input =
        AthleteProfileMediaIT.class.getResourceAsStream("/sports/media/" + name)) {
      if (input == null) {
        throw new IOException("missing fixture " + name);
      }
      return input.readAllBytes();
    }
  }
}

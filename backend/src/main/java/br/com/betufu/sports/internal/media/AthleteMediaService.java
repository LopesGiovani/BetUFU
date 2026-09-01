package br.com.betufu.sports.internal.media;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.platform.identity.OperationalIdentityLookup;
import br.com.betufu.sports.internal.media.AthleteMediaModels.AthleteMediaResponse;
import br.com.betufu.sports.internal.media.AthleteMediaModels.MediaReference;
import br.com.betufu.sports.internal.media.AthleteMediaModels.ProcessedImage;
import br.com.betufu.sports.internal.media.AthleteMediaModels.StoredObject;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
@Profile("runtime | integrated")
class AthleteMediaService {

  private static final Logger LOGGER = LoggerFactory.getLogger(AthleteMediaService.class);
  private static final String OBJECT_KEY_PREFIX = "athlete-profile/";

  private final JdbcTemplate jdbc;
  private final OperationalIdentityLookup identityLookup;
  private final AthleteImageProcessor processor;
  private final AthleteMediaStorage storage;
  private final TransactionTemplate transactions;

  AthleteMediaService(
      JdbcTemplate jdbc,
      OperationalIdentityLookup identityLookup,
      AthleteImageProcessor processor,
      AthleteMediaStorage storage,
      PlatformTransactionManager transactionManager) {
    this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
    this.identityLookup = Objects.requireNonNull(identityLookup, "identityLookup must not be null");
    this.processor = Objects.requireNonNull(processor, "processor must not be null");
    this.storage = Objects.requireNonNull(storage, "storage must not be null");
    this.transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  AthleteMediaResponse getMyMedia(String issuer, String subject) {
    UUID athleteId = resolveLinkedAthlete(issuer, subject);
    return findReference(athleteId).map(this::toResponse).orElseGet(() -> emptyResponse(athleteId));
  }

  AthleteMediaResponse upload(String issuer, String subject, MultipartFile file) {
    UUID athleteId = resolveLinkedAthlete(issuer, subject);
    if (file == null || file.isEmpty()) {
      throw validation();
    }

    String declaredType = file.getContentType();
    byte[] input;
    try {
      input = file.getBytes();
    } catch (Exception e) {
      throw processingFailure();
    }
    ProcessedImage image = processor.process(input, declaredType);
    MediaReference previous = findReference(athleteId).orElse(null);
    String objectKey = OBJECT_KEY_PREFIX + athleteId + "/" + UUID.randomUUID();

    try {
      MediaReference updated =
          storeThenSwap(
              storage,
              objectKey,
              image,
              () -> {
                String etag = storage.head(objectKey).orElseGet(() -> digest(image.bytes()));
                return transactions.execute(
                    status ->
                        replaceReference(
                            athleteId,
                            objectKey,
                            image,
                            etag,
                            previous == null ? null : previous.lockVersion()));
              });
      if (updated == null) {
        throw internalFailure();
      }
      if (previous != null) {
        deleteAfterSwap(previous.objectKey());
      }
      return toResponse(updated);
    } catch (ApiProblemException e) {
      deleteAfterFailure(objectKey);
      throw e;
    } catch (RuntimeException e) {
      deleteAfterFailure(objectKey);
      throw processingFailure();
    }
  }

  AthleteMediaResponse remove(String issuer, String subject) {
    UUID athleteId = resolveLinkedAthlete(issuer, subject);
    MediaReference previous = findReference(athleteId).orElse(null);
    if (previous == null) {
      return emptyResponse(athleteId);
    }

    try {
      int updated =
          transactions.execute(
              status ->
                  jdbc.update(
                      "UPDATE public.sports_athlete_profile_media "
                          + "SET object_key = NULL, content_type = NULL, byte_size = NULL, "
                          + "width = NULL, height = NULL, etag = NULL, media_version = 0, "
                          + "lock_version = lock_version + 1, updated_at = CURRENT_TIMESTAMP(6) "
                          + "WHERE athlete_id = ? AND lock_version = ?",
                      athleteId,
                      previous.lockVersion()));
      if (updated == 0) {
        throw conflict();
      }
    } catch (ApiProblemException e) {
      throw e;
    } catch (RuntimeException e) {
      throw internalFailure();
    }
    deleteAfterSwap(previous.objectKey());
    return emptyResponse(athleteId);
  }

  PublicMedia getPublicMedia(UUID athleteId) {
    if (athleteId == null || !isPublished(athleteId)) {
      throw notFound();
    }
    MediaReference reference = findReference(athleteId).orElseThrow(AthleteMediaService::notFound);
    StoredObject object =
        storage.get(reference.objectKey()).orElseThrow(AthleteMediaService::notFound);
    if (!reference.contentType().equals(object.contentType())) {
      throw notFound();
    }
    return new PublicMedia(object.bytes(), reference.contentType(), reference.etag());
  }

  private MediaReference replaceReference(
      UUID athleteId,
      String objectKey,
      ProcessedImage image,
      String etag,
      Long expectedLockVersion) {
    if (expectedLockVersion == null) {
      jdbc.update(
          "INSERT INTO public.sports_athlete_profile_media "
              + "(athlete_id, object_key, content_type, byte_size, width, height, etag, media_version, lock_version) "
              + "VALUES (?, ?, ?, ?, ?, ?, ?, 1, 1)",
          athleteId,
          objectKey,
          image.contentType(),
          image.bytes().length,
          image.width(),
          image.height(),
          etag);
    } else {
      int updated =
          jdbc.update(
              "UPDATE public.sports_athlete_profile_media "
                  + "SET object_key = ?, content_type = ?, byte_size = ?, width = ?, height = ?, etag = ?, "
                  + "media_version = media_version + 1, lock_version = lock_version + 1, updated_at = CURRENT_TIMESTAMP(6) "
                  + "WHERE athlete_id = ? AND lock_version = ?",
              objectKey,
              image.contentType(),
              image.bytes().length,
              image.width(),
              image.height(),
              etag,
              athleteId,
              expectedLockVersion);
      if (updated == 0) {
        throw conflict();
      }
    }
    return findReference(athleteId).orElseThrow(AthleteMediaService::internalFailure);
  }

  private Optional<MediaReference> findReference(UUID athleteId) {
    return jdbc
        .query(
            "SELECT athlete_id, object_key, content_type, byte_size, width, height, etag, "
                + "media_version, lock_version, created_at, updated_at "
                + "FROM public.sports_athlete_profile_media WHERE athlete_id = ? AND object_key IS NOT NULL",
            this::mapReference,
            athleteId)
        .stream()
        .filter(reference -> reference.objectKey() != null)
        .findFirst();
  }

  private UUID resolveLinkedAthlete(String issuer, String subject) {
    if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank()) {
      throw unauthorized();
    }
    UUID identityId =
        identityLookup
            .findIdentityId(issuer, subject)
            .orElseThrow(AthleteMediaService::unauthorized);
    return jdbc
        .query(
            "SELECT athlete_id FROM public.sports_athlete_link_proposal "
                + "WHERE identity_id = ? AND status = 'ACEITA'",
            (rs, rowNum) -> rs.getObject("athlete_id", UUID.class),
            identityId)
        .stream()
        .findFirst()
        .orElseThrow(AthleteMediaService::notFound);
  }

  private boolean isPublished(UUID athleteId) {
    Long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM public.sports_athlete WHERE athlete_id = ? AND status = 'PUBLICADO'",
            Long.class,
            athleteId);
    return count != null && count == 1;
  }

  private MediaReference mapReference(ResultSet rs, int rowNum) throws SQLException {
    Timestamp createdAt = rs.getTimestamp("created_at");
    Timestamp updatedAt = rs.getTimestamp("updated_at");
    return new MediaReference(
        rs.getObject("athlete_id", UUID.class),
        rs.getString("object_key"),
        rs.getString("content_type"),
        rs.getInt("byte_size"),
        rs.getInt("width"),
        rs.getInt("height"),
        rs.getString("etag"),
        rs.getLong("media_version"),
        rs.getLong("lock_version"),
        createdAt == null ? Instant.EPOCH : createdAt.toInstant(),
        updatedAt == null ? Instant.EPOCH : updatedAt.toInstant());
  }

  private AthleteMediaResponse toResponse(MediaReference reference) {
    return new AthleteMediaResponse(
        reference.athleteId(),
        true,
        reference.contentType(),
        reference.byteSize(),
        reference.width(),
        reference.height(),
        reference.mediaVersion(),
        reference.updatedAt());
  }

  private static AthleteMediaResponse emptyResponse(UUID athleteId) {
    return new AthleteMediaResponse(athleteId, false, null, null, null, null, 0, Instant.now());
  }

  private void deleteAfterSwap(String objectKey) {
    try {
      storage.delete(objectKey);
    } catch (RuntimeException e) {
      LOGGER.warn("Unable to delete replaced athlete media object");
    }
  }

  private void deleteAfterFailure(String objectKey) {
    try {
      storage.delete(objectKey);
    } catch (RuntimeException e) {
      LOGGER.warn("Unable to clean failed athlete media object");
    }
  }

  private static String digest(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  static <T> T storeThenSwap(
      AthleteMediaStorage storage, String objectKey, ProcessedImage image, Supplier<T> swap) {
    storage.putIfAbsent(objectKey, image.bytes(), image.contentType());
    return swap.get();
  }

  record PublicMedia(byte[] bytes, String contentType, String etag) {
    PublicMedia {
      bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
      return bytes.clone();
    }
  }

  private static ApiProblemException validation() {
    return new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
  }

  private static ApiProblemException processingFailure() {
    return new ApiProblemException(HttpStatus.INTERNAL_SERVER_ERROR, ApiErrorCode.INTERNAL_ERROR);
  }

  private static ApiProblemException internalFailure() {
    return processingFailure();
  }

  private static ApiProblemException notFound() {
    return new ApiProblemException(HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND);
  }

  private static ApiProblemException unauthorized() {
    return new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
  }

  private static ApiProblemException conflict() {
    return new ApiProblemException(HttpStatus.CONFLICT, ApiErrorCode.STALE_VERSION);
  }
}

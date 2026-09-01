package br.com.betufu.sports.internal.media;

import br.com.betufu.platform.persistence.S3ObjectStorageAdapter;
import br.com.betufu.sports.internal.media.AthleteMediaModels.StoredObject;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Narrow storage port: domain code never receives a bucket, endpoint, or presigned URL. */
public interface AthleteMediaStorage {

  void put(String objectKey, byte[] bytes, String contentType);

  default void putIfAbsent(String objectKey, byte[] bytes, String contentType) {
    put(objectKey, bytes, contentType);
  }

  Optional<StoredObject> get(String objectKey);

  Optional<String> head(String objectKey);

  void delete(String objectKey);
}

@Configuration(proxyBeanMethods = false)
@Profile("runtime | integrated")
class AthleteMediaStorageConfiguration {

  @Bean
  AthleteMediaStorage athleteMediaStorage(S3ObjectStorageAdapter adapter) {
    return new AthleteMediaStorage() {
      @Override
      public void put(String objectKey, byte[] bytes, String contentType) {
        adapter.put(objectKey, bytes, contentType);
      }

      @Override
      public void putIfAbsent(String objectKey, byte[] bytes, String contentType) {
        adapter.putIfAbsent(objectKey, bytes, contentType);
      }

      @Override
      public Optional<StoredObject> get(String objectKey) {
        return adapter
            .get(objectKey)
            .map(object -> new StoredObject(object.bytes(), object.contentType(), object.etag()));
      }

      @Override
      public Optional<String> head(String objectKey) {
        return adapter.head(objectKey);
      }

      @Override
      public void delete(String objectKey) {
        adapter.delete(objectKey);
      }
    };
  }
}

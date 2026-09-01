package br.com.betufu.identity.internal;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Access(AccessType.FIELD)
@Table(
    name = "local_identity",
    schema = "public",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_local_identity_issuer_subject",
            columnNames = {"issuer", "subject"}))
public class LocalIdentity {

  @Id
  @Column(name = "identity_id", nullable = false, updatable = false)
  private UUID identityId;

  @Column(name = "issuer", nullable = false, updatable = false, length = 2048)
  private String issuer;

  @Column(name = "subject", nullable = false, updatable = false, length = 255)
  private String subject;

  @Column(
      name = "created_at",
      nullable = false,
      updatable = false,
      columnDefinition = "timestamptz(6)")
  private Instant createdAt;

  @Column(
      name = "updated_at",
      nullable = false,
      updatable = false,
      columnDefinition = "timestamptz(6)")
  private Instant updatedAt;

  protected LocalIdentity() {}

  public UUID getIdentityId() {
    return identityId;
  }

  public String getIssuer() {
    return issuer;
  }

  public String getSubject() {
    return subject;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}

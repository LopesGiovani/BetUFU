package br.com.betufu.verification.internal;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Access(AccessType.FIELD)
@Table(name = "ufu_enrollment", schema = "public")
public class UfuEnrollment {

  public static final String STATUS_ATIVO = "ATIVO";
  public static final String STATUS_LIBERADO = "LIBERADO";

  @Id
  @Column(name = "enrollment_id", nullable = false, updatable = false)
  private UUID enrollmentId;

  @Column(name = "identity_id", nullable = false, updatable = false)
  private UUID identityId;

  @Column(name = "matricula_hmac", nullable = false, updatable = false)
  private byte[] matriculaHmac;

  @Column(name = "hmac_version", nullable = false, updatable = false)
  private short hmacVersion;

  @Column(name = "status", nullable = false, length = 16)
  private String status;

  @Column(
      name = "created_at",
      nullable = false,
      updatable = false,
      columnDefinition = "timestamptz(6)")
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz(6)")
  private Instant updatedAt;

  protected UfuEnrollment() {}

  public UfuEnrollment(
      UUID enrollmentId,
      UUID identityId,
      byte[] matriculaHmac,
      short hmacVersion,
      String status,
      Instant createdAt,
      Instant updatedAt) {
    this.enrollmentId = Objects.requireNonNull(enrollmentId, "enrollmentId must not be null");
    this.identityId = Objects.requireNonNull(identityId, "identityId must not be null");
    this.matriculaHmac =
        Objects.requireNonNull(matriculaHmac, "matriculaHmac must not be null").clone();
    this.hmacVersion = hmacVersion;
    this.status = Objects.requireNonNull(status, "status must not be null");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
  }

  public UUID getEnrollmentId() {
    return enrollmentId;
  }

  public UUID getIdentityId() {
    return identityId;
  }

  public byte[] getMatriculaHmac() {
    return matriculaHmac != null ? matriculaHmac.clone() : null;
  }

  public short getHmacVersion() {
    return hmacVersion;
  }

  public String getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public boolean isActive() {
    return STATUS_ATIVO.equals(status);
  }
}

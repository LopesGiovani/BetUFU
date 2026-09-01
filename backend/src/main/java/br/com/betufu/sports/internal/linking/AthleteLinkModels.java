package br.com.betufu.sports.internal.linking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class AthleteLinkModels {

  private AthleteLinkModels() {}

  public record AthleteLinkProposal(
      UUID proposalId,
      UUID athleteId,
      String athleteName,
      String athleteSlug,
      String status,
      Instant createdAt,
      Instant updatedAt,
      Instant expiresAt,
      Instant decidedAt) {
    public AthleteLinkProposal {
      Objects.requireNonNull(proposalId, "proposalId must not be null");
      Objects.requireNonNull(athleteId, "athleteId must not be null");
      Objects.requireNonNull(athleteName, "athleteName must not be null");
      Objects.requireNonNull(athleteSlug, "athleteSlug must not be null");
      Objects.requireNonNull(status, "status must not be null");
      Objects.requireNonNull(createdAt, "createdAt must not be null");
      Objects.requireNonNull(updatedAt, "updatedAt must not be null");
      Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }
  }

  public record AthleteLinkDecisionRequest(
      @NotBlank(message = "Decisão é obrigatória")
          @Pattern(
              regexp = "^(ACCEPT|REJECT|CONTEST)$",
              message = "Decisão deve ser ACCEPT, REJECT ou CONTEST")
          String decision) {}

  public record AthleteLinkReviewSummary(
      UUID reviewId,
      UUID proposalId,
      UUID athleteId,
      String athleteName,
      String reasonCode,
      String status,
      Instant createdAt) {
    public AthleteLinkReviewSummary {
      Objects.requireNonNull(reviewId, "reviewId must not be null");
      Objects.requireNonNull(reasonCode, "reasonCode must not be null");
      Objects.requireNonNull(status, "status must not be null");
      Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
  }

  public record AthleteLinkReviewPage(
      List<AthleteLinkReviewSummary> items,
      int page,
      int size,
      long totalElements,
      int totalPages,
      boolean hasNext) {
    public AthleteLinkReviewPage {
      items = items == null ? List.of() : List.copyOf(items);
    }
  }

  public record AthleteLinkProposalEntity(
      UUID proposalId,
      UUID athleteId,
      UUID identityId,
      String status,
      long lockVersion,
      Instant createdAt,
      Instant updatedAt,
      UUID decidedByIdentityId,
      Instant decidedAt,
      Instant expiresAt) {
    public AthleteLinkProposalEntity {
      Objects.requireNonNull(proposalId, "proposalId must not be null");
      Objects.requireNonNull(athleteId, "athleteId must not be null");
      Objects.requireNonNull(identityId, "identityId must not be null");
      Objects.requireNonNull(status, "status must not be null");
      Objects.requireNonNull(createdAt, "createdAt must not be null");
      Objects.requireNonNull(updatedAt, "updatedAt must not be null");
      Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }
  }

  public record AthleteLinkReviewEntity(
      UUID reviewId,
      UUID proposalId,
      UUID athleteId,
      UUID identityId,
      String reasonCode,
      String status,
      long lockVersion,
      Instant createdAt,
      Instant updatedAt) {
    public AthleteLinkReviewEntity {
      Objects.requireNonNull(reviewId, "reviewId must not be null");
      Objects.requireNonNull(reasonCode, "reasonCode must not be null");
      Objects.requireNonNull(status, "status must not be null");
      Objects.requireNonNull(createdAt, "createdAt must not be null");
      Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }
  }

  public record AthleteMatch(
      UUID athleteId,
      String name,
      String slug,
      byte[] matriculaHmac,
      Short hmacVersion,
      String status) {
    public AthleteMatch {
      Objects.requireNonNull(athleteId, "athleteId must not be null");
      Objects.requireNonNull(name, "name must not be null");
      Objects.requireNonNull(slug, "slug must not be null");
      Objects.requireNonNull(status, "status must not be null");
      matriculaHmac = matriculaHmac == null ? null : matriculaHmac.clone();
    }

    @Override
    public byte[] matriculaHmac() {
      return matriculaHmac == null ? null : matriculaHmac.clone();
    }
  }
}

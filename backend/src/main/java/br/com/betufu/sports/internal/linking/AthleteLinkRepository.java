package br.com.betufu.sports.internal.linking;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.betufu.sports.internal.linking.AthleteLinkModels.AthleteLinkProposal;
import br.com.betufu.sports.internal.linking.AthleteLinkModels.AthleteLinkProposalEntity;
import br.com.betufu.sports.internal.linking.AthleteLinkModels.AthleteLinkReviewEntity;
import br.com.betufu.sports.internal.linking.AthleteLinkModels.AthleteLinkReviewSummary;
import br.com.betufu.sports.internal.linking.AthleteLinkModels.AthleteMatch;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("runtime | integrated")
class AthleteLinkRepository {

    private final JdbcTemplate jdbc;

    AthleteLinkRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Optional<AthleteLinkProposalEntity> findProposalById(UUID proposalId) {
        return jdbc.query(
                "SELECT proposal_id, athlete_id, identity_id, status, lock_version, created_at, updated_at, "
                        + "decided_by_identity_id, decided_at, expires_at "
                        + "FROM public.sports_athlete_link_proposal WHERE proposal_id = ?",
                this::mapProposalEntity,
                proposalId)
                .stream().findFirst();
    }

    Optional<AthleteLinkProposal> findProposalWithAthleteById(UUID proposalId) {
        return jdbc.query(
                "SELECT p.proposal_id, p.athlete_id, a.name AS athlete_name, a.slug AS athlete_slug, "
                        + "p.status, p.created_at, p.updated_at, p.expires_at, p.decided_at "
                        + "FROM public.sports_athlete_link_proposal p "
                        + "JOIN public.sports_athlete a ON a.athlete_id = p.athlete_id "
                        + "WHERE p.proposal_id = ?",
                this::mapProposal,
                proposalId)
                .stream().findFirst();
    }

    Optional<AthleteLinkProposal> findPendingProposalByIdentityId(UUID identityId) {
        return jdbc.query(
                "SELECT p.proposal_id, p.athlete_id, a.name AS athlete_name, a.slug AS athlete_slug, "
                        + "p.status, p.created_at, p.updated_at, p.expires_at, p.decided_at "
                        + "FROM public.sports_athlete_link_proposal p "
                        + "JOIN public.sports_athlete a ON a.athlete_id = p.athlete_id "
                        + "WHERE p.identity_id = ? AND p.status = 'PENDENTE' "
                        + "AND p.expires_at > CURRENT_TIMESTAMP(6) "
                        + "ORDER BY p.created_at DESC LIMIT 1",
                this::mapProposal,
                identityId)
                .stream().findFirst();
    }

    List<AthleteMatch> findAthletesByHmac(byte[] hash) {
        if (hash == null) {
            return List.of();
        }
        return jdbc.query(
                "SELECT athlete_id, name, slug, matricula_hmac, hmac_version, status "
                        + "FROM public.sports_athlete WHERE matricula_hmac = ? ORDER BY created_at",
                this::mapAthleteMatch,
                hash);
    }

    Optional<AthleteMatch> findAthleteById(UUID athleteId) {
        if (athleteId == null) {
            return Optional.empty();
        }
        return jdbc.query(
                "SELECT athlete_id, name, slug, matricula_hmac, hmac_version, status "
                        + "FROM public.sports_athlete WHERE athlete_id = ?",
                this::mapAthleteMatch,
                athleteId)
                .stream().findFirst();
    }

    boolean hasAcceptedLinkForAthlete(UUID athleteId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM public.sports_athlete_link_proposal WHERE athlete_id = ? AND status = 'ACEITA'",
                Integer.class,
                athleteId);
        return count != null && count > 0;
    }

    boolean hasAcceptedLinkForIdentity(UUID identityId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM public.sports_athlete_link_proposal WHERE identity_id = ? AND status = 'ACEITA'",
                Integer.class,
                identityId);
        return count != null && count > 0;
    }

    Optional<AthleteLinkProposalEntity> findProposalByPair(UUID athleteId, UUID identityId) {
        return jdbc.query(
                "SELECT proposal_id, athlete_id, identity_id, status, lock_version, created_at, updated_at, "
                        + "decided_by_identity_id, decided_at, expires_at "
                        + "FROM public.sports_athlete_link_proposal WHERE athlete_id = ? AND identity_id = ? "
                        + "ORDER BY created_at DESC LIMIT 1",
                this::mapProposalEntity,
                athleteId, identityId)
                .stream().findFirst();
    }

    int insertProposal(UUID proposalId, UUID athleteId, UUID identityId, String status) {
        return jdbc.update(
                "INSERT INTO public.sports_athlete_link_proposal (proposal_id, athlete_id, identity_id, status) "
                        + "VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING",
                proposalId, athleteId, identityId, status);
    }

    int updateProposalStatus(
            UUID proposalId,
            long expectedLockVersion,
            String newStatus,
            UUID decidedByIdentityId) {
        return jdbc.update(
                "UPDATE public.sports_athlete_link_proposal "
                        + "SET status = ?, lock_version = lock_version + 1, updated_at = CURRENT_TIMESTAMP(6), "
                        + "decided_by_identity_id = ?, decided_at = CURRENT_TIMESTAMP(6) "
                        + "WHERE proposal_id = ? AND status = 'PENDENTE' AND lock_version = ? "
                        + "AND expires_at > CURRENT_TIMESTAMP(6)",
                newStatus, decidedByIdentityId, proposalId, expectedLockVersion);
    }

    int expireProposalIfNeeded(UUID proposalId) {
        return jdbc.update(
                "UPDATE public.sports_athlete_link_proposal "
                        + "SET status = 'EXPIRADA', lock_version = lock_version + 1, "
                        + "updated_at = CURRENT_TIMESTAMP(6), decided_at = CURRENT_TIMESTAMP(6) "
                        + "WHERE proposal_id = ? AND status = 'PENDENTE' "
                        + "AND expires_at <= CURRENT_TIMESTAMP(6)",
                proposalId);
    }

    int expirePendingProposalsForIdentity(UUID identityId) {
        return jdbc.update(
                "UPDATE public.sports_athlete_link_proposal "
                        + "SET status = 'EXPIRADA', lock_version = lock_version + 1, "
                        + "updated_at = CURRENT_TIMESTAMP(6), decided_at = CURRENT_TIMESTAMP(6) "
                        + "WHERE identity_id = ? AND status = 'PENDENTE' "
                        + "AND expires_at <= CURRENT_TIMESTAMP(6)",
                identityId);
    }

    int insertReview(UUID reviewId, UUID proposalId, UUID athleteId, UUID identityId, String reasonCode, String status) {
        return jdbc.update(
                "INSERT INTO public.sports_athlete_link_review (review_id, proposal_id, athlete_id, identity_id, reason_code, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                reviewId, proposalId, athleteId, identityId, reasonCode, status);
    }

    List<AthleteLinkReviewSummary> findReviews(int limit, int offset) {
        return jdbc.query(
                "SELECT r.review_id, r.proposal_id, r.athlete_id, a.name AS athlete_name, "
                        + "r.reason_code, r.status, r.created_at "
                        + "FROM public.sports_athlete_link_review r "
                        + "LEFT JOIN public.sports_athlete a ON a.athlete_id = r.athlete_id "
                        + "ORDER BY r.created_at DESC LIMIT ? OFFSET ?",
                this::mapReviewSummary,
                limit, offset);
    }

    long countReviews() {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM public.sports_athlete_link_review",
                Long.class);
        return count != null ? count : 0L;
    }

    Optional<AthleteLinkReviewEntity> findReviewByProposalId(UUID proposalId) {
        return jdbc.query(
                "SELECT review_id, proposal_id, athlete_id, identity_id, reason_code, status, lock_version, created_at, updated_at "
                        + "FROM public.sports_athlete_link_review WHERE proposal_id = ?",
                this::mapReviewEntity,
                proposalId)
                .stream().findFirst();
    }

    private AthleteLinkProposalEntity mapProposalEntity(ResultSet rs, int rowNum) throws SQLException {
        OffsetDateTime created = rs.getObject("created_at", OffsetDateTime.class);
        OffsetDateTime updated = rs.getObject("updated_at", OffsetDateTime.class);
        OffsetDateTime decided = rs.getObject("decided_at", OffsetDateTime.class);
        OffsetDateTime expires = rs.getObject("expires_at", OffsetDateTime.class);
        return new AthleteLinkProposalEntity(
                rs.getObject("proposal_id", UUID.class),
                rs.getObject("athlete_id", UUID.class),
                rs.getObject("identity_id", UUID.class),
                rs.getString("status"),
                rs.getLong("lock_version"),
                created != null ? created.toInstant() : Instant.EPOCH,
                updated != null ? updated.toInstant() : Instant.EPOCH,
                rs.getObject("decided_by_identity_id", UUID.class),
                decided != null ? decided.toInstant() : null,
                expires != null ? expires.toInstant() : Instant.EPOCH);
    }

    private AthleteLinkProposal mapProposal(ResultSet rs, int rowNum) throws SQLException {
        OffsetDateTime created = rs.getObject("created_at", OffsetDateTime.class);
        OffsetDateTime updated = rs.getObject("updated_at", OffsetDateTime.class);
        OffsetDateTime expires = rs.getObject("expires_at", OffsetDateTime.class);
        OffsetDateTime decided = rs.getObject("decided_at", OffsetDateTime.class);
        return new AthleteLinkProposal(
                rs.getObject("proposal_id", UUID.class),
                rs.getObject("athlete_id", UUID.class),
                rs.getString("athlete_name"),
                rs.getString("athlete_slug"),
                rs.getString("status"),
                created != null ? created.toInstant() : Instant.EPOCH,
                updated != null ? updated.toInstant() : Instant.EPOCH,
                expires != null ? expires.toInstant() : Instant.EPOCH,
                decided != null ? decided.toInstant() : null);
    }

    private AthleteLinkReviewSummary mapReviewSummary(ResultSet rs, int rowNum) throws SQLException {
        OffsetDateTime created = rs.getObject("created_at", OffsetDateTime.class);
        return new AthleteLinkReviewSummary(
                rs.getObject("review_id", UUID.class),
                rs.getObject("proposal_id", UUID.class),
                rs.getObject("athlete_id", UUID.class),
                rs.getString("athlete_name"),
                rs.getString("reason_code"),
                rs.getString("status"),
                created != null ? created.toInstant() : Instant.EPOCH);
    }

    private AthleteLinkReviewEntity mapReviewEntity(ResultSet rs, int rowNum) throws SQLException {
        OffsetDateTime created = rs.getObject("created_at", OffsetDateTime.class);
        OffsetDateTime updated = rs.getObject("updated_at", OffsetDateTime.class);
        return new AthleteLinkReviewEntity(
                rs.getObject("review_id", UUID.class),
                rs.getObject("proposal_id", UUID.class),
                rs.getObject("athlete_id", UUID.class),
                rs.getObject("identity_id", UUID.class),
                rs.getString("reason_code"),
                rs.getString("status"),
                rs.getLong("lock_version"),
                created != null ? created.toInstant() : Instant.EPOCH,
                updated != null ? updated.toInstant() : Instant.EPOCH);
    }

    private AthleteMatch mapAthleteMatch(ResultSet rs, int rowNum) throws SQLException {
        Short hmacVersion = rs.getObject("hmac_version", Short.class);
        return new AthleteMatch(
                rs.getObject("athlete_id", UUID.class),
                rs.getString("name"),
                rs.getString("slug"),
                rs.getBytes("matricula_hmac"),
                hmacVersion,
                rs.getString("status"));
    }
}

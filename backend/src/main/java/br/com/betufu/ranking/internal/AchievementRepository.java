package br.com.betufu.ranking.internal;

import br.com.betufu.ranking.AchievementQueryPort.Achievement;
import br.com.betufu.ranking.AchievementQueryPort.AwardState;
import br.com.betufu.ranking.internal.AchievementRuleCatalog.Candidate;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("runtime | integrated")
public class AchievementRepository {
  private final JdbcTemplate jdbc;

  public AchievementRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void grant(Candidate candidate) {
    jdbc.update(
        """
            INSERT INTO public.achievement_award
              (identity_id, code, catalog_edition, sports_edition_id,
               origin_key, origin_settlement_id)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (identity_id, code, catalog_edition, origin_key) DO NOTHING
            """,
        candidate.identityId(),
        candidate.code(),
        AchievementRuleCatalog.EDITION,
        candidate.editionId(),
        candidate.originKey(),
        candidate.originSettlementId());
  }

  public List<ActiveAward> findActiveAwards() {
    return jdbc.query(
        """
            SELECT award_id, identity_id, code, origin_key, origin_settlement_id
            FROM public.achievement_award WHERE state = 'AWARDED'
            """,
        (rs, row) ->
            new ActiveAward(
                rs.getObject("award_id", UUID.class),
                rs.getObject("identity_id", UUID.class),
                rs.getString("code"),
                rs.getString("origin_key"),
                rs.getObject("origin_settlement_id", UUID.class)));
  }

  public void revoke(UUID awardId, String reason) {
    int updated =
        jdbc.update(
            """
                UPDATE public.achievement_award
                SET state = 'REVOKED', revoked_at = CURRENT_TIMESTAMP(6), revocation_reason = ?
                WHERE award_id = ? AND state = 'AWARDED'
                """,
            reason,
            awardId);
    if (updated == 1) {
      jdbc.update(
          "INSERT INTO public.achievement_revocation (award_id, reason) VALUES (?, ?)",
          awardId,
          reason);
    }
  }

  public List<Achievement> findByIdentity(UUID identityId) {
    return jdbc.query(
        """
            SELECT a.award_id, a.code, c.name, c.description, c.rule_text,
                   a.catalog_edition, a.sports_edition_id, a.state,
                   a.awarded_at, a.revoked_at, a.revocation_reason
            FROM public.achievement_award a
            JOIN public.achievement_catalog c
              ON c.code = a.code AND c.edition = a.catalog_edition
            WHERE a.identity_id = ?
            ORDER BY a.awarded_at DESC, a.award_id
            """,
        (rs, row) ->
            new Achievement(
                rs.getObject("award_id", UUID.class),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("rule_text"),
                rs.getString("catalog_edition"),
                rs.getObject("sports_edition_id", UUID.class),
                AwardState.valueOf(rs.getString("state")),
                rs.getTimestamp("awarded_at").toInstant(),
                rs.getTimestamp("revoked_at") == null
                    ? null
                    : rs.getTimestamp("revoked_at").toInstant(),
                rs.getString("revocation_reason")),
        identityId);
  }

  public record ActiveAward(
      UUID awardId, UUID identityId, String code, String originKey, UUID originSettlementId) {}
}

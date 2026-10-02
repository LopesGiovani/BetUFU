package br.com.betufu.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.betufu.platform.identity.OperationalIdentityLookup;
import br.com.betufu.ranking.internal.GroupInvitationService;
import br.com.betufu.ranking.internal.GroupRepository;
import br.com.betufu.ranking.internal.GroupService;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PrivateGroupIT extends PrivateGroupFixture {
  @Test
  void ownerManagesGroupWithoutFinancialFacts() {
    UUID owner = identity("owner", true);
    UUID friend = identity("friend", true);
    UUID group = groups.create(ISSUER, owner.toString(), "  Amigos da UFU  ");
    assertThat(groups.findMine(ISSUER, owner.toString()))
        .containsExactly(
            new GroupQueryPort.GroupSummary(
                group, "Amigos da UFU", false, PrivateGroupPort.Role.OWNER));

    PrivateGroupPort.Invitation invitation =
        groups.issueInvitation(ISSUER, owner.toString(), group, Instant.now().plusSeconds(3600));
    assertThat(invitation.code()).hasSize(43);
    assertThat(groups.join(ISSUER, friend.toString(), invitation.code())).isEqualTo(group);
    assertThat(groups.findGroup(ISSUER, friend.toString(), group).orElseThrow().standings())
        .hasSize(2);
    groups.rename(ISSUER, owner.toString(), group, "Nova turma");
    assertThat(groups.findGroup(ISSUER, owner.toString(), group).orElseThrow().name())
        .isEqualTo("Nova turma");
    groups.close(ISSUER, owner.toString(), group);
    assertThat(groups.findGroup(ISSUER, friend.toString(), group).orElseThrow().closed()).isTrue();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.private_group_audit", Long.class))
        .isEqualTo(5);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.credit_ledger_entry", Long.class))
        .isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.prediction", Long.class)).isZero();
    assertThatThrownBy(() -> jdbc.update("DELETE FROM public.private_group_audit"))
        .hasMessageContaining("immutable");
  }

  @Test
  void memberCanLeaveAndRejoinWhileHistoryIsPreserved() {
    UUID owner = identity("owner", true);
    UUID friend = identity("friend", true);
    UUID group = groups.create(ISSUER, owner.toString(), "History");
    String first =
        groups
            .issueInvitation(ISSUER, owner.toString(), group, Instant.now().plusSeconds(3600))
            .code();
    groups.join(ISSUER, friend.toString(), first);
    groups.leave(ISSUER, friend.toString(), group);
    assertThat(groups.findGroup(ISSUER, friend.toString(), group)).isEmpty();
    String second =
        groups
            .issueInvitation(ISSUER, owner.toString(), group, Instant.now().plusSeconds(3600))
            .code();
    groups.join(ISSUER, friend.toString(), second);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM public.private_group_membership WHERE group_id = ? AND identity_id = ?",
                Long.class,
                group,
                friend))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM public.private_group_membership WHERE group_id = ? AND identity_id = ? AND left_at IS NULL",
                Long.class,
                group,
                friend))
        .isEqualTo(1);
  }
}

abstract class PrivateGroupFixture extends RankingFixture {
  static final String ISSUER = "https://idp.invalid";
  protected GroupService groups;

  @BeforeEach
  void setUpGroups() {
    OperationalIdentityLookup identities =
        new OperationalIdentityLookup() {
          @Override
          public Optional<OperationalIdentity> lookupIdentity(String issuer, String subject) {
            return jdbc
                .query(
                    "SELECT identity_id FROM public.local_identity WHERE issuer = ? AND subject = ?",
                    (rs, row) ->
                        new OperationalIdentity(rs.getObject("identity_id", UUID.class), Set.of()),
                    issuer,
                    subject)
                .stream()
                .findFirst();
          }
        };
    groups =
        new GroupService(
            new GroupRepository(jdbc),
            new GroupInvitationService(),
            service,
            identities,
            new DataSourceTransactionManager(jdbc.getDataSource()),
            Clock.systemUTC());
  }
}

package br.com.betufu.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PrivateGroupAuthorizationIT extends PrivateGroupFixture {
  @Test
  void nonMemberCannotReadRosterOrManageGroup() {
    UUID owner = identity("owner", true);
    UUID outsider = identity("outsider", true);
    UUID group = groups.create(ISSUER, owner.toString(), "Private");
    assertThat(groups.findMine(ISSUER, outsider.toString())).isEmpty();
    assertThat(groups.findGroup(ISSUER, outsider.toString(), group)).isEmpty();
    assertThatThrownBy(() -> groups.rename(ISSUER, outsider.toString(), group, "Stolen"))
        .isInstanceOf(NoSuchElementException.class)
        .hasMessage("group unavailable");
    assertThatThrownBy(
            () ->
                groups.issueInvitation(
                    ISSUER, outsider.toString(), group, Instant.now().plusSeconds(3600)))
        .isInstanceOf(NoSuchElementException.class)
        .hasMessage("group unavailable");
    assertThat(groups.findGroup(ISSUER, "unknown", group)).isEmpty();
  }

  @Test
  void oneUseExpiryAndClosedGroupRejectCodeWithoutDisclosingGroup() {
    UUID owner = identity("owner", true);
    UUID first = identity("first", true);
    UUID second = identity("second", true);
    UUID group = groups.create(ISSUER, owner.toString(), "Private");
    String code =
        groups
            .issueInvitation(ISSUER, owner.toString(), group, Instant.now().plusSeconds(3600))
            .code();
    groups.join(ISSUER, first.toString(), code);
    assertThatThrownBy(() -> groups.join(ISSUER, second.toString(), code))
        .isInstanceOf(NoSuchElementException.class)
        .hasMessage("invitation unavailable");
    assertThatThrownBy(() -> groups.join(ISSUER, second.toString(), "invalid"))
        .isInstanceOf(NoSuchElementException.class)
        .hasMessage("invitation unavailable");
    String expired =
        groups
            .issueInvitation(ISSUER, owner.toString(), group, Instant.now().plusSeconds(1))
            .code();
    jdbc.update(
        "UPDATE public.private_group_invitation SET expires_at = created_at + interval '1 microsecond' WHERE group_id = ? AND used_at IS NULL",
        group);
    assertThatThrownBy(() -> groups.join(ISSUER, second.toString(), expired))
        .isInstanceOf(NoSuchElementException.class)
        .hasMessage("invitation unavailable");
    String closed =
        groups
            .issueInvitation(ISSUER, owner.toString(), group, Instant.now().plusSeconds(3600))
            .code();
    groups.close(ISSUER, owner.toString(), group);
    assertThatThrownBy(() -> groups.join(ISSUER, second.toString(), closed))
        .isInstanceOf(NoSuchElementException.class)
        .hasMessage("invitation unavailable");
  }

  @Test
  void ownerAndAdminAndMemberHaveDistinctCapabilities() {
    UUID owner = identity("owner", true);
    UUID admin = identity("admin", true);
    UUID member = identity("member", true);
    UUID group = groups.create(ISSUER, owner.toString(), "Roles");
    groups.join(
        ISSUER,
        admin.toString(),
        groups
            .issueInvitation(ISSUER, owner.toString(), group, Instant.now().plusSeconds(3600))
            .code());
    groups.join(
        ISSUER,
        member.toString(),
        groups
            .issueInvitation(ISSUER, owner.toString(), group, Instant.now().plusSeconds(3600))
            .code());
    GroupQueryPort.GroupView view = groups.findGroup(ISSUER, owner.toString(), group).orElseThrow();
    UUID adminMembership =
        view.standings().stream()
            .filter(row -> row.nickname().equals("admin"))
            .findFirst()
            .orElseThrow()
            .membershipId();
    UUID memberMembership =
        view.standings().stream()
            .filter(row -> row.nickname().equals("member"))
            .findFirst()
            .orElseThrow()
            .membershipId();
    groups.changeRole(
        ISSUER, owner.toString(), group, adminMembership, PrivateGroupPort.Role.ADMIN);
    groups.issueInvitation(ISSUER, admin.toString(), group, Instant.now().plusSeconds(3600));
    assertThatThrownBy(() -> groups.rename(ISSUER, admin.toString(), group, "No"))
        .isInstanceOf(NoSuchElementException.class);
    assertThatThrownBy(() -> groups.remove(ISSUER, admin.toString(), group, adminMembership))
        .isInstanceOf(NoSuchElementException.class);
    groups.remove(ISSUER, admin.toString(), group, memberMembership);
    assertThat(groups.findGroup(ISSUER, member.toString(), group)).isEmpty();
    assertThatThrownBy(() -> groups.leave(ISSUER, owner.toString(), group))
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void rosterReadWaitsForMemberRemovalAndDoesNotReturnRevokedMember() throws Exception {
    UUID owner = identity("owner", true);
    UUID member = identity("member", true);
    UUID group = groups.create(ISSUER, owner.toString(), "Concurrent");
    groups.join(
        ISSUER,
        member.toString(),
        groups
            .issueInvitation(ISSUER, owner.toString(), group, Instant.now().plusSeconds(3600))
            .code());
    DriverManagerDataSource source =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    JdbcTemplate otherJdbc = new JdbcTemplate(source);
    TransactionTemplate transaction =
        new TransactionTemplate(new DataSourceTransactionManager(source));
    CountDownLatch revoked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch reading = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var remover =
          executor.submit(
              () ->
                  transaction.executeWithoutResult(
                      ignored -> {
                        otherJdbc.queryForObject(
                            "SELECT group_id FROM public.private_group WHERE group_id = ? FOR UPDATE",
                            UUID.class,
                            group);
                        otherJdbc.update(
                            "UPDATE public.private_group_membership SET left_at = CURRENT_TIMESTAMP(6) WHERE group_id = ? AND identity_id = ?",
                            group,
                            member);
                        revoked.countDown();
                        try {
                          if (!release.await(5, TimeUnit.SECONDS))
                            throw new IllegalStateException("release timeout");
                        } catch (InterruptedException interrupted) {
                          Thread.currentThread().interrupt();
                          throw new IllegalStateException(interrupted);
                        }
                      }));
      assertThat(revoked.await(5, TimeUnit.SECONDS)).isTrue();
      var reader =
          executor.submit(
              () -> {
                reading.countDown();
                return groups.findGroup(ISSUER, member.toString(), group);
              });
      assertThat(reading.await(5, TimeUnit.SECONDS)).isTrue();
      assertThatThrownBy(() -> reader.get(200, TimeUnit.MILLISECONDS))
          .isInstanceOf(java.util.concurrent.TimeoutException.class);
      release.countDown();
      remover.get(5, TimeUnit.SECONDS);
      assertThat(reader.get(5, TimeUnit.SECONDS)).isEmpty();
    } finally {
      release.countDown();
    }
  }
}

package br.com.betufu.ranking.internal;

import br.com.betufu.platform.identity.OperationalIdentityLookup;
import br.com.betufu.ranking.GroupQueryPort;
import br.com.betufu.ranking.PrivateGroupPort;
import br.com.betufu.ranking.RankingQueryPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("runtime | integrated")
public class GroupService implements PrivateGroupPort, GroupQueryPort {
  private static final String UNAVAILABLE = "group unavailable";
  private static final String INVALID_INVITATION = "invitation unavailable";

  private final GroupRepository repository;
  private final GroupInvitationService invitations;
  private final RankingQueryPort ranking;
  private final OperationalIdentityLookup identities;
  private final TransactionTemplate transactions;
  private final Clock clock;

  @Autowired
  public GroupService(
      GroupRepository repository,
      GroupInvitationService invitations,
      RankingQueryPort ranking,
      OperationalIdentityLookup identities,
      PlatformTransactionManager transactionManager,
      Clock clock) {
    this.repository = Objects.requireNonNull(repository);
    this.invitations = Objects.requireNonNull(invitations);
    this.ranking = Objects.requireNonNull(ranking);
    this.identities = Objects.requireNonNull(identities);
    this.transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager));
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public UUID create(String issuer, String subject, String name) {
    UUID actor = actor(issuer, subject);
    String safeName = name(name);
    return transactions.execute(
        ignored -> {
          UUID id = UUID.randomUUID();
          repository.create(id, actor, safeName);
          return id;
        });
  }

  @Override
  public void rename(String issuer, String subject, UUID groupId, String name) {
    UUID actor = actor(issuer, subject);
    String safeName = name(name);
    transactions.executeWithoutResult(
        ignored -> {
          activeGroup(groupId);
          requireRole(groupId, actor, Role.OWNER);
          repository.rename(groupId, safeName, actor);
        });
  }

  @Override
  public Invitation issueInvitation(
      String issuer, String subject, UUID groupId, Instant expiresAt) {
    UUID actor = actor(issuer, subject);
    Instant now = clock.instant();
    if (expiresAt == null
        || !expiresAt.isAfter(now)
        || expiresAt.isAfter(now.plus(Duration.ofDays(7)))) {
      throw new IllegalArgumentException("invitation expiry must be within seven days");
    }
    return transactions.execute(
        ignored -> {
          activeGroup(groupId);
          requireRole(groupId, actor, Role.OWNER, Role.ADMIN);
          String code = invitations.newCode();
          repository.insertInvitation(
              groupId, invitations.hash(code).orElseThrow(), actor, expiresAt);
          return new Invitation(code, expiresAt);
        });
  }

  @Override
  public UUID join(String issuer, String subject, String code) {
    UUID actor = actor(issuer, subject);
    byte[] hash = invitations.hash(code).orElseThrow(() -> invalidInvitation());
    return transactions.execute(
        ignored -> {
          GroupRepository.InvitationRow invitation =
              repository.findInvitation(hash).orElseThrow(() -> invalidInvitation());
          if (invitation.used()
              || invitation.revoked()
              || !invitation.expiresAt().isAfter(clock.instant())) {
            throw invalidInvitation();
          }
          GroupRepository.Group group =
              repository
                  .findGroup(invitation.groupId(), true)
                  .orElseThrow(() -> invalidInvitation());
          if (group.closed() || repository.findMember(group.id(), actor).isPresent()) {
            throw invalidInvitation();
          }
          repository.addMember(group.id(), actor, Role.MEMBER);
          repository.useInvitation(invitation.id(), actor, group.id());
          return group.id();
        });
  }

  @Override
  public void changeRole(
      String issuer, String subject, UUID groupId, UUID membershipId, Role role) {
    UUID actor = actor(issuer, subject);
    if (role != Role.ADMIN && role != Role.MEMBER)
      throw new IllegalArgumentException("invalid role");
    transactions.executeWithoutResult(
        ignored -> {
          activeGroup(groupId);
          requireRole(groupId, actor, Role.OWNER);
          GroupRepository.Member target = target(groupId, membershipId);
          if (target.role() == Role.OWNER) throw unavailable();
          if (target.role() != role) repository.changeRole(groupId, target, role, actor);
        });
  }

  @Override
  public void remove(String issuer, String subject, UUID groupId, UUID membershipId) {
    UUID actor = actor(issuer, subject);
    transactions.executeWithoutResult(
        ignored -> {
          activeGroup(groupId);
          GroupRepository.Member caller = requireRole(groupId, actor, Role.OWNER, Role.ADMIN);
          GroupRepository.Member target = target(groupId, membershipId);
          if (target.role() == Role.OWNER
              || (caller.role() == Role.ADMIN && target.role() != Role.MEMBER)) {
            throw unavailable();
          }
          repository.endMembership(groupId, target, actor, "REMOVED");
        });
  }

  @Override
  public void leave(String issuer, String subject, UUID groupId) {
    UUID actor = actor(issuer, subject);
    transactions.executeWithoutResult(
        ignored -> {
          activeGroup(groupId);
          GroupRepository.Member member = requireRole(groupId, actor, Role.ADMIN, Role.MEMBER);
          repository.endMembership(groupId, member, actor, "LEFT");
        });
  }

  @Override
  public void close(String issuer, String subject, UUID groupId) {
    UUID actor = actor(issuer, subject);
    transactions.executeWithoutResult(
        ignored -> {
          activeGroup(groupId);
          requireRole(groupId, actor, Role.OWNER);
          repository.close(groupId, actor);
        });
  }

  @Override
  public List<GroupSummary> findMine(String issuer, String subject) {
    Optional<UUID> caller = identities.findIdentityId(issuer, subject);
    if (caller.isEmpty()) return List.of();
    UUID actor = caller.orElseThrow();
    return repository.findMine(actor).stream()
        .map(row -> new GroupSummary(row.groupId(), row.name(), row.closed(), row.role()))
        .toList();
  }

  @Override
  public Optional<GroupView> findGroup(String issuer, String subject, UUID groupId) {
    Optional<UUID> caller = identities.findIdentityId(issuer, subject);
    if (caller.isEmpty()) return Optional.empty();
    UUID actor = caller.orElseThrow();
    if (groupId == null) return Optional.empty();
    return transactions.execute(
        ignored -> {
          Optional<GroupRepository.Group> found = repository.findGroup(groupId, true);
          if (found.isEmpty() || repository.findMember(groupId, actor).isEmpty()) {
            return Optional.empty();
          }
          List<GroupRepository.Member> members = repository.findMembers(groupId);
          Set<UUID> identitiesInGroup = new HashSet<>();
          Map<UUID, GroupRepository.Member> byIdentity = new HashMap<>();
          for (GroupRepository.Member member : members) {
            identitiesInGroup.add(member.identityId());
            byIdentity.put(member.identityId(), member);
          }
          List<Standing> standings = new ArrayList<>();
          for (RankingQueryPort.ScopedMember ranked : ranking.rankMembers(identitiesInGroup)) {
            GroupRepository.Member member = byIdentity.get(ranked.identityId());
            standings.add(
                new Standing(
                    member.membershipId(),
                    ranked.nickname(),
                    member.role(),
                    ranked.metrics().position(),
                    ranked.metrics().wins(),
                    ranked.metrics().losses(),
                    ranked.metrics().hitRate(),
                    ranked.metrics().verificationActive()));
          }
          GroupRepository.Group group = found.orElseThrow();
          return Optional.of(
              new GroupView(group.id(), group.name(), group.closed(), List.copyOf(standings)));
        });
  }

  private UUID actor(String issuer, String subject) {
    return identities.findIdentityId(issuer, subject).orElseThrow(GroupService::unavailable);
  }

  private GroupRepository.Group activeGroup(UUID groupId) {
    GroupRepository.Group group =
        repository.findGroup(groupId, true).orElseThrow(GroupService::unavailable);
    if (group.closed()) throw unavailable();
    return group;
  }

  private GroupRepository.Member requireRole(UUID groupId, UUID actor, Role... allowed) {
    GroupRepository.Member member =
        repository.findMember(groupId, actor).orElseThrow(GroupService::unavailable);
    for (Role role : allowed) {
      if (member.role() == role) return member;
    }
    throw unavailable();
  }

  private GroupRepository.Member target(UUID groupId, UUID membershipId) {
    return repository.findMemberById(groupId, membershipId).orElseThrow(GroupService::unavailable);
  }

  private static String name(String value) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException("invalid group name");
    String trimmed = value.strip();
    if (trimmed.length() > 80) throw new IllegalArgumentException("invalid group name");
    return trimmed;
  }

  private static NoSuchElementException unavailable() {
    return new NoSuchElementException(UNAVAILABLE);
  }

  private static NoSuchElementException invalidInvitation() {
    return new NoSuchElementException(INVALID_INVITATION);
  }
}

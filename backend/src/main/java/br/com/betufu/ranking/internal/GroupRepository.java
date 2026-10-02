package br.com.betufu.ranking.internal;

import br.com.betufu.ranking.PrivateGroupPort.Role;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("runtime | integrated")
public class GroupRepository {
  private final JdbcTemplate jdbc;

  public GroupRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  void create(UUID groupId, UUID ownerId, String name) {
    jdbc.update(
        "INSERT INTO public.private_group (group_id, owner_identity_id, name) VALUES (?, ?, ?)",
        groupId,
        ownerId,
        name);
    addMember(groupId, ownerId, Role.OWNER);
    audit(groupId, "CREATED", ownerId, ownerId, name);
  }

  Optional<Group> findGroup(UUID groupId, boolean lock) {
    return jdbc
        .query(
            "SELECT group_id, name, status FROM public.private_group WHERE group_id = ?"
                + (lock ? " FOR UPDATE" : ""),
            (rs, row) ->
                new Group(
                    rs.getObject("group_id", UUID.class),
                    rs.getString("name"),
                    "CLOSED".equals(rs.getString("status"))),
            groupId)
        .stream()
        .findFirst();
  }

  Optional<Member> findMember(UUID groupId, UUID identityId) {
    return jdbc
        .query(
            "SELECT membership_id, identity_id, role FROM public.private_group_membership "
                + "WHERE group_id = ? AND identity_id = ? AND left_at IS NULL",
            (rs, row) ->
                new Member(
                    rs.getObject("membership_id", UUID.class),
                    rs.getObject("identity_id", UUID.class),
                    Role.valueOf(rs.getString("role"))),
            groupId,
            identityId)
        .stream()
        .findFirst();
  }

  Optional<Member> findMemberById(UUID groupId, UUID membershipId) {
    return jdbc
        .query(
            "SELECT membership_id, identity_id, role FROM public.private_group_membership "
                + "WHERE group_id = ? AND membership_id = ? AND left_at IS NULL",
            (rs, row) ->
                new Member(
                    rs.getObject("membership_id", UUID.class),
                    rs.getObject("identity_id", UUID.class),
                    Role.valueOf(rs.getString("role"))),
            groupId,
            membershipId)
        .stream()
        .findFirst();
  }

  List<Member> findMembers(UUID groupId) {
    return jdbc.query(
        "SELECT membership_id, identity_id, role FROM public.private_group_membership "
            + "WHERE group_id = ? AND left_at IS NULL ORDER BY joined_at, membership_id",
        (rs, row) ->
            new Member(
                rs.getObject("membership_id", UUID.class),
                rs.getObject("identity_id", UUID.class),
                Role.valueOf(rs.getString("role"))),
        groupId);
  }

  List<Mine> findMine(UUID identityId) {
    return jdbc.query(
        "SELECT g.group_id, g.name, g.status, m.role FROM public.private_group g "
            + "JOIN public.private_group_membership m ON m.group_id = g.group_id "
            + "WHERE m.identity_id = ? AND m.left_at IS NULL ORDER BY g.created_at DESC, g.group_id",
        (rs, row) ->
            new Mine(
                rs.getObject("group_id", UUID.class),
                rs.getString("name"),
                "CLOSED".equals(rs.getString("status")),
                Role.valueOf(rs.getString("role"))),
        identityId);
  }

  void rename(UUID groupId, String name, UUID actor) {
    jdbc.update(
        "UPDATE public.private_group SET name = ?, updated_at = CURRENT_TIMESTAMP(6) WHERE group_id = ?",
        name,
        groupId);
    audit(groupId, "RENAMED", actor, null, name);
  }

  void close(UUID groupId, UUID actor) {
    jdbc.update(
        "UPDATE public.private_group SET status = 'CLOSED', closed_at = CURRENT_TIMESTAMP(6), "
            + "updated_at = CURRENT_TIMESTAMP(6) WHERE group_id = ?",
        groupId);
    audit(groupId, "CLOSED", actor, null, null);
  }

  void addMember(UUID groupId, UUID identityId, Role role) {
    jdbc.update(
        "INSERT INTO public.private_group_membership (group_id, identity_id, role) VALUES (?, ?, ?)",
        groupId,
        identityId,
        role.name());
  }

  void changeRole(UUID groupId, Member member, Role role, UUID actor) {
    jdbc.update(
        "UPDATE public.private_group_membership SET role = ? WHERE membership_id = ?",
        role.name(),
        member.membershipId());
    audit(groupId, role == Role.ADMIN ? "PROMOTED" : "DEMOTED", actor, member.identityId(), null);
  }

  void endMembership(UUID groupId, Member member, UUID actor, String eventType) {
    jdbc.update(
        "UPDATE public.private_group_membership SET left_at = CURRENT_TIMESTAMP(6) "
            + "WHERE membership_id = ? AND left_at IS NULL",
        member.membershipId());
    audit(groupId, eventType, actor, member.identityId(), null);
  }

  void insertInvitation(UUID groupId, byte[] hash, UUID actor, Instant expiresAt) {
    jdbc.update(
        "INSERT INTO public.private_group_invitation "
            + "(group_id, token_hash, created_by_identity_id, expires_at) VALUES (?, ?, ?, ?)",
        groupId,
        hash,
        actor,
        Timestamp.from(expiresAt));
    audit(groupId, "INVITED", actor, null, null);
  }

  Optional<InvitationRow> findInvitation(byte[] hash) {
    return jdbc
        .query(
            "SELECT invitation_id, group_id, expires_at, used_at, revoked_at "
                + "FROM public.private_group_invitation WHERE token_hash = ? FOR UPDATE",
            (rs, row) ->
                new InvitationRow(
                    rs.getObject("invitation_id", UUID.class),
                    rs.getObject("group_id", UUID.class),
                    rs.getTimestamp("expires_at").toInstant(),
                    rs.getTimestamp("used_at") != null,
                    rs.getTimestamp("revoked_at") != null),
            hash)
        .stream()
        .findFirst();
  }

  void useInvitation(UUID invitationId, UUID identityId, UUID groupId) {
    jdbc.update(
        "UPDATE public.private_group_invitation SET used_by_identity_id = ?, "
            + "used_at = CURRENT_TIMESTAMP(6) WHERE invitation_id = ? AND used_at IS NULL",
        identityId,
        invitationId);
    audit(groupId, "JOINED", identityId, identityId, null);
  }

  private void audit(UUID groupId, String type, UUID actor, UUID subject, String detail) {
    jdbc.update(
        "INSERT INTO public.private_group_audit "
            + "(group_id, event_type, actor_identity_id, subject_identity_id, detail) "
            + "VALUES (?, ?, ?, ?, ?)",
        groupId,
        type,
        actor,
        subject,
        detail);
  }

  record Group(UUID id, String name, boolean closed) {}

  record Member(UUID membershipId, UUID identityId, Role role) {}

  record Mine(UUID groupId, String name, boolean closed, Role role) {}

  record InvitationRow(UUID id, UUID groupId, Instant expiresAt, boolean used, boolean revoked) {}
}

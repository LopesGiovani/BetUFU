package br.com.betufu.ranking;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Reads are scoped to the authenticated group member and expose no identity-provider details. */
public interface GroupQueryPort {
  List<GroupSummary> findMine(String issuer, String subject);

  Optional<GroupView> findGroup(String issuer, String subject, UUID groupId);

  record GroupSummary(UUID groupId, String name, boolean closed, PrivateGroupPort.Role myRole) {}

  record GroupView(UUID groupId, String name, boolean closed, List<Standing> standings) {}

  record Standing(
      UUID membershipId,
      String nickname,
      PrivateGroupPort.Role role,
      int position,
      long wins,
      long losses,
      BigDecimal hitRate,
      boolean verificationActive) {}
}

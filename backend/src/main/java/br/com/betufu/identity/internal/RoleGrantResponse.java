package br.com.betufu.identity.internal;

import br.com.betufu.identity.OperationalRole;
import java.util.List;
import java.util.UUID;

public record RoleGrantResponse(UUID identityId, String nickname, List<RoleStatusDto> roles) {

  public record RoleStatusDto(OperationalRole role, String status, UUID scopeRef) {

    public RoleStatusDto(OperationalRole role, String status) {
      this(role, status, null);
    }
  }
}

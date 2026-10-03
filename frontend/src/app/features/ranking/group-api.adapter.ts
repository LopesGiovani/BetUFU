import { Injectable, inject } from '@angular/core';
import { PrivateApiRecovery } from '../../core/session/private-api-recovery';
import { GroupService, PrivateGroupRole } from '../../api/generated';
@Injectable({ providedIn: 'root' })
export class GroupApiAdapter {
  private readonly recovery = inject(PrivateApiRecovery);
  private readonly api = inject(GroupService);
  async list() {
    return (await this.recovery.read(this.api.getMyGroups())).items;
  }
  read(groupId: string) {
    return this.recovery.read(this.api.getPrivateGroup({ groupId }));
  }
  create(name: string) {
    return this.recovery.read(this.api.createPrivateGroup({ privateGroupNameRequest: { name } }));
  }
  join(code: string) {
    return this.recovery.read(this.api.joinPrivateGroup({ privateGroupJoinRequest: { code } }));
  }
  invite(groupId: string) {
    return this.recovery.read(
      this.api.issuePrivateGroupInvitation({
        groupId,
        privateGroupInvitationRequest: { expiresAt: new Date(Date.now() + 86400000).toISOString() },
      }),
    );
  }
  rename(groupId: string, name: string) {
    return this.recovery.read(
      this.api.renamePrivateGroup({ groupId, privateGroupNameRequest: { name } }),
    );
  }
  role(groupId: string, membershipId: string, role: PrivateGroupRole) {
    return this.recovery.read(
      this.api.changePrivateGroupMemberRole({
        groupId,
        membershipId,
        privateGroupRoleRequest: { role },
      }),
    );
  }
  leave(groupId: string) {
    return this.recovery.read(this.api.leavePrivateGroup({ groupId }));
  }
  close(groupId: string) {
    return this.recovery.read(this.api.closePrivateGroup({ groupId }));
  }
  remove(groupId: string, membershipId: string) {
    return this.recovery.read(this.api.removePrivateGroupMember({ groupId, membershipId }));
  }
}

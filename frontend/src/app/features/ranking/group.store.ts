import { Injectable, computed, inject, signal } from '@angular/core';
import { PrivateGroup, PrivateGroupInvitation, PrivateGroupSummary } from '../../api/generated';
import { GroupApiAdapter } from './group-api.adapter';
@Injectable()
export class GroupStore {
  readonly api = inject(GroupApiAdapter);
  readonly groups = signal<PrivateGroupSummary[]>([]);
  readonly selected = signal<PrivateGroup | null>(null);
  readonly loading = signal(false);
  readonly busy = signal(false);
  readonly error = signal(false);
  readonly message = signal('');
  readonly invitation = signal<PrivateGroupInvitation | null>(null);
  readonly myRole = computed(
    () => this.groups().find((g) => g.groupId === this.selected()?.groupId)?.myRole,
  );
  private generation = 0;
  async load() {
    const generation = ++this.generation;
    this.loading.set(true);
    this.error.set(false);
    this.selected.set(null);
    this.invitation.set(null);
    try {
      const groups = await this.api.list();
      if (generation === this.generation) this.groups.set(groups);
    } catch {
      if (generation === this.generation) {
        this.groups.set([]);
        this.error.set(true);
      }
    } finally {
      if (generation === this.generation) this.loading.set(false);
    }
  }
  async open(groupId: string) {
    if (this.busy() || this.loading()) return;
    const generation = ++this.generation;
    this.selected.set(null);
    this.invitation.set(null);
    this.loading.set(true);
    this.message.set('');
    try {
      const group = await this.api.read(groupId);
      if (generation === this.generation) this.selected.set(group);
    } catch {
      this.message.set('Nao foi possivel acessar este grupo. Atualize seus grupos.');
    } finally {
      this.loading.set(false);
    }
  }
  async mutate(action: () => Promise<unknown>, invite = false) {
    if (this.busy() || this.loading()) return;
    this.busy.set(true);
    this.message.set('');
    this.invitation.set(null);
    try {
      await action();
      await this.load();
      this.message.set('Alteracao registrada.');
    } catch {
      this.message.set(
        invite
          ? 'Nao foi possivel entrar neste grupo com esse convite. Confira o codigo ou solicite outro convite.'
          : 'Nao foi possivel concluir a acao. Atualize seus grupos antes de tentar novamente.',
      );
    } finally {
      this.busy.set(false);
    }
  }
  async issueInvitation() {
    const group = this.selected();
    if (!group || this.busy()) return;
    this.busy.set(true);
    this.invitation.set(null);
    this.message.set('');
    try {
      this.invitation.set(await this.api.invite(group.groupId));
    } catch {
      this.message.set('Nao foi possivel emitir o convite. Atualize seus grupos.');
    } finally {
      this.busy.set(false);
    }
  }
}

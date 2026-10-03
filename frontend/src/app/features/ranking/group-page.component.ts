import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnInit,
  ViewChild,
  inject,
  signal,
} from '@angular/core';
import { DatePipe, PercentPipe } from '@angular/common';
import { FormControl, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { GroupStore } from './group.store';
type DestructiveAction = {
  kind: 'leave' | 'close' | 'remove';
  groupId: string;
  name: string;
  membershipId?: string;
};
@Component({
  selector: 'app-group-page',
  standalone: true,
  imports: [FormsModule, ReactiveFormsModule, PercentPipe, DatePipe],
  providers: [GroupStore],
  templateUrl: './group-page.component.html',
  styleUrl: './group-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GroupPageComponent implements OnInit {
  readonly store = inject(GroupStore);
  readonly name = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(80)],
  });
  readonly code = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(64)],
  });
  readonly rename = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(80)],
  });
  readonly pending = signal<DestructiveAction | null>(null);
  @ViewChild('confirmation') confirmation?: ElementRef<HTMLDialogElement>;
  private trigger: HTMLElement | null = null;
  ngOnInit() {
    void this.store.load();
  }
  create() {
    if (this.name.valid && this.name.value.trim())
      void this.store.mutate(() => this.store.api.create(this.name.value.trim()));
  }
  join() {
    if (this.code.valid && this.code.value.trim())
      void this.store.mutate(() => this.store.api.join(this.code.value.trim()), true);
  }
  renameGroup() {
    const g = this.store.selected();
    if (g && this.rename.valid && this.rename.value.trim())
      void this.store.mutate(() => this.store.api.rename(g.groupId, this.rename.value.trim()));
  }
  changeRole(membershipId: string, role: 'ADMIN' | 'MEMBER') {
    const group = this.store.selected();
    if (group) void this.store.mutate(() => this.store.api.role(group.groupId, membershipId, role));
  }
  ask(kind: DestructiveAction['kind'], event: Event, membershipId?: string) {
    const group = this.store.selected();
    if (!group || this.store.busy()) return;
    this.trigger = event.currentTarget as HTMLElement;
    this.pending.set({ kind, groupId: group.groupId, name: group.name, membershipId });
    this.confirmation?.nativeElement.showModal();
  }
  cancel(event?: Event) {
    event?.preventDefault();
    this.confirmation?.nativeElement.close();
    this.pending.set(null);
    this.trigger?.focus();
  }
  containFocus(event: KeyboardEvent) {
    if (event.key !== 'Tab') return;
    const buttons =
      this.confirmation?.nativeElement.querySelectorAll<HTMLButtonElement>('button:not(:disabled)');
    if (!buttons?.length) return;
    const first = buttons[0];
    const last = buttons[buttons.length - 1];
    if (event.shiftKey ? event.target === first : event.target === last) {
      event.preventDefault();
      (event.shiftKey ? last : first).focus();
    }
  }
  async confirm() {
    const p = this.pending();
    if (!p) return;
    this.cancel();
    await this.store.mutate(() =>
      p.kind === 'leave'
        ? this.store.api.leave(p.groupId)
        : p.kind === 'close'
          ? this.store.api.close(p.groupId)
          : this.store.api.remove(p.groupId, p.membershipId!),
    );
  }
}

import { ChangeDetectionStrategy, Component, DestroyRef, inject, input, signal } from '@angular/core';
import { AthleteProfileStore } from './athlete-profile.store';

@Component({
  selector: 'app-athlete-photo-field',
  standalone: true,
  template: `
    <section class="photo-field" aria-labelledby="athlete-photo-title">
      <div class="section-head">
        <div>
          <p class="eyebrow">Midia processada</p>
          <h2 id="athlete-photo-title">Foto</h2>
        </div>
        <span>JPEG ou PNG, ate 5 MiB</span>
      </div>

      <div class="photo-layout">
        <div class="photo-preview" aria-live="polite">
          @if (store.photoUrl(); as photoUrl) {
            <img [src]="photoUrl" [alt]="'Foto de ' + athleteName()" />
          } @else {
            <span class="avatar-fallback" aria-hidden="true">{{ athleteName().charAt(0) }}</span>
          }
        </div>

        <div class="photo-actions">
          <p class="help" id="athlete-photo-help">
            Escolha uma imagem ate 5 MiB. A BETUFU processa a foto antes de publica-la e remove
            metadados.
          </p>
          <label class="button button--secondary" for="athlete-photo-input">
            {{ store.photoErrorTitle() ? 'Escolher outra foto' : 'Escolher foto' }}
          </label>
          <input
            id="athlete-photo-input"
            class="file-input"
            type="file"
            accept="image/jpeg,image/png"
            [disabled]="store.isPhotoUploading() || store.isPhotoRemoving()"
            aria-describedby="athlete-photo-help athlete-photo-status athlete-photo-error"
            (change)="onFileSelected($event)" />

          @if (store.selectedPhoto(); as selectedPhoto) {
            <p class="selection" id="athlete-photo-status">
              Imagem selecionada para processamento ({{ formatBytes(selectedPhoto.size) }}).
            </p>
          }
          @if (store.isPhotoLoading()) {
            <p class="status" id="athlete-photo-status" role="status">Carregando foto...</p>
          }
          @if (store.isPhotoUploading()) {
            <div class="progress-block" id="athlete-photo-status" role="status">
              <progress [value]="store.photoProgress() ?? 0" max="100"></progress>
              <span>Enviando foto... {{ store.photoProgress() ?? 0 }}%</span>
            </div>
          }

          <div class="photo-buttons">
            <button
              class="button button--primary"
              type="button"
              [disabled]="!store.selectedPhoto() || store.isPhotoUploading() || store.isPhotoRemoving()"
              (click)="upload()">
              {{ store.isPhotoUploading() ? 'Enviando foto...' : 'Enviar foto' }}
            </button>
            @if (store.hasPhoto()) {
              <button
                class="button button--danger"
                type="button"
                [disabled]="store.isPhotoUploading() || store.isPhotoRemoving()"
                (click)="requestRemoval()">
                {{ store.isPhotoRemoving() ? 'Removendo foto...' : 'Remover foto' }}
              </button>
            }
          </div>

          @if (confirmingRemoval()) {
            <div class="confirmation" role="group" aria-labelledby="remove-photo-title">
              <strong id="remove-photo-title">Remover sua foto pública?</strong>
              <div class="photo-buttons">
                <button class="button button--danger" type="button" (click)="confirmRemoval()">
                  Remover foto
                </button>
                <button class="button button--secondary" type="button" (click)="cancelRemoval()">
                  Manter foto
                </button>
              </div>
            </div>
          }

          @if (store.photoErrorMessage(); as message) {
            <section class="feedback feedback--error" id="athlete-photo-error" role="alert">
              <strong>{{ store.photoErrorTitle() }}</strong>
              <p>{{ message }}</p>
              @if (store.selectedPhoto() && !store.isPhotoUploading()) {
                <button class="button button--secondary" type="button" (click)="upload()">
                  Tentar enviar novamente
                </button>
              }
            </section>
          }
          @if (store.photoFeedbackMessage(); as feedback) {
            <p class="live-region" role="status" aria-live="polite">{{ feedback }}</p>
          }
        </div>
      </div>
    </section>
  `,
  styles: `
    :host { display: block; }
    .photo-field {
      display: grid;
      gap: var(--bt-space-4);
      margin-bottom: var(--bt-space-6);
      padding: var(--bt-space-6);
      border: 1px solid var(--bt-border);
      border-radius: var(--bt-radius-card);
      background: var(--bt-surface);
    }
    .section-head { display: flex; justify-content: space-between; gap: var(--bt-space-4); }
    .section-head > span, .help, .selection, .status, .progress-block span { color: var(--bt-text-muted); }
    .section-head > span, .eyebrow { font-size: var(--bt-type-label); }
    .eyebrow { margin: 0; color: var(--bt-signal-soft); letter-spacing: .08em; text-transform: uppercase; }
    h2 { margin: var(--bt-space-1) 0 0; font: 600 var(--bt-type-heading)/.9 'Barlow Condensed', sans-serif; text-transform: uppercase; }
    .photo-layout { display: grid; grid-template-columns: minmax(120px, 180px) 1fr; gap: var(--bt-space-6); align-items: center; }
    .photo-preview { width: 140px; aspect-ratio: 1; overflow: hidden; display: grid; place-items: center; border: 1px solid var(--bt-border); border-radius: 50%; background: var(--bt-surface-raised); }
    .photo-preview img { width: 100%; height: 100%; object-fit: cover; }
    .avatar-fallback { color: var(--bt-signal); font: 600 3rem/.9 'Barlow Condensed', sans-serif; }
    .photo-actions { display: grid; gap: var(--bt-space-3); }
    .help, .selection, .status, .live-region { margin: 0; }
    .file-input { width: 1px; height: 1px; overflow: hidden; position: absolute; clip: rect(0 0 0 0); }
    .button { min-height: 44px; width: max-content; display: inline-flex; align-items: center; justify-content: center; padding: 0 var(--bt-space-4); border: 0; border-radius: var(--bt-radius-field); font-weight: 700; cursor: pointer; text-decoration: none; }
    .button:focus-visible { outline: 3px solid var(--bt-signal); outline-offset: 3px; }
    .button:disabled { cursor: not-allowed; opacity: .55; }
    .button--primary { background: var(--bt-signal); color: var(--bt-ink); }
    .button--secondary { border: 1px solid var(--bt-border); background: var(--bt-surface-raised); color: var(--bt-text); }
    .button--danger { background: var(--bt-danger); color: var(--bt-ink); }
    .photo-buttons { display: flex; flex-wrap: wrap; gap: var(--bt-space-2); }
    .progress-block { display: grid; gap: var(--bt-space-1); }
    progress { width: min(100%, 28rem); accent-color: var(--bt-signal); }
    .confirmation, .feedback { display: grid; gap: var(--bt-space-2); padding: var(--bt-space-3); border: 1px solid var(--bt-border); }
    .feedback--error { border-color: var(--bt-danger); }
    .feedback p { margin: 0; color: var(--bt-text-muted); }
    @media (max-width: 640px) {
      .photo-field { padding: var(--bt-space-4); }
      .photo-layout { grid-template-columns: 1fr; }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AthletePhotoFieldComponent {
  readonly athleteName = input.required<string>();
  readonly store = inject(AthleteProfileStore);
  private readonly confirmingRemovalState = signal(false);
  readonly confirmingRemoval = this.confirmingRemovalState.asReadonly();

  constructor() {
    inject(DestroyRef).onDestroy(() => this.store.releasePhotoResources());
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = input.files;
    const file = files && typeof files.item === 'function' ? files.item(0) : files?.[0];
    this.store.selectPhoto(file ?? null);
    input.value = '';
  }

  async upload(): Promise<void> {
    await this.store.uploadPhoto();
  }

  requestRemoval(): void {
    this.confirmingRemovalState.set(true);
  }

  cancelRemoval(): void {
    this.confirmingRemovalState.set(false);
  }

  async confirmRemoval(): Promise<void> {
    if (await this.store.removePhoto()) this.confirmingRemovalState.set(false);
  }

  formatBytes(bytes: number): string {
    return `${(bytes / (1024 * 1024)).toFixed(2)} MiB`;
  }
}

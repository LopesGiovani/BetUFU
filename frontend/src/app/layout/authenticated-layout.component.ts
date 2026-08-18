import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { SessionStore } from '../core/session/session.store';

@Component({
  selector: 'app-authenticated-layout',
  standalone: true,
  imports: [CommonModule, RouterOutlet],
  templateUrl: './authenticated-layout.component.html',
  styleUrl: './authenticated-layout.component.scss',
})
export class AuthenticatedLayoutComponent implements OnInit {
  readonly sessionStore = inject(SessionStore);

  ngOnInit(): void {
    void this.sessionStore.loadSession();
  }

  async onLogout(): Promise<void> {
    const result = await this.sessionStore.logoutCurrentDevice();
    if (result?.endSessionUrl && (result.endSessionUrl.startsWith('http://') || result.endSessionUrl.startsWith('https://') || result.endSessionUrl.startsWith('/'))) {
      window.location.href = result.endSessionUrl;
    }
  }

  onLogin(): void {
    // Redireciona para o fluxo OIDC Authorization Code gerenciado pelo Spring BFF
    window.location.href = '/oauth2/authorization/keycloak';
  }
}

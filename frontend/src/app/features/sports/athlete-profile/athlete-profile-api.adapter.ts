import { Injectable, inject } from '@angular/core';
import { Observable, defer, map, throwError } from 'rxjs';
import { AthletePublicProfileService } from '../../../api/generated/api/athlete-public-profile.service';
import { AthletePublicProfile } from '../../../api/generated/model/athlete-public-profile';
import { AthletePublicProfileUpdateRequest } from '../../../api/generated/model/athlete-public-profile-update-request';

type SocialField = 'instagramUrl' | 'xUrl' | 'linkedinUrl' | 'stravaUrl' | 'youtubeUrl';

const SOCIAL_HOSTS: Readonly<Record<SocialField, ReadonlySet<string>>> = Object.freeze({
  instagramUrl: new Set(['instagram.com', 'www.instagram.com', 'instagr.am']),
  xUrl: new Set(['x.com', 'www.x.com', 'twitter.com', 'www.twitter.com']),
  linkedinUrl: new Set(['linkedin.com', 'www.linkedin.com']),
  stravaUrl: new Set(['strava.com', 'www.strava.com']),
  youtubeUrl: new Set(['youtube.com', 'www.youtube.com', 'youtu.be']),
});

@Injectable({ providedIn: 'root' })
export class AthleteProfileApiAdapter {
  private readonly service = inject(AthletePublicProfileService);

  fetchMine(): Observable<AthletePublicProfile> {
    return defer(() => this.service.getMyAthleteProfile()).pipe(
      map((response) => this.adapt(response)),
    );
  }

  updateMine(request: AthletePublicProfileUpdateRequest): Observable<AthletePublicProfile> {
    return defer(() => this.service.updateMyAthleteProfile({
      athletePublicProfileUpdateRequest: request,
    })).pipe(map((response) => this.adapt(response)));
  }

  clearMine(): Observable<AthletePublicProfile> {
    return defer(() => this.service.deleteMyAthleteProfile()).pipe(
      map((response) => this.adapt(response)),
    );
  }

  fetchPublic(athleteId: string): Observable<AthletePublicProfile> {
    const normalizedId = athleteId.trim();
    if (!normalizedId) {
      return throwError(() => new Error('CONTRATO_PERFIL_ATLETA_INVALIDO: identificador ausente.'));
    }
    return defer(() => this.service.getAthletePublicProfile({ athleteId: normalizedId })).pipe(
      map((response) => this.adapt(response, normalizedId)),
    );
  }

  private adapt(response: AthletePublicProfile | null | undefined, expectedAthleteId?: string): AthletePublicProfile {
    if (!response
      || typeof response.athleteId !== 'string'
      || typeof response.athleteName !== 'string'
      || typeof response.athleteSlug !== 'string'
      || !Number.isInteger(response.lockVersion)
      || response.lockVersion < 0
      || typeof response.updatedAt !== 'string'
      || Number.isNaN(Date.parse(response.updatedAt))) {
      throw new Error('CONTRATO_PERFIL_ATLETA_INVALIDO: resposta ausente ou malformada.');
    }
    if (expectedAthleteId && response.athleteId !== expectedAthleteId) {
      throw new Error('CONTRATO_PERFIL_ATLETA_INVALIDO: atleta diverge da rota publica.');
    }

    const course = this.optionalText(response.course, 120, 'curso');
    const bio = this.optionalText(response.bio, 500, 'biografia');
    const instagramUrl = this.socialUrl('instagramUrl', response.instagramUrl);
    const xUrl = this.socialUrl('xUrl', response.xUrl);
    const linkedinUrl = this.socialUrl('linkedinUrl', response.linkedinUrl);
    const stravaUrl = this.socialUrl('stravaUrl', response.stravaUrl);
    const youtubeUrl = this.socialUrl('youtubeUrl', response.youtubeUrl);

    return Object.freeze({
      athleteId: response.athleteId,
      athleteName: response.athleteName.trim(),
      athleteSlug: response.athleteSlug.trim(),
      course,
      bio,
      instagramUrl,
      xUrl,
      linkedinUrl,
      stravaUrl,
      youtubeUrl,
      lockVersion: response.lockVersion,
      updatedAt: response.updatedAt,
    });
  }

  private optionalText(value: unknown, maxLength: number, label: string): string | null {
    if (value === null || value === undefined || value === '') return null;
    if (typeof value !== 'string' || value.length > maxLength) {
      throw new Error(`CONTRATO_PERFIL_ATLETA_INVALIDO: ${label} invalida.`);
    }
    return value;
  }

  private socialUrl(field: SocialField, value: unknown): string | null {
    if (value === null || value === undefined || value === '') return null;
    if (typeof value !== 'string' || value.length > 255) {
      throw new Error('CONTRATO_PERFIL_ATLETA_INVALIDO: link social invalido.');
    }
    let parsed: URL;
    try {
      parsed = new URL(value);
    } catch {
      throw new Error('CONTRATO_PERFIL_ATLETA_INVALIDO: link social invalido.');
    }
    if (parsed.protocol !== 'https:'
      || parsed.username
      || parsed.password
      || !SOCIAL_HOSTS[field].has(parsed.hostname.toLowerCase())
      || parsed.pathname === '/') {
      throw new Error('CONTRATO_PERFIL_ATLETA_INVALIDO: link social fora da allowlist.');
    }
    return parsed.toString();
  }
}

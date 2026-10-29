import { Injectable, inject } from '@angular/core';
import { map } from 'rxjs';
import { PrivacyRightsService } from '../../api/generated/api/privacy-rights.service';
import { OperationalCapabilitiesService } from '../../api/generated/api/operational-capabilities.service';
import type { PrivacyCase } from '../../api/generated/model/privacy-case';
import type { PrivacyRequest } from '../../api/generated/model/privacy-request';
import type { PrivacyResponseCommand } from '../../api/generated/model/privacy-response-command';
import type { PrivacyExport } from '../../api/generated/model/privacy-export';

const options = { transferCache: false };
export const PRIVACY_UUID = /^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/u;

@Injectable({ providedIn: 'root' })
export class PrivacyRightsApiAdapter {
  private readonly api = inject(PrivacyRightsService);
  private readonly capabilities = inject(OperationalCapabilitiesService);

  list(offset: number) {
    return this.api.getOwnPrivacyCases({ limit: 50, offset }, 'body', false, options)
      .pipe(map(items => items.map(item => this.validCase(item))));
  }
  view(caseId: string) {
    return this.api.getPrivacyCase({ caseId }, 'body', false, options).pipe(map(item => this.validCase(item)));
  }
  request(privacyRequest: PrivacyRequest) {
    return this.api.requestPrivacyRight({ privacyRequest }, 'body', false, options);
  }
  respond(caseId: string, privacyResponseCommand: PrivacyResponseCommand) {
    return this.api.respondPrivacyCase({ caseId, privacyResponseCommand }, 'body', false, options);
  }
  prepareExport(caseId: string) {
    return this.api.preparePrivacyExport({ caseId }, 'body', false, options).pipe(map(value => this.validExport(value)));
  }
  download(caseId: string) {
    return this.api.downloadPrivacyExport({ caseId }, 'body', false, options).pipe(map(value => this.validExport(value)));
  }
  administrator() {
    return this.capabilities.getOperationalCapabilities('body', false, options).pipe(
      map(value => Array.from(value.capabilities).includes('MANAGE_ROLES')),
    );
  }
  private validCase(value: PrivacyCase): PrivacyCase {
    if (!value || !PRIVACY_UUID.test(value.caseId) ||
      !['ACCOUNT', 'ATHLETE_WITHOUT_ACCOUNT'].includes(value.subjectKind) ||
      !['NOTICE', 'ACCESS', 'CORRECTION', 'DELETION', 'OBJECTION', 'PSEUDONYMIZATION'].includes(value.requestKind) ||
      !['PENDING', 'UNDER_REVIEW', 'RESPONDED', 'REJECTED'].includes(value.state) ||
      !Number.isFinite(Date.parse(value.requestedAt)) || !Array.isArray(value.timeline) ||
      value.timeline.some(item => !item || !['REQUESTED', 'IDENTITY_VERIFIED', 'ANALYZED', 'RESPONSE_SENT', 'EXPORT_READY', 'DISPOSITIONED', 'PSEUDONYMIZED', 'REJECTED'].includes(item.eventKind) || !Number.isFinite(Date.parse(item.occurredAt)) ||
        (item.responseText !== null && (typeof item.responseText !== 'string' || item.responseText.length > 2000)))) {
      throw new Error('Invalid private case');
    }
    return value;
  }
  private validExport(value: PrivacyExport): PrivacyExport {
    if (!value || !PRIVACY_UUID.test(value.exportId) || typeof value.complete !== 'boolean' ||
      !Array.isArray(value.blockedClasses) || !value.data || typeof value.data !== 'object' || Array.isArray(value.data) ||
      value.complete !== (value.blockedClasses.length === 0)) throw new Error('Invalid private export');
    return value;
  }
}

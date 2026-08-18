export * from './diagnostic.service';
import { DiagnosticService } from './diagnostic.service';
export * from './identity.service';
import { IdentityService } from './identity.service';
export * from './session.service';
import { SessionService } from './session.service';
export const APIS = [DiagnosticService, IdentityService, SessionService];

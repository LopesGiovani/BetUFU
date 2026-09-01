import { HttpEventType, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, defer, filter, map, throwError } from 'rxjs';
import {
  AthleteMediaService,
  UploadMyAthletePhotoRequestParams,
} from '../../../api/generated/api/athlete-media.service';
import { AthleteMediaResponse } from '../../../api/generated/model/athlete-media-response';

export type AthleteMediaUploadEvent =
  | { kind: 'progress'; progress: number }
  | { kind: 'complete'; media: AthleteMediaResponse };

@Injectable({ providedIn: 'root' })
export class AthleteMediaApiAdapter {
  private readonly service = inject(AthleteMediaService);

  fetchMine(): Observable<AthleteMediaResponse> {
    return defer(() => this.service.getMyAthletePhoto('body', false, { transferCache: false })).pipe(
      map((response) => this.adapt(response)),
    );
  }

  fetchPublicPhoto(athleteId: string): Observable<string> {
    const normalizedId = athleteId.trim();
    if (!normalizedId) {
      return throwError(() => new Error('CONTRATO_FOTO_PUBLICA_INVALIDO: identificador ausente.'));
    }
    return defer(() =>
      this.service.getAthletePublicPhoto(
        { athleteId: normalizedId },
        'body',
        false,
        { transferCache: false },
      ),
    ).pipe(
      map((blob) => {
        if (!(blob instanceof Blob) || !/^image\/(jpeg|png)$/u.test(blob.type)) {
          throw new Error('CONTRATO_FOTO_PUBLICA_INVALIDO: resposta nao e imagem processada.');
        }
        return URL.createObjectURL(blob);
      }),
    );
  }

  createPreviewUrl(file: File): string {
    return URL.createObjectURL(file);
  }

  revokeUrl(url: string): void {
    URL.revokeObjectURL(url);
  }

  uploadMine(file: File): Observable<AthleteMediaUploadEvent> {
    const request: UploadMyAthletePhotoRequestParams = { file };
    return defer(() =>
      this.service.uploadMyAthletePhoto(request, 'events', true, { transferCache: false }),
    ).pipe(
      map((event) => {
        if (event.type === HttpEventType.UploadProgress) {
          const total = event.total ?? 0;
          return {
            kind: 'progress' as const,
            progress: total > 0 ? Math.min(100, Math.round((event.loaded / total) * 100)) : 0,
          };
        }
        if (event instanceof HttpResponse) {
          return { kind: 'complete' as const, media: this.adapt(event.body) };
        }
        return null;
      }),
      filter((event): event is AthleteMediaUploadEvent => event !== null),
    );
  }

  removeMine(): Observable<AthleteMediaResponse> {
    return defer(() => this.service.removeMyAthletePhoto()).pipe(map((response) => this.adapt(response)));
  }

  private adapt(response: AthleteMediaResponse | null | undefined): AthleteMediaResponse {
    if (
      !response ||
      typeof response.athleteId !== 'string' ||
      typeof response.present !== 'boolean' ||
      !Number.isInteger(response.mediaVersion) ||
      response.mediaVersion < 0 ||
      typeof response.updatedAt !== 'string' ||
      Number.isNaN(Date.parse(response.updatedAt))
    ) {
      throw new Error('CONTRATO_FOTO_INVALIDO: resposta ausente ou malformada.');
    }
    const contentType = response.contentType ?? null;
    if (contentType !== null && contentType !== 'image/jpeg' && contentType !== 'image/png') {
      throw new Error('CONTRATO_FOTO_INVALIDO: MIME inesperado.');
    }
    if (!response.present) {
      return Object.freeze({
        athleteId: response.athleteId,
        present: false,
        contentType: null,
        byteSize: null,
        width: null,
        height: null,
        mediaVersion: response.mediaVersion,
        updatedAt: response.updatedAt,
      });
    }
    const byteSize = response.byteSize;
    const width = response.width;
    const height = response.height;
    if (
      contentType === null ||
      typeof byteSize !== 'number' ||
      !Number.isInteger(byteSize) ||
      byteSize <= 0 ||
      typeof width !== 'number' ||
      !Number.isInteger(width) ||
      width <= 0 ||
      typeof height !== 'number' ||
      !Number.isInteger(height) ||
      height <= 0
    ) {
      throw new Error('CONTRATO_FOTO_INVALIDO: metadados incompletos.');
    }
    return Object.freeze({
      athleteId: response.athleteId,
      present: true,
      contentType,
      byteSize,
      width,
      height,
      mediaVersion: response.mediaVersion,
      updatedAt: response.updatedAt,
    });
  }
}

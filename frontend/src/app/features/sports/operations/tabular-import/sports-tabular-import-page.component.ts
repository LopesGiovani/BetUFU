import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { SportsCatalogTree } from '../../../../api/generated/model/sports-catalog-tree';
import { SportsCatalogApiAdapter } from '../catalog/sports-catalog-api.adapter';
import { SportsScopeStore } from '../scope/sports-scope.store';
import { SportsTabularImportStore } from './sports-tabular-import.store';

const TEMPLATE_HEADERS = [
  'organizacao_slug',
  'modalidade_slug',
  'categoria_slug',
  'nome_atleta',
  'matricula',
  'numero_camisa',
  'posicao',
] as const;

@Component({
  selector: 'app-sports-tabular-import-page',
  standalone: true,
  templateUrl: './sports-tabular-import-page.component.html',
  styleUrl: './sports-tabular-import-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SportsTabularImportPageComponent implements OnInit {
  readonly scope = inject(SportsScopeStore);
  readonly store = inject(SportsTabularImportStore);
  private readonly catalogAdapter = inject(SportsCatalogApiAdapter);
  private readonly route = inject(ActivatedRoute, { optional: true });
  private readonly router = inject(Router, { optional: true });

  readonly tree = signal<SportsCatalogTree | null>(null);
  readonly contextLoading = signal(false);
  readonly contextError = signal<string | null>(null);
  readonly selectedEditionId = signal('');
  readonly selectedEditionName = signal('');

  async ngOnInit(): Promise<void> {
    const competitionId = this.route?.snapshot.queryParamMap.get('competitionId');
    const requestedEditionId = this.route?.snapshot.queryParamMap.get('editionId') ?? null;
    await this.scope.load(competitionId);
    if (!this.scope.hasSelection()) return;
    await this.loadContext(requestedEditionId);
  }

  async retryContext(): Promise<void> {
    await this.scope.load(this.route?.snapshot.queryParamMap.get('competitionId'));
    if (this.scope.hasSelection()) {
      await this.loadContext(this.selectedEditionId());
    }
  }

  async changeCompetition(competitionId: string): Promise<void> {
    if (!this.scope.selectCompetition(competitionId)) return;
    this.tree.set(null);
    this.selectedEditionId.set('');
    this.selectedEditionName.set('');
    await this.syncUrl(competitionId, null);
    await this.loadContext(null);
  }

  async changeEdition(editionId: string): Promise<void> {
    const edition = this.tree()?.editions.find((item) => item.editionId === editionId);
    if (!edition) return;
    this.selectedEditionId.set(edition.editionId);
    this.selectedEditionName.set(edition.name);
    await this.syncUrl(this.scope.selectedCompetitionId(), edition.editionId);
    this.store.reset();
  }

  selectFile(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    this.store.selectFile(input?.files?.[0] ?? null);
  }

  async validate(): Promise<void> {
    const editionId = this.selectedEditionId();
    if (editionId) await this.store.validate(editionId);
  }

  async commit(): Promise<void> {
    const editionId = this.selectedEditionId();
    if (editionId) await this.store.commit(editionId);
  }

  async checkStatus(): Promise<void> {
    const editionId = this.selectedEditionId();
    if (editionId) await this.store.checkStatus(editionId);
  }

  resetFile(input?: HTMLInputElement): void {
    this.store.reset();
    if (input) input.value = '';
  }

  downloadTemplate(format: 'CSV' | 'XLSX'): void {
    const xlsxTemplate = format === 'XLSX' ? this.toArrayBuffer(this.createXlsxTemplate()) : null;
    const blob = format === 'CSV'
      ? new Blob([`${TEMPLATE_HEADERS.join(',')}\r\n`], { type: 'text/csv;charset=utf-8' })
      : new Blob([xlsxTemplate!], {
        type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = format === 'CSV' ? 'betufu-template-roster.csv' : 'betufu-template-roster.xlsx';
    anchor.click();
    URL.revokeObjectURL(url);
  }

  private createXlsxTemplate(): Uint8Array {
    const escapeXml = (value: string): string => value
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&apos;');
    const cells = TEMPLATE_HEADERS.map((header, index) =>
      `<c r="${String.fromCharCode(65 + index)}1" t="inlineStr"><is><t>${escapeXml(header)}</t></is></c>`).join('');
    const entries = [
      ['[Content_Types].xml', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>'],
      ['_rels/.rels', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>'],
      ['xl/workbook.xml', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Roster" sheetId="1" r:id="rId1"/></sheets></workbook>'],
      ['xl/_rels/workbook.xml.rels', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>'],
      ['xl/worksheets/sheet1.xml', `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData><row r="1">${cells}</row></sheetData></worksheet>`],
    ].map(([name, content]) => ({ name, bytes: new TextEncoder().encode(content) }));

    const crc32 = (bytes: Uint8Array): number => {
      let crc = 0xffffffff;
      for (const byte of bytes) {
        crc ^= byte;
        for (let bit = 0; bit < 8; bit++) crc = (crc >>> 1) ^ (0xedb88320 & -(crc & 1));
      }
      return (crc ^ 0xffffffff) >>> 0;
    };
    const chunks: Uint8Array[] = [];
    const central: Uint8Array[] = [];
    let offset = 0;
    const writeU16 = (view: DataView, position: number, value: number) => view.setUint16(position, value, true);
    const writeU32 = (view: DataView, position: number, value: number) => view.setUint32(position, value, true);
    for (const entry of entries) {
      const name = new TextEncoder().encode(entry.name);
      const local = new Uint8Array(30 + name.length);
      const localView = new DataView(local.buffer);
      writeU32(localView, 0, 0x04034b50); writeU16(localView, 4, 20); writeU16(localView, 6, 0);
      writeU16(localView, 8, 0); writeU16(localView, 10, 0); writeU16(localView, 12, 0);
      writeU32(localView, 14, crc32(entry.bytes)); writeU32(localView, 18, entry.bytes.length); writeU32(localView, 22, entry.bytes.length);
      writeU16(localView, 26, name.length); writeU16(localView, 28, 0); local.set(name, 30);
      chunks.push(local, entry.bytes);
      const directory = new Uint8Array(46 + name.length);
      const directoryView = new DataView(directory.buffer);
      writeU32(directoryView, 0, 0x02014b50); writeU16(directoryView, 4, 20); writeU16(directoryView, 6, 20); writeU16(directoryView, 8, 0);
      writeU16(directoryView, 10, 0); writeU16(directoryView, 12, 0); writeU16(directoryView, 14, 0);
      writeU32(directoryView, 16, crc32(entry.bytes)); writeU32(directoryView, 20, entry.bytes.length); writeU32(directoryView, 24, entry.bytes.length);
      writeU16(directoryView, 28, name.length); writeU16(directoryView, 30, 0); writeU16(directoryView, 32, 0); writeU16(directoryView, 34, 0); writeU16(directoryView, 36, 0);
      writeU32(directoryView, 38, 0); writeU32(directoryView, 42, offset); directory.set(name, 46);
      central.push(directory);
      offset += local.length + entry.bytes.length;
    }
    const centralBytes = central.reduce((total, chunk) => total + chunk.length, 0);
    const end = new Uint8Array(22);
    const endView = new DataView(end.buffer);
    writeU32(endView, 0, 0x06054b50); writeU16(endView, 8, entries.length); writeU16(endView, 10, entries.length);
    writeU32(endView, 12, centralBytes); writeU32(endView, 16, offset); writeU16(endView, 20, 0);
    return this.concatBytes([...chunks, ...central, end]);
  }

  private concatBytes(chunks: readonly Uint8Array[]): Uint8Array {
    const result = new Uint8Array(chunks.reduce((total, chunk) => total + chunk.length, 0));
    let offset = 0;
    for (const chunk of chunks) { result.set(chunk, offset); offset += chunk.length; }
    return result;
  }

  private toArrayBuffer(bytes: Uint8Array): ArrayBuffer {
    const buffer = new ArrayBuffer(bytes.byteLength);
    new Uint8Array(buffer).set(bytes);
    return buffer;
  }

  private async loadContext(requestedEditionId: string | null): Promise<void> {
    const competitionId = this.scope.selectedCompetitionId();
    if (!competitionId) return;
    this.contextLoading.set(true);
    this.contextError.set(null);
    try {
      const tree = await firstValueFrom(this.catalogAdapter.fetchTree(competitionId));
      this.tree.set(tree);
      const edition = tree.editions.find((item) => item.editionId === requestedEditionId) ?? tree.editions[0];
      if (edition) {
        this.selectedEditionId.set(edition.editionId);
        this.selectedEditionName.set(edition.name);
      }
    } catch {
      this.tree.set(null);
      this.contextError.set('As edições desta competição não estão disponíveis para importar o elenco.');
    } finally {
      this.contextLoading.set(false);
    }
  }

  private async syncUrl(competitionId: string | null, editionId: string | null): Promise<void> {
    if (!this.router || !this.route) return;
    await this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { competitionId, editionId },
      queryParamsHandling: 'merge',
    });
  }
}

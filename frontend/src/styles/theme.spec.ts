import { describe, it, expect } from 'vitest';

declare const require: any;
declare const process: any;
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

/**
 * Funções auxiliares para cálculo estrito de contraste per WCAG 2.2
 */
function hexToRgb(hex: string): [number, number, number] {
  const clean = hex.replace('#', '').trim();
  const num = parseInt(clean, 16);
  return [(num >> 16) & 255, (num >> 8) & 255, num & 255];
}

function relativeLuminance([r, g, b]: [number, number, number]): number {
  const [rs, gs, bs] = [r, g, b].map((val) => {
    const s = val / 255;
    return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
  });
  return 0.2126 * rs + 0.7152 * gs + 0.0722 * bs;
}

function contrastRatio(hex1: string, hex2: string): number {
  const lum1 = relativeLuminance(hexToRgb(hex1));
  const lum2 = relativeLuminance(hexToRgb(hex2));
  const brightest = Math.max(lum1, lum2);
  const darkest = Math.min(lum1, lum2);
  return (brightest + 0.05) / (darkest + 0.05);
}

/**
 * Extrai o valor de uma variável SCSS ($nome: valor;) do conteúdo bruto do arquivo.
 */
function extractScssVariable(scssContent: string, varName: string): string {
  const regex = new RegExp(`\\$${varName}\\s*:\\s*([^;]+);`, 'u');
  const match = scssContent.match(regex);
  if (!match || !match[1]) {
    throw new Error(`Variável SCSS $${varName} não encontrada no arquivo.`);
  }
  return match[1].trim();
}

describe('BETUFU Design System & Theme Specs (D-01 & UI-SPEC)', () => {
  // Leitura direta dos arquivos SCSS entregues no repositório
  const basePath = typeof process !== 'undefined' && process.cwd ? process.cwd() : '';
  const tokensFilePath = path.resolve(basePath, 'src/styles/_tokens.scss');
  const stylesFilePath = path.resolve(basePath, 'src/styles.scss');
  const fontsFilePath = path.resolve(basePath, 'src/styles/_fonts.scss');
  const materialSymbolsFilePath = path.resolve(
    basePath,
    'src/assets/fonts/material-symbols-rounded.woff2',
  );
  const materialSymbolsLicensePath = path.resolve(
    basePath,
    'src/assets/fonts/material-symbols-apache-license.txt',
  );

  const tokensContent: string = fs.readFileSync(tokensFilePath, 'utf8');
  const stylesContent: string = fs.readFileSync(stylesFilePath, 'utf8');
  const fontsContent: string = fs.readFileSync(fontsFilePath, 'utf8');

  // Cores dinamicamente extraídas do _tokens.scss
  const colors = {
    canvas: extractScssVariable(tokensContent, 'color-canvas'),
    surface: extractScssVariable(tokensContent, 'color-surface'),
    surfaceRaised: extractScssVariable(tokensContent, 'color-surface-raised'),
    accent: extractScssVariable(tokensContent, 'color-accent'),
    onAccent: extractScssVariable(tokensContent, 'color-on-accent'),
    text: extractScssVariable(tokensContent, 'color-text'),
    textMuted: extractScssVariable(tokensContent, 'color-text-muted'),
    border: extractScssVariable(tokensContent, 'color-border'),
    focus: extractScssVariable(tokensContent, 'color-focus'),
    available: extractScssVariable(tokensContent, 'color-available'),
    degraded: extractScssVariable(tokensContent, 'color-degraded'),
    unavailable: extractScssVariable(tokensContent, 'color-unavailable'),
  };

  const minTouchTarget = extractScssVariable(tokensContent, 'min-touch-target');
  const focusOutlineWidth = extractScssVariable(tokensContent, 'focus-outline-width');
  const focusOutlineOffset = extractScssVariable(tokensContent, 'focus-outline-offset');

  it('D-01: Todos os tokens obrigatórios de cor e superfície são extraídos diretamente de _tokens.scss', () => {
    expect(colors.canvas).toBe('#0B1220');
    expect(colors.surface).toBe('#142033');
    expect(colors.surfaceRaised).toBe('#1C2C42');
    expect(colors.accent).toBe('#FF8A3D');
    expect(colors.onAccent).toBe('#0B1220');
    expect(colors.text).toBe('#F8FAFC');
    expect(colors.textMuted).toBe('#C5D1E0');
    expect(colors.border).toBe('#52657D');
    expect(colors.focus).toBe('#FFC08A');
    expect(colors.available).toBe('#56D19C');
    expect(colors.degraded).toBe('#F6C453');
    expect(colors.unavailable).toBe('#FF6B6B');
  });

  it('WCAG 2.2 AA: Texto normal extraído de _tokens.scss mantém razão de contraste mínima de 4.5:1 contra superfícies', () => {
    const contrastTextOnCanvas = contrastRatio(colors.text, colors.canvas);
    const contrastTextOnSurface = contrastRatio(colors.text, colors.surface);
    const contrastTextOnRaised = contrastRatio(colors.text, colors.surfaceRaised);

    // #F8FAFC sobre #0B1220 (~17.5:1), sobre #142033 (~15.63:1), sobre #1C2C42 (~13.49:1)
    expect(contrastTextOnCanvas).toBeGreaterThanOrEqual(4.5);
    expect(contrastTextOnSurface).toBeGreaterThanOrEqual(4.5);
    expect(contrastTextOnRaised).toBeGreaterThanOrEqual(4.5);
  });

  it('WCAG 2.2 AA: Botão primário com texto on-accent sobre accent de _tokens.scss atende a no mínimo 4.5:1', () => {
    const ratio = contrastRatio(colors.onAccent, colors.accent);
    // #0B1220 sobre #FF8A3D produz ~7.98:1
    expect(ratio).toBeGreaterThanOrEqual(4.5);
  });

  it('Acessibilidade: Foco visível possui espessura de 3px, offset de 3px e cor aprovada nos tokens e em styles.scss', () => {
    expect(focusOutlineWidth).toBe('3px');
    expect(focusOutlineOffset).toBe('3px');
    expect(colors.focus).toBe('#FFC08A');

    // Valida que styles.scss aplica as regras de foco nos seletores globais
    expect(stylesContent).toContain(':focus-visible');
    expect(stylesContent).toContain('tokens.$focus-outline-width solid tokens.$color-focus');
    expect(stylesContent).toContain('tokens.$focus-outline-offset');
  });

  it('Acessibilidade: Alvos de toque respeitam o piso mínimo de 44px em tokens e em styles.scss', () => {
    expect(minTouchTarget).toBe('44px');

    // Valida que styles.scss define min-height e min-width de 44px
    expect(stylesContent).toContain('min-height: tokens.$min-touch-target');
    expect(stylesContent).toContain('min-width: tokens.$min-touch-target');
  });

  it('Acessibilidade: Suporte obrigatório a prefers-reduced-motion está presente em styles.scss', () => {
    expect(stylesContent).toContain('@media (prefers-reduced-motion: reduce)');
    expect(stylesContent).toContain('animation-duration: 0.01ms !important');
    expect(stylesContent).toContain('transition-duration: 0.01ms !important');
  });

  it('Contraste de Estados: available, degraded e unavailable atendem ao piso mínimo de 3.0:1 contra canvas', () => {
    const availableContrast = contrastRatio(colors.available, colors.canvas);
    const degradedContrast = contrastRatio(colors.degraded, colors.canvas);
    const unavailableContrast = contrastRatio(colors.unavailable, colors.canvas);

    expect(availableContrast).toBeGreaterThanOrEqual(3.0);
    expect(degradedContrast).toBeGreaterThanOrEqual(3.0);
    expect(unavailableContrast).toBeGreaterThanOrEqual(3.0);
  });

  it('Segurança e Isolamento: Nenhum tema claro ou dependência de CDN remota em SCSS', () => {
    // Proibição de light theme overrides
    expect(stylesContent).not.toContain('prefers-color-scheme: light');
    expect(tokensContent).not.toContain('prefers-color-scheme: light');

    // Proibição de CDNs de fontes em todos os arquivos de estilos
    for (const content of [stylesContent, tokensContent, fontsContent]) {
      expect(content).not.toContain('fonts.googleapis.com');
      expect(content).not.toContain('fonts.gstatic.com');
      expect(content).not.toContain('http://');
      expect(content).not.toContain('https://');
    }
  });

  it('Ícones: Material Symbols Rounded usa o subconjunto local e a licença aprovados', () => {
    const font = fs.readFileSync(materialSymbolsFilePath);
    const license = fs.readFileSync(materialSymbolsLicensePath);

    expect(font.subarray(0, 4).toString('ascii')).toBe('wOF2');
    expect(crypto.createHash('sha256').update(font).digest('hex')).toBe(
      'b5d6a3abcecb8dbc02446840babc93ff50f5d0442b730f057b9f5fd767510aad',
    );
    expect(crypto.createHash('sha256').update(license).digest('hex')).toBe(
      '58d1e17ffe5109a7ae296caafcadfdbe6a7d176f0bc4ab01e12a689b0499d8bd',
    );
    expect(license.toString('utf8')).toContain('Apache License');
    expect(fontsContent).toContain("font-family: 'Material Symbols Rounded'");
    expect(fontsContent).toContain("url('/assets/fonts/material-symbols-rounded.woff2')");
    expect(fontsContent).toContain('font-display: block');
    expect(fontsContent).toContain('.material-symbols-rounded');
    expect(fontsContent).toContain("font-feature-settings: 'liga'");
  });
});

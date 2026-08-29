import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { GrainientBackgroundComponent } from './grainient-background.component';

function createGlMock(): WebGL2RenderingContext {
  const uniform = {} as WebGLUniformLocation;
  return {
    VERTEX_SHADER: 0x8b31,
    FRAGMENT_SHADER: 0x8b30,
    COMPILE_STATUS: 0x8b81,
    LINK_STATUS: 0x8b82,
    TRIANGLES: 0x0004,
    createShader: vi.fn().mockReturnValue({}),
    shaderSource: vi.fn(),
    compileShader: vi.fn(),
    getShaderParameter: vi.fn().mockReturnValue(true),
    deleteShader: vi.fn(),
    createProgram: vi.fn().mockReturnValue({}),
    createVertexArray: vi.fn().mockReturnValue({}),
    attachShader: vi.fn(),
    linkProgram: vi.fn(),
    getProgramParameter: vi.fn().mockReturnValue(true),
    deleteProgram: vi.fn(),
    deleteVertexArray: vi.fn(),
    getUniformLocation: vi.fn().mockReturnValue(uniform),
    viewport: vi.fn(),
    useProgram: vi.fn(),
    bindVertexArray: vi.fn(),
    uniform2f: vi.fn(),
    uniform1f: vi.fn(),
    uniform3f: vi.fn(),
    drawArrays: vi.fn(),
  } as unknown as WebGL2RenderingContext;
}

describe('GrainientBackgroundComponent', () => {
  const resizeDisconnect = vi.fn();
  const intersectionDisconnect = vi.fn();
  let gl: WebGL2RenderingContext;
  let scheduledFrame: FrameRequestCallback | undefined;

  beforeEach(() => {
    resizeDisconnect.mockClear();
    intersectionDisconnect.mockClear();
    scheduledFrame = undefined;
    gl = createGlMock();
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(gl);
    vi.stubGlobal('ResizeObserver', class {
      observe(): void {}
      disconnect(): void { resizeDisconnect(); }
    });
    vi.stubGlobal('IntersectionObserver', class {
      constructor(callback: IntersectionObserverCallback) {
        callback([{ isIntersecting: true } as IntersectionObserverEntry], this as unknown as IntersectionObserver);
      }
      observe(): void {}
      disconnect(): void { intersectionDisconnect(); }
    });
    vi.stubGlobal('requestAnimationFrame', vi.fn((callback: FrameRequestCallback) => {
      scheduledFrame = callback;
      return 7;
    }));
    vi.stubGlobal('cancelAnimationFrame', vi.fn());
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      value: vi.fn().mockReturnValue({
        matches: false,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      }),
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('libera observers, RAF, programa e VAO no destroy', async () => {
    await TestBed.configureTestingModule({ imports: [GrainientBackgroundComponent] }).compileComponents();
    const fixture = TestBed.createComponent(GrainientBackgroundComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.fallback()).toBe(false);

    fixture.destroy();

    expect(resizeDisconnect).toHaveBeenCalledTimes(1);
    expect(intersectionDisconnect).toHaveBeenCalledTimes(1);
    expect(cancelAnimationFrame).toHaveBeenCalled();
    expect(gl.deleteProgram).toHaveBeenCalledTimes(1);
    expect(gl.deleteVertexArray).toHaveBeenCalledTimes(1);
  });

  it('previne perda do contexto, ativa fallback e reconstrói após restauração', async () => {
    await TestBed.configureTestingModule({ imports: [GrainientBackgroundComponent] }).compileComponents();
    const fixture = TestBed.createComponent(GrainientBackgroundComponent);
    fixture.detectChanges();
    const canvas = fixture.nativeElement.querySelector('canvas') as HTMLCanvasElement;

    const lost = new Event('webglcontextlost', { cancelable: true });
    canvas.dispatchEvent(lost);
    fixture.detectChanges();
    expect(lost.defaultPrevented).toBe(true);
    expect(fixture.componentInstance.fallback()).toBe(true);

    canvas.dispatchEvent(new Event('webglcontextrestored'));
    fixture.detectChanges();
    expect(fixture.componentInstance.fallback()).toBe(false);
    expect(gl.createProgram).toHaveBeenCalledTimes(2);
  });

  it('delega resize ao observer sem medir layout em cada frame', async () => {
    const widthRead = vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockReturnValue(800);
    const heightRead = vi.spyOn(HTMLElement.prototype, 'clientHeight', 'get').mockReturnValue(450);
    await TestBed.configureTestingModule({ imports: [GrainientBackgroundComponent] }).compileComponents();
    const fixture = TestBed.createComponent(GrainientBackgroundComponent);
    fixture.detectChanges();
    widthRead.mockClear();
    heightRead.mockClear();

    scheduledFrame?.(1_000);

    expect(widthRead).not.toHaveBeenCalled();
    expect(heightRead).not.toHaveBeenCalled();
    expect(gl.drawArrays).toHaveBeenCalled();
    fixture.destroy();
  });

  it('limita a área do buffer em telas 4K', async () => {
    vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockReturnValue(3_840);
    vi.spyOn(HTMLElement.prototype, 'clientHeight', 'get').mockReturnValue(2_160);
    await TestBed.configureTestingModule({ imports: [GrainientBackgroundComponent] }).compileComponents();
    const fixture = TestBed.createComponent(GrainientBackgroundComponent);
    fixture.detectChanges();

    const canvas = fixture.nativeElement.querySelector('canvas') as HTMLCanvasElement;
    expect(canvas.width * canvas.height).toBeLessThanOrEqual(900_000);
    fixture.destroy();
  });
});

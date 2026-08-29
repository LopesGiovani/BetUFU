import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  ViewChild,
  effect,
  input,
  signal,
} from '@angular/core';

const MAX_PIXEL_AREA = 900_000;
const FRAME_INTERVAL = 1_000 / 30;

const VERTEX_SHADER = `#version 300 es
const vec2 POSITIONS[3] = vec2[3](vec2(-1.0, -1.0), vec2(3.0, -1.0), vec2(-1.0, 3.0));
void main() { gl_Position = vec4(POSITIONS[gl_VertexID], 0.0, 1.0); }
`;

const FRAGMENT_SHADER = `#version 300 es
precision highp float;
uniform vec2 uResolution;
uniform float uTime;
uniform float uTimeSpeed;
uniform float uColorBalance;
uniform float uWarpStrength;
uniform float uWarpFrequency;
uniform float uWarpSpeed;
uniform float uWarpAmplitude;
uniform float uBlendAngle;
uniform float uBlendSoftness;
uniform float uRotationAmount;
uniform float uNoiseScale;
uniform float uGrainAmount;
uniform float uGrainScale;
uniform float uContrast;
uniform float uGamma;
uniform float uSaturation;
uniform vec2 uCenterOffset;
uniform float uZoom;
uniform vec3 uColor1;
uniform vec3 uColor2;
uniform vec3 uColor3;
out vec4 outColor;
#define S(a,b,t) smoothstep(a,b,t)
mat2 Rot(float a){float s=sin(a),c=cos(a);return mat2(c,-s,s,c);}
vec2 hash(vec2 p){p=vec2(dot(p,vec2(2127.1,81.17)),dot(p,vec2(1269.5,283.37)));return fract(sin(p)*43758.5453);}
float noise(vec2 p){vec2 i=floor(p),f=fract(p),u=f*f*(3.0-2.0*f);float n=mix(mix(dot(-1.0+2.0*hash(i),f),dot(-1.0+2.0*hash(i+vec2(1.0,0.0)),f-vec2(1.0,0.0)),u.x),mix(dot(-1.0+2.0*hash(i+vec2(0.0,1.0)),f-vec2(0.0,1.0)),dot(-1.0+2.0*hash(i+vec2(1.0)),f-vec2(1.0)),u.x),u.y);return .5+.5*n;}
void main(){
  float t=uTime*uTimeSpeed;
  vec2 uv=gl_FragCoord.xy/uResolution.xy;
  float ratio=uResolution.x/uResolution.y;
  vec2 tuv=uv-.5+uCenterOffset;
  tuv/=max(uZoom,.001);
  float degree=noise(vec2(t*.1,tuv.x*tuv.y)*uNoiseScale);
  tuv.y/=ratio;
  tuv*=Rot(radians((degree-.5)*uRotationAmount+180.0));
  tuv.y*=ratio;
  float amplitude=uWarpAmplitude/max(uWarpStrength,.001);
  float warpTime=t*uWarpSpeed;
  tuv.x+=sin(tuv.y*uWarpFrequency+warpTime)/amplitude;
  tuv.y+=sin(tuv.x*(uWarpFrequency*1.5)+warpTime)/(amplitude*.5);
  float b=uColorBalance,s=max(uBlendSoftness,0.0);
  float blendX=(tuv*Rot(radians(uBlendAngle))).x;
  float edge0=-.3-b-s,edge1=.2-b+s;
  vec3 layer1=mix(uColor3,uColor2,S(edge0,edge1,blendX));
  vec3 layer2=mix(uColor2,uColor1,S(edge0,edge1,blendX));
  float vertical=1.0-S(-.3-b-s,.5-b+s,tuv.y);
  vec3 col=mix(layer1,layer2,vertical);
  float grain=fract(sin(dot(uv*max(uGrainScale,.001),vec2(12.9898,78.233)))*43758.5453);
  col+=(grain-.5)*uGrainAmount;
  col=(col-.5)*uContrast+.5;
  float luma=dot(col,vec3(.2126,.7152,.0722));
  col=mix(vec3(luma),col,uSaturation);
  col=pow(max(col,0.0),vec3(1.0/max(uGamma,.001)));
  outColor=vec4(clamp(col,0.0,1.0),1.0);
}
`;

interface GrainientResources {
  program: WebGLProgram;
  vao: WebGLVertexArrayObject;
  resolution: WebGLUniformLocation;
  time: WebGLUniformLocation;
}

@Component({
  selector: 'app-grainient-background',
  standalone: true,
  template: `<canvas #canvas aria-hidden="true" [class.is-hidden]="fallback()"></canvas>`,
  styles: `
    :host {
      position: absolute;
      inset: 0;
      display: block;
      overflow: hidden;
      background:
        radial-gradient(circle at 14% 18%, rgba(255, 98, 107, .38), transparent 36%),
        radial-gradient(circle at 78% 62%, rgba(116, 29, 66, .62), transparent 48%),
        linear-gradient(135deg, #09080d, #21131d 52%, #120b12);
      pointer-events: none;
    }
    canvas { display: block; width: 100%; height: 100%; opacity: .96; transition: opacity 180ms ease; }
    canvas.is-hidden { opacity: 0; }
    @media (prefers-reduced-motion: reduce) { canvas { transition: none; } }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GrainientBackgroundComponent implements AfterViewInit, OnDestroy {
  readonly paused = input(false);
  readonly fallback = signal(true);

  @ViewChild('canvas', { static: true })
  private canvasRef?: ElementRef<HTMLCanvasElement>;

  private gl?: WebGL2RenderingContext;
  private resources?: GrainientResources;
  private resizeObserver?: ResizeObserver;
  private intersectionObserver?: IntersectionObserver;
  private mediaQuery?: MediaQueryList;
  private frameId?: number;
  private visible = true;
  private contextLost = false;
  private reducedMotion = false;
  private destroyed = false;
  private initialized = false;
  private startedAt = 0;
  private lastFrameAt = 0;

  private readonly pauseEffect = effect(() => {
    this.paused();
    if (this.initialized) {
      this.synchronizeLoop();
    }
  });

  ngAfterViewInit(): void {
    const canvas = this.canvasRef?.nativeElement;
    if (!canvas) {
      return;
    }

    this.mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
    this.reducedMotion = this.mediaQuery.matches;
    this.mediaQuery.addEventListener('change', this.onMotionPreferenceChange);
    document.addEventListener('visibilitychange', this.onVisibilityChange);
    canvas.addEventListener('webglcontextlost', this.onContextLost);
    canvas.addEventListener('webglcontextrestored', this.onContextRestored);

    this.resizeObserver = new ResizeObserver(() => {
      this.resize();
      this.draw(this.reducedMotion ? 0 : performance.now());
    });
    this.resizeObserver.observe(canvas.parentElement ?? canvas);

    if (typeof IntersectionObserver !== 'undefined') {
      this.intersectionObserver = new IntersectionObserver(([entry]) => {
        this.visible = entry?.isIntersecting ?? false;
        this.synchronizeLoop();
      });
      this.intersectionObserver.observe(canvas);
    }

    this.initialized = true;
    this.startedAt = performance.now();
    this.createContextAndResources();
    this.resize();
    this.draw(this.reducedMotion ? 0 : this.startedAt);
    this.synchronizeLoop();
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    this.stopLoop();
    this.resizeObserver?.disconnect();
    this.intersectionObserver?.disconnect();
    this.mediaQuery?.removeEventListener('change', this.onMotionPreferenceChange);
    document.removeEventListener('visibilitychange', this.onVisibilityChange);
    const canvas = this.canvasRef?.nativeElement;
    canvas?.removeEventListener('webglcontextlost', this.onContextLost);
    canvas?.removeEventListener('webglcontextrestored', this.onContextRestored);
    this.releaseResources();
    this.pauseEffect.destroy();
  }

  private createContextAndResources(): void {
    const canvas = this.canvasRef?.nativeElement;
    if (!canvas) {
      return;
    }

    try {
      this.gl = canvas.getContext('webgl2', {
        alpha: false,
        antialias: false,
        powerPreference: 'low-power',
      }) ?? undefined;
      if (!this.gl) {
        this.fallback.set(true);
        return;
      }
      this.resources = this.buildResources(this.gl);
      this.fallback.set(!this.resources);
    } catch {
      this.resources = undefined;
      this.fallback.set(true);
    }
  }

  private buildResources(gl: WebGL2RenderingContext): GrainientResources | undefined {
    const vertex = this.compileShader(gl, gl.VERTEX_SHADER, VERTEX_SHADER);
    const fragment = this.compileShader(gl, gl.FRAGMENT_SHADER, FRAGMENT_SHADER);
    if (!vertex || !fragment) {
      return undefined;
    }

    const program = gl.createProgram();
    const vao = gl.createVertexArray();
    if (!program || !vao) {
      gl.deleteShader(vertex);
      gl.deleteShader(fragment);
      return undefined;
    }

    gl.attachShader(program, vertex);
    gl.attachShader(program, fragment);
    gl.linkProgram(program);
    gl.deleteShader(vertex);
    gl.deleteShader(fragment);
    if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
      gl.deleteProgram(program);
      gl.deleteVertexArray(vao);
      return undefined;
    }

    const resolution = gl.getUniformLocation(program, 'uResolution');
    const time = gl.getUniformLocation(program, 'uTime');
    if (!resolution || !time) {
      gl.deleteProgram(program);
      gl.deleteVertexArray(vao);
      return undefined;
    }
    gl.useProgram(program);
    this.setUniform1f(gl, program, 'uTimeSpeed', .12);
    this.setUniform1f(gl, program, 'uColorBalance', -.04);
    this.setUniform1f(gl, program, 'uWarpStrength', .72);
    this.setUniform1f(gl, program, 'uWarpFrequency', 3.4);
    this.setUniform1f(gl, program, 'uWarpSpeed', .42);
    this.setUniform1f(gl, program, 'uWarpAmplitude', 78);
    this.setUniform1f(gl, program, 'uBlendAngle', -18);
    this.setUniform1f(gl, program, 'uBlendSoftness', .16);
    this.setUniform1f(gl, program, 'uRotationAmount', 150);
    this.setUniform1f(gl, program, 'uNoiseScale', 1.45);
    this.setUniform1f(gl, program, 'uGrainAmount', .035);
    this.setUniform1f(gl, program, 'uGrainScale', 1.8);
    this.setUniform1f(gl, program, 'uContrast', 1.24);
    this.setUniform1f(gl, program, 'uGamma', 1.04);
    this.setUniform1f(gl, program, 'uSaturation', .92);
    gl.uniform2f(gl.getUniformLocation(program, 'uCenterOffset'), -.06, .08);
    this.setUniform1f(gl, program, 'uZoom', .82);
    gl.uniform3f(gl.getUniformLocation(program, 'uColor1'), 1, .384, .42);
    gl.uniform3f(gl.getUniformLocation(program, 'uColor2'), .455, .114, .259);
    gl.uniform3f(gl.getUniformLocation(program, 'uColor3'), .035, .031, .051);
    return { program, vao, resolution, time };
  }

  private setUniform1f(
    gl: WebGL2RenderingContext,
    program: WebGLProgram,
    name: string,
    value: number,
  ): void {
    gl.uniform1f(gl.getUniformLocation(program, name), value);
  }

  private compileShader(
    gl: WebGL2RenderingContext,
    type: number,
    source: string,
  ): WebGLShader | undefined {
    const shader = gl.createShader(type);
    if (!shader) {
      return undefined;
    }
    gl.shaderSource(shader, source);
    gl.compileShader(shader);
    if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
      gl.deleteShader(shader);
      return undefined;
    }
    return shader;
  }

  private resize(): void {
    const canvas = this.canvasRef?.nativeElement;
    if (!canvas) {
      return;
    }
    const width = Math.max(1, canvas.parentElement?.clientWidth ?? canvas.clientWidth ?? 1);
    const height = Math.max(1, canvas.parentElement?.clientHeight ?? canvas.clientHeight ?? 1);
    const deviceRatio = Math.max(1, window.devicePixelRatio || 1);
    const areaRatio = Math.sqrt(MAX_PIXEL_AREA / Math.max(1, width * height));
    // Cap the drawing buffer by area so 4K displays cannot exceed the GPU budget.
    const ratio = Math.max(0.25, Math.min(deviceRatio, areaRatio));
    const pixelWidth = Math.max(1, Math.floor(width * ratio));
    const pixelHeight = Math.max(1, Math.floor(height * ratio));
    if (canvas.width !== pixelWidth || canvas.height !== pixelHeight) {
      canvas.width = pixelWidth;
      canvas.height = pixelHeight;
      this.gl?.viewport(0, 0, pixelWidth, pixelHeight);
    }
  }

  private draw(now: number): void {
    const canvas = this.canvasRef?.nativeElement;
    const gl = this.gl;
    const resources = this.resources;
    if (!canvas || !gl || !resources || this.contextLost || this.destroyed) {
      return;
    }
    gl.useProgram(resources.program);
    gl.bindVertexArray(resources.vao);
    gl.uniform2f(resources.resolution, canvas.width, canvas.height);
    gl.uniform1f(resources.time, this.reducedMotion ? 0 : (now - this.startedAt) / 1_000);
    gl.drawArrays(gl.TRIANGLES, 0, 3);
  }

  private readonly renderFrame = (now: number): void => {
    this.frameId = undefined;
    if (!this.shouldAnimate()) {
      return;
    }
    if (now - this.lastFrameAt >= FRAME_INTERVAL) {
      this.lastFrameAt = now - ((now - this.lastFrameAt) % FRAME_INTERVAL);
      this.draw(now);
    }
    this.frameId = requestAnimationFrame(this.renderFrame);
  };

  private shouldAnimate(): boolean {
    return this.initialized
      && !this.destroyed
      && !this.paused()
      && !this.reducedMotion
      && this.visible
      && !document.hidden
      && !this.contextLost
      && Boolean(this.resources);
  }

  private synchronizeLoop(): void {
    this.stopLoop();
    if (this.reducedMotion && this.resources && !document.hidden && !this.contextLost) {
      this.resize();
      this.draw(0);
      return;
    }
    if (this.shouldAnimate()) {
      this.frameId = requestAnimationFrame(this.renderFrame);
    }
  }

  private stopLoop(): void {
    if (this.frameId !== undefined) {
      cancelAnimationFrame(this.frameId);
      this.frameId = undefined;
    }
  }

  private releaseResources(): void {
    if (this.gl && this.resources && !this.contextLost) {
      this.gl.deleteProgram(this.resources.program);
      this.gl.deleteVertexArray(this.resources.vao);
    }
    this.resources = undefined;
  }

  private readonly onVisibilityChange = (): void => this.synchronizeLoop();

  private readonly onMotionPreferenceChange = (event: MediaQueryListEvent): void => {
    this.reducedMotion = event.matches;
    this.synchronizeLoop();
  };

  private readonly onContextLost = (event: Event): void => {
    event.preventDefault();
    this.contextLost = true;
    this.stopLoop();
    this.resources = undefined;
    this.fallback.set(true);
  };

  private readonly onContextRestored = (): void => {
    if (this.destroyed) {
      return;
    }
    this.contextLost = false;
    this.createContextAndResources();
    this.resize();
    this.draw(this.reducedMotion ? 0 : performance.now());
    this.synchronizeLoop();
  };
}

import { HttpErrorResponse, HttpXsrfTokenExtractor } from '@angular/common/http';
import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { Subject, firstValueFrom, takeUntil } from 'rxjs';
import { ChatService } from '../../api/generated/api/chat.service';
import { ProfileService } from '../../api/generated/api/profile.service';
import type { ChatMessage } from '../../api/generated/model/chat-message';
import type { ChatReadResponse } from '../../api/generated/model/chat-read-response';
import { SessionStore } from '../../core/session/session.store';

const UUID = /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/u;

interface ReadState {
  messages: readonly ChatMessage[];
  status: 'idle' | 'loading' | 'ready' | 'error';
  error: string | null;
  connection: 'idle' | 'connecting' | 'live' | 'reconnecting' | 'unavailable';
  historyCursor: string | null;
  historyLoading: boolean;
  resynchronizing: boolean;
  ownId: string | null;
  revision: number;
  mode: 'initial' | 'older' | 'sync';
}

const EMPTY: ReadState = {
  messages: [],
  status: 'idle',
  error: null,
  connection: 'idle',
  historyCursor: null,
  historyLoading: false,
  resynchronizing: false,
  ownId: null,
  revision: 0,
  mode: 'initial',
};

@Injectable()
export class ChatReadStore {
  private readonly api = inject(ChatService);
  private readonly profiles = inject(ProfileService);
  private readonly csrf = inject(HttpXsrfTokenExtractor);
  private readonly session = inject(SessionStore);
  private readonly state = signal<ReadState>(EMPTY);
  private readonly cancel = new Subject<void>();
  private generation = 0;
  private target: { eventId: string | null; authenticated: boolean } | null = null;
  private socket: WebSocket | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private attempts = 0;
  private syncAgain = false;

  readonly messages = computed(() => this.state().messages);
  readonly status = computed(() => this.state().status);
  readonly error = computed(() => this.state().error);
  readonly historyCursor = computed(() => this.state().historyCursor);
  readonly historyLoading = computed(() => this.state().historyLoading);
  readonly isResynchronizing = computed(() => this.state().resynchronizing);
  readonly revision = computed(() => this.state().revision);
  readonly mode = computed(() => this.state().mode);
  readonly ownId = computed(() => this.state().ownId);
  readonly connectionLabel = computed(() => {
    switch (this.state().connection) {
      case 'live':
        return 'Ao vivo';
      case 'connecting':
        return 'Conectando';
      case 'reconnecting':
        return 'Reconectando';
      case 'unavailable':
        return 'Atualizacao indisponivel';
      default:
        return 'Somente leitura';
    }
  });

  constructor() {
    window.addEventListener('offline', this.offline);
    window.addEventListener('online', this.online);
    inject(DestroyRef).onDestroy(() => {
      this.close();
      this.cancel.complete();
      window.removeEventListener('offline', this.offline);
      window.removeEventListener('online', this.online);
    });
  }

  open(eventId: string | null, authenticated: boolean): void {
    if (this.target?.eventId === eventId && this.target.authenticated === authenticated) return;
    this.close();
    if (eventId !== null && !UUID.test(eventId)) {
      this.state.set({ ...EMPTY, status: 'error', error: 'Evento indisponivel.' });
      return;
    }
    if (eventId === null && !authenticated) return;
    this.target = { eventId, authenticated };
    const generation = this.generation;
    this.state.set({ ...EMPTY, status: 'loading' });
    if (authenticated) void this.identify(generation);
    void this.initialize(generation);
  }

  close(): void {
    this.generation++;
    this.target = null;
    this.cancel.next();
    this.disconnect();
    this.syncAgain = false;
    this.attempts = 0;
    this.state.set(EMPTY);
  }

  retry(): void {
    const target = this.target;
    if (target) {
      this.close();
      this.open(target.eventId, target.authenticated);
    }
  }

  refresh(): void {
    if (this.target) void this.resync(this.generation);
  }

  async older(): Promise<void> {
    const cursor = this.historyCursor();
    if (!cursor || this.historyLoading() || this.isResynchronizing()) return;
    const generation = this.generation;
    this.state.update((s) => ({ ...s, historyLoading: true, error: null }));
    try {
      const page = await this.read(cursor);
      if (generation !== this.generation) return;
      const present = new Set(this.messages().map((message) => message.messageId));
      this.state.update((s) => ({
        ...s,
        messages: [...page.messages]
          .reverse()
          .filter((m) => !present.has(m.messageId))
          .concat(s.messages),
        historyCursor: page.historyCursor ?? null,
        mode: 'older',
        revision: s.revision + 1,
      }));
    } catch (error) {
      this.failed(error, generation, false);
    } finally {
      if (generation === this.generation) {
        this.state.update((s) => ({ ...s, historyLoading: false }));
        if (this.syncAgain) void this.resync(generation);
      }
    }
  }

  private async identify(generation: number): Promise<void> {
    try {
      const profile = await firstValueFrom(
        this.profiles.getMyProfile().pipe(takeUntil(this.cancel)),
      );
      if (generation === this.generation && UUID.test(profile.identityId))
        this.state.update((s) => ({ ...s, ownId: profile.identityId }));
    } catch {
      /* A missing profile never grants permissions or guesses the current author. */
    }
  }

  private async initialize(generation: number): Promise<void> {
    try {
      const page = await this.read(null);
      if (generation !== this.generation) return;
      this.state.update((s) => ({
        ...s,
        status: 'ready',
        messages: [...page.messages].reverse(),
        historyCursor: page.historyCursor ?? null,
        revision: s.revision + 1,
        mode: 'initial',
      }));
      this.connect(generation);
    } catch (error) {
      this.failed(error, generation, true);
    }
  }

  private async read(historyCursor: string | null): Promise<ChatReadResponse> {
    const target = this.target;
    if (!target) throw new Error('READ_CANCELLED');
    const options = { transferCache: false };
    const request =
      target.eventId === null
        ? this.api.getGlobalChat(
            { historyCursor: historyCursor ?? undefined, limit: 50 },
            'body',
            false,
            options,
          )
        : this.api.getEventChat(
            { eventId: target.eventId, historyCursor: historyCursor ?? undefined, limit: 50 },
            'body',
            false,
            options,
          );
    const page = await firstValueFrom(request.pipe(takeUntil(this.cancel)));
    if (
      !page ||
      page.kind !== (target.eventId === null ? 'GLOBAL' : 'EVENT') ||
      (page.eventId ?? null) !== target.eventId ||
      !Array.isArray(page.messages) ||
      page.messages.length > 100 ||
      !Number.isSafeInteger(page.changeCursor) ||
      page.changeCursor < 0
    )
      throw new Error('INVALID_CHAT_READ');
    // Whitelist presentation fields; removed facts never retain a text/author in memory.
    const messages = page.messages.map((m): ChatMessage => {
      if (
        !UUID.test(m.messageId) ||
        !Number.isFinite(Date.parse(m.postedAt)) ||
        typeof m.removed !== 'boolean' ||
        (!m.removed &&
          (typeof m.text !== 'string' ||
            typeof m.nickname !== 'string' ||
            !UUID.test(m.participantId ?? '')))
      )
        throw new Error('INVALID_CHAT_MESSAGE');
      return {
        messageId: m.messageId,
        postedAt: m.postedAt,
        removed: m.removed,
        changeCursor: m.changeCursor,
        participantId: m.removed ? null : m.participantId,
        nickname: m.removed ? null : m.nickname,
        text: m.removed ? null : m.text,
      };
    });
    return {
      state: page.state,
      kind: page.kind,
      eventId: page.eventId,
      messages,
      historyCursor: page.historyCursor,
      changeCursor: page.changeCursor,
      deadline: page.deadline,
    };
  }

  private async resync(generation: number): Promise<void> {
    if (generation !== this.generation) return;
    if (this.isResynchronizing() || this.historyLoading()) {
      this.syncAgain = true;
      return;
    }
    this.state.update((s) => ({ ...s, resynchronizing: true }));
    try {
      do {
        this.syncAgain = false;
        const oldest = this.messages()[0]?.messageId;
        const budget = Math.ceil(this.messages().length / 50) + 1;
        let page = await this.read(null);
        const messages = [...page.messages];
        for (
          let n = 1;
          oldest &&
          !messages.some((m) => m.messageId === oldest) &&
          page.historyCursor &&
          n < budget;
          n++
        ) {
          page = await this.read(page.historyCursor);
          messages.push(...page.messages);
        }
        if (generation !== this.generation) return;
        if (oldest && page.historyCursor && !messages.some((m) => m.messageId === oldest))
          throw new Error('CHAT_HISTORY_WINDOW_CHANGED');
        this.state.update((s) => ({
          ...s,
          messages: messages.reverse(),
          historyCursor: page.historyCursor ?? null,
          status: 'ready',
          error: null,
          mode: 'sync',
          revision: s.revision + 1,
        }));
      } while (this.syncAgain && generation === this.generation);
    } catch (error) {
      this.failed(error, generation, true);
    } finally {
      if (generation === this.generation)
        this.state.update((s) => ({ ...s, resynchronizing: false }));
    }
  }

  private connect(generation: number): void {
    if (generation !== this.generation || !this.target) return;
    if (!navigator.onLine) {
      this.state.update((s) => ({ ...s, connection: 'reconnecting' }));
      return;
    }
    const token = this.csrf.getToken();
    if (!token) {
      this.state.update((s) => ({ ...s, connection: 'unavailable' }));
      return;
    }
    this.disconnect();
    this.state.update((s) => ({ ...s, connection: 'connecting' }));
    const url = new URL('/api/v1/chat/push', window.location.href);
    url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
    const socket = new WebSocket(url);
    this.socket = socket;
    let buffer = '';
    socket.onopen = () => socket.send(`CONNECT\naccept-version:1.2\nX-XSRF-TOKEN:${token}\n\n\0`);
    socket.onmessage = (event: MessageEvent<string>) => {
      if (generation !== this.generation || this.socket !== socket) return;
      buffer += event.data;
      if (buffer.length > 32768) {
        socket.close(1008);
        return;
      }
      let end: number;
      while ((end = buffer.indexOf('\0')) !== -1) {
        const frame = buffer.slice(0, end).replace(/^\s+/u, '');
        buffer = buffer.slice(end + 1);
        if (frame.startsWith('CONNECTED\n')) {
          this.attempts = 0;
          const destination =
            this.target!.eventId === null ? '/chat/global' : `/chat/events/${this.target!.eventId}`;
          socket.send(`SUBSCRIBE\nid:read\ndestination:${destination}\nack:auto\n\n\0`);
          this.state.update((s) => ({ ...s, connection: 'live' }));
        } else if (frame.startsWith('MESSAGE\n')) {
          // Every hint rebuilds the window, even when its sequence is lower than a prior hint.
          void this.resync(generation);
        } else if (frame.startsWith('ERROR\n')) socket.close(1008);
      }
    };
    socket.onclose = (event) => {
      if (generation !== this.generation || this.socket !== socket) return;
      this.socket = null;
      if (event.code === 1008) {
        this.close();
        void this.session.loadSession();
        return;
      }
      this.reconnect(generation);
    };
  }

  private reconnect(generation: number): void {
    this.state.update((s) => ({ ...s, connection: 'reconnecting' }));
    if (!navigator.onLine) return;
    if (++this.attempts > 3) {
      this.state.update((s) => ({ ...s, connection: 'unavailable' }));
      return;
    }
    this.reconnectTimer = setTimeout(() => this.connect(generation), 1000 * 2 ** this.attempts);
  }

  private disconnect(): void {
    if (this.reconnectTimer) clearTimeout(this.reconnectTimer);
    this.reconnectTimer = null;
    const socket = this.socket;
    this.socket = null;
    socket?.close();
  }

  private readonly offline = () => {
    if (!this.target) return;
    this.disconnect();
    this.state.update((s) => ({ ...s, connection: 'reconnecting' }));
  };
  private readonly online = () => {
    if (this.target) this.connect(this.generation);
  };

  private failed(error: unknown, generation: number, clear: boolean): void {
    if (generation !== this.generation) return;
    if (error instanceof HttpErrorResponse && (error.status === 401 || error.status === 403)) {
      this.close();
      void this.session.loadSession();
      return;
    }
    this.state.update((s) => ({
      ...s,
      status: clear ? 'error' : s.status,
      messages: clear ? [] : s.messages,
      connection: clear ? 'unavailable' : s.connection,
      error: 'Nao foi possivel atualizar as mensagens. Tente novamente.',
    }));
    if (clear) this.disconnect();
  }
}

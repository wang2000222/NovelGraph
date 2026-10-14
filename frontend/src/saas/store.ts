import { defineStore } from 'pinia';
import { request, setCsrf } from './api';
import type { User, Tenant } from './types';
export const useSession = defineStore('session', {
  state: () => ({
    user: null as User | null,
    tenants: [] as Tenant[],
    tenantId: 0,
    ready: false,
    message: '',
    tone: 'success',
    supportEmail: 'support@example.com',
    plans: [] as Record<string, unknown>[],
  }),
  getters: { tenant: (state) => state.tenants.find((t) => t.id === state.tenantId) },
  actions: {
    async load() {
      try {
        const r = await request<{ user: User; csrf: string; tenants: Tenant[] }>('/me');
        this.user = r.user;
        this.tenants = r.tenants;
        setCsrf(r.csrf);
        if (!this.tenants.some((t) => t.id === this.tenantId))
          this.tenantId = this.tenants[0]?.id || 0;
      } catch {
        this.user = null;
        this.tenants = [];
        this.tenantId = 0;
        setCsrf('');
      } finally {
        this.ready = true;
      }
    },
    async public() {
      try {
        const r = await request<{ supportEmail: string; plans: Record<string, unknown>[] }>(
          '/public',
        );
        this.supportEmail = r.supportEmail;
        this.plans = r.plans;
      } catch {
        this.notify('服务尚未连接，请启动新版后端', 'error');
      }
    },
    notify(text: string, tone = 'success') {
      this.message = text;
      this.tone = tone;
      setTimeout(
        () => {
          if (this.message === text) this.message = '';
        },
        tone === 'error' ? 9000 : 5000,
      );
    },
    async logout() {
      await request('/auth/logout', 'POST');
      const { clear } = await import('idb-keyval');
      await clear();
      this.user = null;
      this.tenants = [];
      this.tenantId = 0;
      setCsrf('');
    },
  },
});

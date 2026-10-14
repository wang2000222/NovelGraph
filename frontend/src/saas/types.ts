export type Row = Record<string, unknown> & {
  id: number;
  name?: string;
  revision?: number;
  deleted?: number;
  visibility?: string;
  description?: string;
  data?: string;
  reveal_chapter?: number;
};
export interface User {
  id: number;
  name: string;
  email: string;
  platformRole: string;
}
export interface Tenant {
  id: number;
  name: string;
  role: string;
  kind: string;
  plan_code: string;
}
export interface Project {
  id: number;
  tenant_id: number;
  title: string;
  genre: string;
  overview: string;
  style: string;
  constraints: string;
  revision: number;
  rights?: { role: string; can_export: number; can_secrets: number };
}
export interface Graph {
  project: Project;
  schemaVersion: string;
  views: Row[];
  [kind: string]: unknown;
}
export const kinds: Record<string, string> = {
  characters: '人物',
  tasks: '剧情任务',
  events: '事件',
  relations: '人物关系',
  participants: '事件参与',
  dependencies: '因果与先后',
  changes: '关系变化',
  locations: '地点',
  factions: '势力',
  factionMembers: '势力成员',
  rules: '世界规则',
  secrets: '秘密',
};
export const rows = (g: Graph | null, k: string): Row[] =>
  Array.isArray(g?.[k]) ? (g![k] as Row[]) : [];
export const data = (r: unknown): Record<string, unknown> => {
  if (typeof r === 'object' && r) return r as Record<string, unknown>;
  try {
    return JSON.parse(String(r || '{}'));
  } catch {
    return {};
  }
};

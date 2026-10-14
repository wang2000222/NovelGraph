<script setup lang="ts">
import { ref, shallowRef, computed, onMounted, onBeforeUnmount, watch } from 'vue';
import { useRoute, onBeforeRouteLeave } from 'vue-router';
import {
  VueFlow,
  Handle,
  Position,
  useVueFlow,
  type Node,
  type Edge,
  type Connection,
} from '@vue-flow/core';
import { Background } from '@vue-flow/background';
import { Controls } from '@vue-flow/controls';
import { MiniMap } from '@vue-flow/minimap';
import {
  Plus,
  Search,
  Network,
  CalendarClock,
  ArrowDownToLine,
  ShieldCheck,
  Save,
  Check,
  RotateCcw,
  RotateCw,
  History,
  Trash2,
  Maximize,
  Workflow,
  X,
} from 'lucide-vue-next';
import { get, set, del } from 'idb-keyval';
import { useSession } from './store';
import { request, download } from './api';
import { rows, data, kinds, type Row, type Graph } from './types';
import Dialog from './Dialog.vue';
import ExtractionDialog from './ExtractionDialog.vue';
import '@vue-flow/core/dist/style.css';
import '@vue-flow/core/dist/theme-default.css';
import '@vue-flow/controls/dist/style.css';
import '@vue-flow/minimap/dist/style.css';
const session = useSession(),
  route = useRoute(),
  pid = Number(route.params.id),
  graph = ref<Graph | null>(null),
  category = ref('characters'),
  search = ref(''),
  selected = ref<{ kind: string; id: number } | null>(null),
  form = ref<Record<string, any>>({}),
  dirty = ref(false),
  busy = ref(false),
  saving = ref('已保存'),
  tab = ref('graph'),
  dialog = ref(''),
  nodes = shallowRef<Node[]>([]),
  edges = shallowRef<Edge[]>([]),
  issues = ref<Row[]>([]),
  snapshots = ref<Row[]>([]),
  showDeleted = ref(false),
  showMini = ref(false),
  layoutRevision = ref(0),
  exportForm = ref({
    format: 'MARKDOWN',
    perspective: 'READER',
    chapterFrom: 1,
    chapterTo: 20,
    relationDepth: 1,
    characterIds: [] as number[],
    eventIds: [] as number[],
    storyFrom: '' as string | number,
    storyTo: '' as string | number,
    includeSecrets: false,
    writingTask: '',
    branch: '',
  }),
  preview = ref<{
    content: string;
    revision: number;
    characters: number;
    containsUnrevealed: boolean;
  } | null>(null),
  projectForm = ref<Record<string, any>>({}),
  projectGenres = ref<{ id: number; name: string }[]>([]),
  permission = ref({ userId: 0, role: 'EDITOR', canExport: false, canSecrets: false }),
  autoSaving = ref(false),
  layingOut = ref(false);
const { fitView: fitGraph } = useVueFlow();
function fitView() {
  return fitGraph({
    maxZoom: 1,
    padding: 0.2,
    duration: matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 380,
  });
}
const editable = computed(() =>
    ['ADMIN', 'EDITOR'].includes(graph.value?.project.rights?.role || ''),
  ),
  secretAllowed = computed(() => graph.value?.project.rights?.can_secrets === 1),
  exportAllowed = computed(() => graph.value?.project.rights?.can_export === 1);
const activeRows = (kind: string) => rows(graph.value, kind).filter((r) => !r.deleted);
const directory = computed(() =>
  rows(graph.value, category.value).filter(
    (r) => Boolean(r.deleted) === showDeleted.value && String(r.name || '').includes(search.value),
  ),
);
const timeline = computed(() =>
  activeRows('events')
    .slice()
    .sort((a, b) => Number(a.story_seq ?? Infinity) - Number(b.story_seq ?? Infinity)),
);
const directoryTop = ref(0),
  timelineTop = ref(0);
const directoryStart = computed(() => Math.max(0, Math.floor((directoryTop.value - 50) / 68) - 5));
const visibleDirectory = computed(() =>
  directory.value.slice(directoryStart.value, directoryStart.value + 30),
);
const timelineStart = computed(() => Math.max(0, Math.floor((timelineTop.value - 80) / 164) - 5));
const visibleTimeline = computed(() =>
  timeline.value.slice(timelineStart.value, timelineStart.value + 25),
);
watch([category, search], () => (directoryTop.value = 0));
const selectedRow = computed(() =>
  selected.value
    ? rows(graph.value, selected.value.kind).find((r) => r.id === selected.value!.id)
    : null,
);
const draftKey = `ng:${session.user?.id}:${session.tenantId}:${pid}:draft`;
let editSequence = 0;
let saveTimer: ReturnType<typeof setTimeout> | undefined,
  layoutTimer: ReturnType<typeof setTimeout> | undefined,
  worker: Worker | null = null,
  stopped = false,
  layoutSaving = false,
  layoutAgain = false;
interface Command {
  type: 'layout' | 'entity';
  before: unknown;
  after: unknown;
  kind?: string;
  id?: number;
  created?: boolean;
}
const permissionMembers = ref<Record<string, any>[]>([]),
  grants = ref<Record<string, any>[]>([]);
const undoStack = ref<Command[]>([]),
  redoStack = ref<Command[]>([]);
let dragBefore: unknown = null;
const endpoint = `/projects/${pid}`;
function model(r: Row) {
  const result: Record<string, unknown> = { ...r, data: data(r.data) };
  for (const key of [
    'source_id',
    'target_id',
    'event_id',
    'character_id',
    'relation_id',
    'effective_seq',
    'story_seq',
    'location_id',
    'anchor_id',
    'parent_id',
    'faction_id',
    'start_seq',
    'end_seq',
    'time_kind',
    'time_text',
    'starts_at',
    'ends_at',
    'reveal_chapter',
  ])
    result[key.replace(/_([a-z])/g, (_, c: string) => c.toUpperCase())] = r[key] ?? '';
  return result;
}
async function load(rebuild = true) {
  graph.value = await request<Graph>(endpoint + '/graph');
  if (rebuild) buildGraph();
  if (selected.value) {
    const r = selectedRow.value;
    if (r && !dirty.value) form.value = model(r);
  }
}
function buildGraph() {
  const view = rows(graph.value, 'views')[0];
  layoutRevision.value = Number(view?.revision || 0);
  const positions = data(view?.layout);
  const stored = Array.isArray(positions.nodes)
    ? (positions.nodes as { kind: string; id: number; x: number; y: number }[])
    : [];
  nodes.value = ['characters', 'events', 'locations', 'factions'].flatMap((kind, ki) =>
    activeRows(kind).map((r, i) => {
      const pos = stored.find((p) => p.kind === kind && p.id === r.id);
      return {
        id: `${kind}:${r.id}`,
        type: 'world',
        position: { x: pos?.x ?? 80 + ki * 280, y: pos?.y ?? 60 + i * 145 },
        data: { kind, id: r.id, name: r.name, description: r.description },
        draggable: editable.value,
        connectable: editable.value,
      };
    }),
  );
  const e: Edge[] = [];
  for (const r of activeRows('relations'))
    e.push({
      id: `relations:${r.id}`,
      source: `characters:${r.source_id}`,
      target: `characters:${r.target_id}`,
      label: r.name,
      markerEnd: r.direction === 'BIDIRECTIONAL' ? undefined : 'arrowclosed',
      data: { kind: 'relations', id: r.id },
      style: { stroke: '#738a75' },
    });
  for (const r of activeRows('participants'))
    e.push({
      id: `participants:${r.id}`,
      source: `events:${r.event_id}`,
      target: `characters:${r.character_id}`,
      label: String(r.role),
      data: { kind: 'participants', id: r.id },
      style: { stroke: '#b89e79', strokeDasharray: '5 4' },
    });
  for (const r of activeRows('dependencies'))
    e.push({
      id: `dependencies:${r.id}`,
      source: `events:${r.source_id}`,
      target: `events:${r.target_id}`,
      label: String(r.type),
      markerEnd: 'arrowclosed',
      data: { kind: 'dependencies', id: r.id },
      style: { stroke: '#9b7fb7' },
    });
  const ids = new Set(nodes.value.map((n) => n.id));
  edges.value = e.filter((x) => ids.has(x.source) && ids.has(x.target));
}
async function choose(kind: string, id: number) {
  if (autoSaving.value || busy.value) {
    session.notify('正在保存，请稍候');
    return;
  }
  if (dirty.value && !confirm('当前资料尚未同步，切换前请保存。仍要切换吗？')) return;
  clearTimeout(saveTimer);
  selected.value = { kind, id };
  category.value = kind;
  const r = selectedRow.value;
  if (r) {
    form.value = model(r);
    dirty.value = false;
    await del(draftKey);
  }
}
function create(kind = category.value, values: Record<string, unknown> = {}) {
  if (autoSaving.value || busy.value) return;
  if (!editable.value) return;
  if (dirty.value && !confirm('当前资料尚未保存，放弃修改吗？')) return;
  clearTimeout(saveTimer);
  selected.value = { kind, id: 0 };
  category.value = kind;
  form.value = {
    name: '',
    description: '',
    visibility: 'PUBLIC',
    data: {},
    timeKind: 'UNKNOWN',
    direction: 'DIRECTED',
    revision: 0,
    ...values,
  };
  dirty.value = false;
  dialog.value = '';
}
function changed() {
  editSequence++;
  dirty.value = true;
  saving.value = '未同步更改';
  void set(
    draftKey,
    JSON.parse(
      JSON.stringify({
        selected: selected.value,
        form: form.value,
        baseRevision: graph.value?.project.revision,
      }),
    ),
  ).catch(() => session.notify('本机草稿存储不可用，请及时保存', 'error'));
  clearTimeout(saveTimer);
  saveTimer = setTimeout(() => {
    if (dirty.value && String(form.value.name || '').trim()) void save(true);
  }, 800);
}
function body() {
  const b = { ...form.value };
  for (const k of [
    'sourceId',
    'targetId',
    'eventId',
    'characterId',
    'relationId',
    'effectiveSeq',
    'storySeq',
    'locationId',
    'anchorId',
    'parentId',
    'factionId',
    'startSeq',
    'endSeq',
    'revealChapter',
  ])
    b[k] = b[k] === '' || b[k] == null ? null : Number(b[k]);
  return b;
}
async function save(auto = false, record = true) {
  if (!selected.value || busy.value || autoSaving.value) return;
  // 已同步的资料不重复提交，避免产生无意义的版本与撤销记录。
  if (!dirty.value && selected.value.id !== 0) {
    if (!auto) session.notify('所有修改已保存，无需重复保存');
    return;
  }
  const selection = { ...selected.value },
    before = selectedRow.value ? model(selectedRow.value) : null;
  const b = JSON.parse(JSON.stringify(body()));
  const sequence = editSequence;
  if (!String(b.name || '').trim()) {
    if (!auto) session.notify('请填写名称', 'error');
    return;
  }
  // 手动保存接管待执行的自动保存，防止同一修改再次提交。
  clearTimeout(saveTimer);
  if (auto) autoSaving.value = true;
  else busy.value = true;
  saving.value = '保存中';
  try {
    const saved = await request<Row>(
      `${endpoint}/entities/${selection.kind}${selection.id ? '/' + selection.id : ''}`,
      selection.id ? 'PATCH' : 'POST',
      b,
    );
    selected.value = { kind: selection.kind, id: saved.id };
    if (sequence === editSequence) {
      dirty.value = false;
      form.value = model(saved);
    } else {
      form.value.revision = saved.revision;
      dirty.value = true;
    }

    if (record) {
      undoStack.value.push({
        type: 'entity',
        before,
        after: model(saved),
        kind: selection.kind,
        id: saved.id,
        created: !selection.id,
      });
      redoStack.value = [];
    }
    if (!dirty.value) await del(draftKey);
    await load(!layoutSaving && !layoutDirty.value);
    saving.value = dirty.value ? '未同步更改' : '已保存';
    if (!auto) session.notify('设定已保存');
  } catch (e) {
    saving.value = '保存失败';
    if (!auto) session.notify((e as Error).message, 'error');
  } finally {
    busy.value = false;
    autoSaving.value = false;
    if (dirty.value && sequence !== editSequence) changed();
  }
}
const layoutDirty = ref(false);
const layoutDraftKey = draftKey + ':layout';
const layoutState = () =>
  nodes.value.map((n) => ({ kind: n.data.kind, id: n.data.id, x: n.position.x, y: n.position.y }));
function dragStart() {
  dragBefore = structuredClone(layoutState());
}
function dragStop() {
  undoStack.value.push({
    type: 'layout',
    before: dragBefore,
    after: structuredClone(layoutState()),
  });
  redoStack.value = [];
  queueLayout();
}
function queueLayout() {
  layoutDirty.value = true;
  void set(layoutDraftKey, { nodes: layoutState(), revision: layoutRevision.value }).catch(
    () => {},
  );
  saving.value = '未同步更改';
  clearTimeout(layoutTimer);
  layoutTimer = setTimeout(() => void saveLayout().catch(() => {}), 500);
}
async function saveLayout() {
  if (!editable.value) return;
  if (layoutSaving) {
    layoutAgain = true;
    return;
  }
  layoutSaving = true;
  saving.value = '保存中';
  try {
    const r = await request<{ revision: number }>(endpoint + '/layout', 'POST', {
      viewKey: 'main',
      baseLayoutRevision: layoutRevision.value,
      layout: { nodes: layoutState() },
    });
    layoutRevision.value = r.revision;
    if (!layoutAgain) {
      layoutDirty.value = false;
      await del(layoutDraftKey);
    }
    saving.value = dirty.value ? '未同步更改' : '已保存';
  } catch (e) {
    saving.value = '布局保存失败';
    session.notify((e as Error).message, 'error');
    throw e;
  } finally {
    layoutSaving = false;
    if (layoutAgain) {
      layoutAgain = false;
      void saveLayout().catch(() => {});
    }
  }
}
function connected(c: Connection) {
  const [a, aid] = (c.source || '').split(':'),
    [b, bid] = (c.target || '').split(':');
  if (a === 'characters' && b === 'characters')
    create('relations', { sourceId: Number(aid), targetId: Number(bid), name: '人物关系' });
  else if (a === 'events' && b === 'events')
    create('dependencies', {
      sourceId: Number(aid),
      targetId: Number(bid),
      type: 'BEFORE',
      name: '事件先后',
    });
  else if ((a === 'events' && b === 'characters') || (a === 'characters' && b === 'events'))
    create('participants', {
      eventId: Number(a === 'events' ? aid : bid),
      characterId: Number(a === 'characters' ? aid : bid),
      role: '参与',
      name: '事件参与',
    });
  else session.notify('请从目录编辑地点或势力关联');
}
async function history(redo = false) {
  const stack = redo ? redoStack : undoStack,
    other = redo ? undoStack : redoStack;
  const command = stack.value[stack.value.length - 1];
  if (!command || busy.value) return;
  try {
    if (command.type === 'layout') {
      const state = (redo ? command.after : command.before) as {
        kind: string;
        id: number;
        x: number;
        y: number;
      }[];
      nodes.value = nodes.value.map((n) => {
        const pos = state.find((p) => `${p.kind}:${p.id}` === n.id);
        return pos ? { ...n, position: { x: pos.x, y: pos.y } } : n;
      });
      await saveLayout();
    } else {
      const current = rows(graph.value, command.kind!).find((r) => r.id === command.id);
      if (!current) throw new Error('资料状态已变化，无法撤销');
      if (command.created) {
        if (redo)
          await request(`${endpoint}/entities/${command.kind}/${command.id}/restore`, 'POST', {
            revision: current.revision,
          });
        else
          await request(`${endpoint}/entities/${command.kind}/${command.id}`, 'DELETE', {
            revision: current.revision,
          });
      } else {
        const payload = JSON.parse(JSON.stringify(redo ? command.after : command.before));
        payload.revision = current.revision;
        await request(`${endpoint}/entities/${command.kind}/${command.id}`, 'PATCH', payload);
      }
      await load();
    }
    stack.value.pop();
    other.value.push(command);
  } catch (e) {
    session.notify((e as Error).message, 'error');
  }
}
async function remove() {
  if (
    !selectedRow.value ||
    !confirm('删除此资料？关联关系与参与记录会受影响。资料可在已删除列表中恢复。')
  )
    return;
  busy.value = true;
  try {
    await request(`${endpoint}/entities/${selected.value!.kind}/${selected.value!.id}`, 'DELETE', {
      revision: selectedRow.value.revision,
    });
    selected.value = null;
    dirty.value = false;
    clearTimeout(saveTimer);
    await del(draftKey);
    await load();
    session.notify('资料已删除，可在回收列表恢复');
  } catch (e) {
    session.notify((e as Error).message, 'error');
  } finally {
    busy.value = false;
  }
}
async function restoreRow(r: Row) {
  try {
    await request(`${endpoint}/entities/${category.value}/${r.id}/restore`, 'POST', {
      revision: r.revision,
    });
    await load();
    session.notify('资料已恢复，关联资料需分别检查');
  } catch (e) {
    session.notify((e as Error).message, 'error');
  }
}
function autoLayout() {
  if (layingOut.value) {
    worker?.terminate();
    layingOut.value = false;
    return;
  }
  layingOut.value = true;
  const before = structuredClone(layoutState());
  worker = new Worker(new URL('./layout.worker.ts', import.meta.url), { type: 'module' });
  worker.onmessage = (e) => {
    if (stopped) return;
    if (JSON.stringify(before) !== JSON.stringify(layoutState())) {
      worker?.terminate();
      layingOut.value = false;
      session.notify('布局期间画布已变化，已保留你的拖动');
      return;
    }
    if (e.data.error) session.notify(e.data.error, 'error');
    else {
      nodes.value = nodes.value.map((n) => {
        const p = (e.data.nodes as { id: string; x: number; y: number }[]).find(
          (p) => p.id === n.id,
        );
        return p ? { ...n, position: { x: p.x, y: p.y } } : n;
      });
      undoStack.value.push({ type: 'layout', before, after: structuredClone(layoutState()) });
      redoStack.value = [];
      queueLayout();
      fitView();
    }
    layingOut.value = false;
    worker?.terminate();
  };
  worker.postMessage({
    nodes: nodes.value.map((n) => ({ id: n.id })),
    edges: edges.value.map((e) => ({ id: e.id, source: e.source, target: e.target })),
  });
}
async function check() {
  try {
    const r = await request<{ issues: Row[] }>(endpoint + '/validate', 'POST');
    issues.value = r.issues;
    dialog.value = 'validate';
  } catch (e) {
    session.notify((e as Error).message, 'error');
  }
}
async function openHistory() {
  try {
    snapshots.value = await request<Row[]>(endpoint + '/snapshots');
    dialog.value = 'history';
  } catch (e) {
    session.notify((e as Error).message, 'error');
  }
}
async function snapshot() {
  try {
    await request(endpoint + '/snapshots', 'POST', {
      name: `恢复点 ${new Date().toLocaleString('zh-CN')}`,
    });
    await openHistory();
    session.notify('恢复点已创建');
  } catch (e) {
    session.notify((e as Error).message, 'error');
  }
}
async function restoreSnapshot(s: Row) {
  if (!confirm('恢复此版本会将项目内容改为该恢复点，同时创建恢复前快照。继续吗？')) return;
  busy.value = true;
  try {
    graph.value = await request<Graph>(`${endpoint}/snapshots/${s.id}/restore`, 'POST', {
      baseRevision: graph.value?.project.revision,
    });
    selected.value = null;
    buildGraph();
    dialog.value = '';
    session.notify('已恢复为新的内容版本');
  } catch (e) {
    session.notify((e as Error).message, 'error');
  } finally {
    busy.value = false;
  }
}
function exportBody() {
  return {
    format: exportForm.value.format,
    perspective: exportForm.value.perspective,
    baseContentRevision: preview.value?.revision ?? graph.value?.project.revision,
    includeSecrets: exportForm.value.includeSecrets,
    writingTask: exportForm.value.writingTask,
    scope: {
      characterIds: exportForm.value.characterIds,
      eventIds: exportForm.value.eventIds,
      branch: exportForm.value.branch,
      relationDepth: exportForm.value.relationDepth,
      chapterFrom: exportForm.value.chapterFrom,
      chapterTo: exportForm.value.chapterTo,
      ...(exportForm.value.storyFrom !== ''
        ? { storyFrom: Number(exportForm.value.storyFrom) }
        : {}),
      ...(exportForm.value.storyTo !== '' ? { storyTo: Number(exportForm.value.storyTo) } : {}),
    },
  };
}
async function exportPreview() {
  busy.value = true;
  try {
    preview.value = await request(endpoint + '/exports/preview', 'POST', exportBody());
  } catch (e) {
    session.notify((e as Error).message, 'error');
  } finally {
    busy.value = false;
  }
}
async function copyExport() {
  if (!preview.value) return;
  try {
    await navigator.clipboard.writeText(preview.value.content);
    session.notify('设定提示词已复制');
  } catch {
    session.notify('无法自动复制，请在预览中手动选择', 'error');
  }
}
async function downloadExport() {
  try {
    await download(
      endpoint + '/exports/download',
      exportBody(),
      `novelgraph-${pid}.${['JSON', 'BACKUP'].includes(exportForm.value.format) ? 'json' : 'md'}`,
    );
  } catch (e) {
    session.notify((e as Error).message, 'error');
  }
}
watch(exportForm, () => (preview.value = null), { deep: true });
interface Field {
  key: string;
  label: string;
  type?: string;
  options?: { id: number | string; name: string }[];
}
const options = (kind: string) => activeRows(kind).map((x) => ({ id: x.id, name: String(x.name) }));
const fields = computed<Field[]>(() => {
  const k = selected.value?.kind;
  const f: Field[] = [];
  if (k === 'events')
    f.push(
      {
        key: 'timeKind',
        label: '时间类型',
        type: 'select',
        options: ['UNKNOWN', 'EXACT', 'RANGE', 'FUZZY', 'RELATIVE'].map((x) => ({
          id: x,
          name: {
            UNKNOWN: '未设定',
            EXACT: '精确时间',
            RANGE: '时间区间',
            FUZZY: '模糊时间',
            RELATIVE: '相对时间',
          }[x as 'UNKNOWN'],
        })),
      },
      { key: 'timeText', label: '时间表达' },
      { key: 'startsAt', label: '开始时间（ISO日期或故事内表达）' },
      { key: 'endsAt', label: '结束时间（可选）' },
      { key: 'storySeq', label: '故事序号', type: 'number' },
      { key: 'anchorId', label: '锚定事件', type: 'select', options: options('events') },
      { key: 'locationId', label: '地点', type: 'select', options: options('locations') },
    );
  if (k === 'relations')
    f.push(
      { key: 'sourceId', label: '起点人物', type: 'select', options: options('characters') },
      { key: 'targetId', label: '终点人物', type: 'select', options: options('characters') },
      {
        key: 'direction',
        label: '关系方向',
        type: 'select',
        options: [
          { id: 'DIRECTED', name: '单向' },
          { id: 'BIDIRECTIONAL', name: '双向' },
        ],
      },
      { key: 'startSeq', label: '生效故事序号', type: 'number' },
      { key: 'endSeq', label: '结束故事序号', type: 'number' },
    );
  if (k === 'participants')
    f.push(
      { key: 'eventId', label: '事件', type: 'select', options: options('events') },
      { key: 'characterId', label: '人物', type: 'select', options: options('characters') },
      { key: 'role', label: '参与角色' },
    );
  if (k === 'dependencies')
    f.push(
      { key: 'sourceId', label: '起点事件', type: 'select', options: options('events') },
      { key: 'targetId', label: '终点事件', type: 'select', options: options('events') },
      {
        key: 'type',
        label: '连接类型',
        type: 'select',
        options: [
          { id: 'BEFORE', name: '发生在之前' },
          { id: 'CAUSES', name: '导致' },
          { id: 'ENABLES', name: '促成条件' },
        ],
      },
    );
  if (k === 'changes')
    f.push(
      { key: 'relationId', label: '变化的关系', type: 'select', options: options('relations') },
      { key: 'eventId', label: '触发事件', type: 'select', options: options('events') },
      { key: 'effectiveSeq', label: '生效故事序号', type: 'number' },
    );
  if (k === 'factionMembers')
    f.push(
      { key: 'factionId', label: '势力', type: 'select', options: options('factions') },
      { key: 'characterId', label: '成员人物', type: 'select', options: options('characters') },
      { key: 'role', label: '成员身份' },
    );
  if (k === 'tasks')
    f.push(
      { key: 'characterId', label: '执行人物', type: 'select', options: options('characters') },
      { key: 'eventId', label: '关联事件', type: 'select', options: options('events') },
    );
  if (k === 'locations')
    f.push({ key: 'parentId', label: '上级地点', type: 'select', options: options('locations') });
  return f;
});
const dataFields = computed(() =>
  selected.value?.kind === 'characters'
    ? [
        { key: 'identity', label: '身份' },
        { key: 'personality', label: '性格' },
        { key: 'goals', label: '目标与动机' },
        { key: 'background', label: '公开背景' },
        { key: 'tags', label: '标签 / 支线' },
      ]
    : selected.value?.kind === 'tasks'
      ? [
          { key: 'objective', label: '任务目标' },
          { key: 'status', label: '任务状态' },
          { key: 'outcome', label: '任务结果' },
        ]
      : selected.value?.kind === 'participants'
        ? [{ key: 'knowledge', label: '知情备注（不自动推断）' }]
        : selected.value?.kind === 'events'
          ? [
              { key: 'branch', label: '剧情支线' },
              { key: 'relativeOrder', label: '相对先后说明' },
            ]
          : [],
);
async function openProject() {
  try {
    projectGenres.value = await request('/genres');
    projectForm.value = {
      ...graph.value?.project,
      genreId: projectGenres.value.find((g) => g.name === graph.value?.project.genre)?.id ?? '',
    };
    dialog.value = 'project';
  } catch (e) {
    session.notify((e as Error).message, 'error');
  }
}
async function saveProject() {
  busy.value = true;
  try {
    await request(endpoint, 'PATCH', {
      ...projectForm.value,
      genreId: projectForm.value.genreId ? Number(projectForm.value.genreId) : undefined,
      baseRevision: graph.value?.project.revision,
    });
    dialog.value = '';
    await load();
    session.notify('项目设定已更新');
  } catch (e) {
    session.notify((e as Error).message, 'error');
  } finally {
    busy.value = false;
  }
}
async function openPermissions() {
  try {
    const r = await request<{ members: Record<string, any>[]; grants: Record<string, any>[] }>(
      endpoint + '/permissions',
    );
    permissionMembers.value = r.members;
    grants.value = r.grants;
    dialog.value = 'permissions';
  } catch (e) {
    session.notify((e as Error).message, 'error');
  }
}
async function revokeGrant(userId: number) {
  try {
    await request(endpoint + '/permissions', 'POST', { userId, revoke: true });
    await openPermissions();
    session.notify('项目访问已撤销');
  } catch (e) {
    session.notify((e as Error).message, 'error');
  }
}
async function deleteSnapshot(s: Row) {
  if (!confirm('永久删除此恢复点并释放额度？')) return;
  try {
    await request(`${endpoint}/snapshots/${s.id}`, 'DELETE');
    await openHistory();
    session.notify('恢复点已删除');
  } catch (e) {
    session.notify((e as Error).message, 'error');
  }
}
async function grant() {
  try {
    await request(endpoint + '/permissions', 'POST', permission.value);
    dialog.value = '';
    session.notify('项目权限已更新');
  } catch (e) {
    session.notify((e as Error).message, 'error');
  }
}
function keys(e: KeyboardEvent) {
  if ((e.ctrlKey || e.metaKey) && e.key === 's') {
    e.preventDefault();
    void save();
    return;
  }
  if (
    e.target instanceof HTMLElement &&
    e.target.closest('input,textarea,select,[contenteditable]')
  )
    return;
  if ((e.ctrlKey || e.metaKey) && e.key === 's') {
    e.preventDefault();
    void save();
  }
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'z') {
    e.preventDefault();
    void history(e.shiftKey);
  }
  if (e.key === 'Delete') void remove();
}
onMounted(async () => {
  try {
    await load();
    const cached = await get<{
      selected: { kind: string; id: number };
      form: Record<string, unknown>;
      baseRevision: number;
    }>(draftKey);
    if (cached && confirm('发现未同步草稿，是否恢复到属性面板？')) {
      selected.value = cached.selected;
      form.value = cached.form;
      dirty.value = true;
      saving.value =
        cached.baseRevision === graph.value?.project.revision
          ? '未同步更改'
          : '版本冲突，草稿已保留';
    }
    const layoutDraft = await get<{
      nodes: { kind: string; id: number; x: number; y: number }[];
      revision: number;
    }>(layoutDraftKey);
    if (layoutDraft && confirm('发现未同步的画布布局，是否恢复？')) {
      nodes.value = nodes.value.map((n) => {
        const p = layoutDraft.nodes.find((p) => `${p.kind}:${p.id}` === n.id);
        return p ? { ...n, position: { x: p.x, y: p.y } } : n;
      });
      layoutDirty.value = true;
      if (layoutDraft.revision === layoutRevision.value) queueLayout();
      else saving.value = '布局版本冲突，草稿已保留';
    }
    document.addEventListener('keydown', keys);
    setTimeout(() => fitView(), 150);
  } catch (e) {
    session.notify((e as Error).message, 'error');
  }
});
onBeforeRouteLeave(async () => {
  if (layoutDirty.value && !layoutSaving) {
    try {
      await saveLayout();
    } catch {}
  }
  if (
    (dirty.value || layoutDirty.value || autoSaving.value || layoutSaving) &&
    !confirm('有未同步设定，草稿已保留在本机。离开吗？')
  )
    return false;
});
onBeforeUnmount(() => {
  stopped = true;
  clearTimeout(saveTimer);
  clearTimeout(layoutTimer);
  worker?.terminate();
  document.removeEventListener('keydown', keys);
});
</script>
<template>
  <div class="editor-root">
    <main v-if="graph" class="editor-page">
      <header class="editor-toolbar">
        <div>
          <span class="eyebrow">WORLD EDITOR</span
          ><button class="title-button" @click="openProject">
            {{ graph.project.title }} <span>⌄</span>
          </button>
        </div>
        <span class="save-status" :class="{ warn: saving !== '已保存' }"><i></i>{{ saving }}</span>
        <div class="editor-tool-actions">
          <button
            class="icon"
            title="撤销 Ctrl+Z"
            :disabled="!undoStack.length || !editable"
            @click="history()"
          >
            <RotateCcw :size="17" /></button
          ><button
            class="icon"
            title="重做"
            :disabled="!redoStack.length || !editable"
            @click="history(true)"
          >
            <RotateCw :size="17" /></button
          ><button class="btn small" @click="check"><ShieldCheck :size="15" />检查</button
          ><button class="btn small" :disabled="!secretAllowed" @click="openHistory">
            <History :size="15" />历史</button
          ><button
            class="btn primary small"
            :disabled="!exportAllowed || dirty"
            @click="
              dialog = 'export';
              preview = null;
            "
          >
            <ArrowDownToLine :size="15" />导出提示词
          </button>
          <button
            class="btn small"
            :disabled="!editable || !secretAllowed || dirty || busy || autoSaving"
            @click="dialog = 'extraction'"
          >
            文本提取 / AI导入
          </button>
        </div>
      </header>
      <div class="editor-body">
        <aside class="resource-panel">
          <div class="resource-heading">
            <strong>世界资料</strong
            ><button class="icon" :disabled="!editable" aria-label="新增资料" @click="create()">
              <Plus :size="18" />
            </button>
          </div>
          <label class="search compact"
            ><Search :size="15" /><input v-model="search" placeholder="搜索资料"
          /></label>
          <div class="resource-tabs">
            <button
              v-for="(label, k) in kinds"
              v-show="k !== 'secrets' || secretAllowed"
              :key="k"
              :class="{ active: category === k }"
              @click="category = String(k)"
            >
              {{ label }}<small>{{ activeRows(String(k)).length }}</small>
            </button>
          </div>
          <div
            class="resource-list"
            @scroll="directoryTop = ($event.target as HTMLElement).scrollTop"
          >
            <div class="resource-list-title">
              {{ kinds[category]
              }}<label><input v-model="showDeleted" type="checkbox" />已删除</label>
            </div>
            <div v-if="!directory.length" class="small-empty">
              {{ showDeleted ? '暂无已删除资料' : '尚未建立资料'
              }}<button v-if="!showDeleted && editable" class="text-btn" @click="create()">
                ＋ 创建{{ kinds[category] }}
              </button>
            </div>
            <div
              :style="{
                paddingTop: directoryStart * 68 + 'px',
                paddingBottom:
                  Math.max(0, directory.length - directoryStart - visibleDirectory.length) * 68 +
                  'px',
              }"
            >
              <div
                v-for="r in visibleDirectory"
                :key="r.id"
                class="resource-item"
                :class="{ selected: selected?.id === r.id && selected?.kind === category }"
              >
                <button @click="choose(category, r.id)">
                  <span class="entity-dot" :class="category"></span
                  ><span
                    >{{ r.name
                    }}<small>{{
                      r.visibility === 'AUTHOR_ONLY'
                        ? '作者私密'
                        : r.reveal_chapter
                          ? `第${r.reveal_chapter}章揭示`
                          : '公开设定'
                    }}</small></span
                  ></button
                ><button v-if="showDeleted && editable" class="text-btn" @click="restoreRow(r)">
                  恢复
                </button>
              </div>
            </div>
          </div>
          <div class="resource-foot">
            内容版本 {{ graph.project.revision
            }}<button
              v-if="graph.project.rights?.role === 'ADMIN'"
              class="text-btn"
              @click="openPermissions"
            >
              项目授权
            </button>
          </div>
        </aside>
        <section class="canvas-column">
          <div class="canvas-switch">
            <div class="segmented">
              <button :class="{ active: tab === 'graph' }" @click="tab = 'graph'">
                <Network :size="15" />关系图</button
              ><button :class="{ active: tab === 'timeline' }" @click="tab = 'timeline'">
                <CalendarClock :size="15" />时间线
              </button>
            </div>
            <div v-if="tab === 'graph'" class="actions">
              <button class="canvas-tool" :disabled="!editable" @click="autoLayout">
                <Workflow :size="15" />{{ layingOut ? '取消布局' : '自动布局' }}</button
              ><button class="icon" title="适应视图" @click="fitView()">
                <Maximize :size="16" /></button
              ><button class="icon" title="迷你地图" @click="showMini = !showMini">▧</button>
            </div>
          </div>
          <div v-if="tab === 'graph'" class="canvas-wrap">
            <VueFlow
              v-model:nodes="nodes"
              v-model:edges="edges"
              :only-render-visible-elements="true"
              :min-zoom="0.05"
              :max-zoom="2"
              :nodes-draggable="editable"
              :nodes-connectable="editable"
              :select-nodes-on-drag="false"
              :selection-on-drag="true"
              @node-click="({ node }) => choose(node.data.kind, node.data.id)"
              @edge-click="({ edge }) => choose(edge.data.kind, edge.data.id)"
              @node-drag-start="dragStart"
              @node-drag-stop="dragStop"
              @connect="connected"
              @pane-double-click="create('characters')"
              ><template #node-world="{ data: nodeData }"
                ><div class="world-node" :class="nodeData.kind">
                  <Handle type="target" :position="Position.Left" />
                  <div class="node-top">
                    <span>{{ kinds[nodeData.kind] }}</span
                    ><span>•••</span>
                  </div>
                  <strong>{{ nodeData.name }}</strong>
                  <p>{{ String(nodeData.description || '点击完善设定').slice(0, 35) }}</p>
                  <Handle type="source" :position="Position.Right" /></div></template
              ><Background :gap="22" color="#dadbd2" /><Controls /><MiniMap v-if="showMini"
            /></VueFlow>
            <div v-if="!nodes.length" class="canvas-empty">
              <Network :size="46" />
              <h2>让世界从第一个人物开始</h2>
              <p>创建人物和事件，拖动节点端口连接关系。</p>
              <button class="btn primary" :disabled="!editable" @click="create('characters')">
                <Plus :size="16" />创建人物
              </button>
            </div>
            <div class="canvas-legend">
              <span><i class="characters"></i>人物</span><span><i class="events"></i>事件</span
              ><span>实线 · 关系 / 因果</span><span>虚线 · 参与</span>
            </div>
          </div>
          <div
            v-else
            class="timeline-list"
            @scroll="timelineTop = ($event.target as HTMLElement).scrollTop"
          >
            <div class="timeline-intro">
              <h2>事件时间线</h2>
              <p class="muted">故事发生时间与读者揭示章节分开保存。未知顺序进入待定位区。</p>
            </div>
            <div
              :style="{
                paddingTop: timelineStart * 164 + 'px',
                paddingBottom:
                  Math.max(0, timeline.length - timelineStart - visibleTimeline.length) * 164 +
                  'px',
              }"
            >
              <button
                v-for="r in visibleTimeline"
                :key="r.id"
                class="timeline-card"
                @click="choose('events', r.id)"
              >
                <span class="timeline-time"
                  >{{ r.story_seq != null ? `序号 ${r.story_seq}` : '待定位'
                  }}<small>{{ r.time_text || '时间未设定' }}</small></span
                >
                <div>
                  <span class="eyebrow">{{ r.time_kind }}</span>
                  <h3>{{ r.name }}</h3>
                  <p>{{ r.description }}</p>
                  <small>{{
                    r.reveal_chapter ? `第${r.reveal_chapter}章揭示` : '未设定揭示章节'
                  }}</small>
                </div>
              </button>
            </div>
            <div v-if="!timeline.length" class="empty">创建事件后，在这里梳理剧情顺序。</div>
          </div>
        </section>
        <aside class="properties-panel">
          <template v-if="selected"
            ><div class="resource-heading">
              <strong>{{ selected.id ? '编辑' : '新建' }}{{ kinds[selected.kind] }}</strong
              ><button class="icon" aria-label="关闭属性" @click="selected = null">
                <X :size="16" />
              </button>
            </div>
            <form class="property-form" @submit.prevent="save()">
              <fieldset :disabled="!editable || Boolean(selectedRow?.deleted)">
                <label>名称<input v-model="form.name" required @input="changed" /></label
                ><label
                  >描述<textarea
                    v-model="form.description"
                    rows="4"
                    placeholder="记录作者确认的事实与设定"
                    @input="changed"
                  /></label
                ><template v-for="field in fields" :key="field.key"
                  ><label
                    >{{ field.label
                    }}<select
                      v-if="field.type === 'select'"
                      v-model="form[field.key]"
                      @change="changed"
                    >
                      <option value="">未设定</option>
                      <option v-for="o in field.options" :key="o.id" :value="o.id">
                        {{ o.name }}
                      </option></select
                    ><input
                      v-else
                      v-model="form[field.key]"
                      :type="field.type || 'text'"
                      @input="changed" /></label></template
                ><label v-for="f in dataFields" :key="f.key"
                  >{{ f.label
                  }}<textarea
                    v-model="(form.data as Record<string, any>)[f.key]"
                    rows="2"
                    @input="changed"
                  /></label
                ><template v-if="selected.kind === 'changes'"
                  ><label
                    >变化后关系名称<input
                      :value="
                        String(data((form.data as Record<string, any>).afterState).name || '')
                      "
                      @input="
                        (form.data as Record<string, any>).afterState = {
                          ...data((form.data as Record<string, any>).afterState),
                          name: ($event.target as HTMLInputElement).value,
                        };
                        changed();
                      " /></label
                  ><label
                    >变化后描述<textarea
                      :value="
                        String(
                          data((form.data as Record<string, any>).afterState).description || '',
                        )
                      "
                      @input="
                        (form.data as Record<string, any>).afterState = {
                          ...data((form.data as Record<string, any>).afterState),
                          description: ($event.target as HTMLTextAreaElement).value,
                        };
                        changed();
                      "
                    /></label
                ></template>
                <div class="field-section">揭示与可见性</div>
                <label
                  >资料可见性<select v-model="form.visibility" @change="changed">
                    <option value="PUBLIC">公开设定</option>
                    <option value="REVEALED">按章节揭示</option>
                    <option v-if="secretAllowed" value="AUTHOR_ONLY">仅作者可见</option>
                  </select></label
                ><label
                  >读者揭示章节<input
                    v-model="form.revealChapter"
                    type="number"
                    min="1"
                    placeholder="未设定则不提前揭示"
                    @input="changed"
                /></label>
                <p v-if="form.data?.evidence" class="small muted">
                  原文依据：{{ form.data.evidence
                  }}<template v-if="form.data.sourceChapter">
                    · 来源章节：{{ form.data.sourceChapter }}</template
                  >
                </p>
                <p class="small muted">故事时间早，不等于读者可以提前知道。</p>
              </fieldset>
              <div class="property-actions">
                <button
                  class="btn primary"
                  :disabled="
                    !editable ||
                    busy ||
                    autoSaving ||
                    (!dirty && selected.id !== 0) ||
                    !String(form.name || '').trim()
                  "
                  :aria-busy="busy || autoSaving"
                >
                  <Check v-if="!dirty && selected.id !== 0 && !busy && !autoSaving" :size="15" />
                  <Save v-else :size="15" />
                  {{
                    busy || autoSaving
                      ? '保存中…'
                      : !dirty && selected.id !== 0
                        ? '已保存'
                        : '立即保存'
                  }}</button
                ><button
                  v-if="selected.id && editable && !selectedRow?.deleted"
                  type="button"
                  class="icon danger-text"
                  aria-label="删除当前资料"
                  @click="remove"
                >
                  <Trash2 :size="16" />
                </button>
              </div>
              <p class="small muted" aria-live="polite">
                {{
                  busy || autoSaving
                    ? '正在同步当前资料，请稍候。'
                    : !dirty && selected.id !== 0
                      ? '所有修改已保存。继续编辑后会自动保存。'
                      : '停止编辑约 0.8 秒后自动保存，也可以点击立即保存。'
                }}
              </p>
              <p v-if="saving.includes('失败')" class="notice">
                内容已保留为本地草稿。请检查必填项、关联资料与版本后重试。
              </p>
            </form></template
          >
          <div v-else class="properties-empty">
            <Network :size="34" />
            <h3>每个细节，都有位置</h3>
            <p>选择图中节点或左侧资料，查看与编辑人物、事件和关系。</p>
            <small>拖动端口创建连接<br />双击画布新建人物<br />Ctrl / Cmd + Z 撤销</small>
          </div>
        </aside>
      </div>
    </main>
    <div v-else class="empty">正在打开世界…</div>
    <ExtractionDialog
      v-if="dialog === 'extraction'"
      :project="pid"
      @close="dialog = ''"
      @imported="
        dialog = '';
        load();
        selected = null;
        dirty = false;
      "
    />
    <Dialog v-if="dialog === 'validate'" title="设定一致性检查" wide @close="dialog = ''"
      ><p v-if="!issues.length" class="notice">当前结构与已确定顺序没有发现冲突。</p>
      <button
        v-for="(issue, i) in issues"
        :key="i"
        class="issue-row"
        @click="
          issue.kind &&
          issue.entityId &&
          (choose(String(issue.kind), Number(issue.entityId)), (dialog = ''))
        "
      >
        <span class="pill" :class="String(issue.severity).toLowerCase()">{{ issue.severity }}</span>
        <div>
          <strong>{{ issue.ruleId }}</strong>
          <p>{{ issue.message }}</p>
        </div>
        <span>↗</span>
      </button></Dialog
    ><Dialog
      v-if="dialog === 'history'"
      title="世界的恢复点"
      wide
      :busy="busy"
      @close="!busy && (dialog = '')"
      ><div class="panel-heading">
        <p class="muted">恢复会形成新版本，并自动保存恢复前状态。</p>
        <button class="btn primary" @click="snapshot">创建恢复点</button>
      </div>
      <div v-for="s in snapshots" :key="s.id" class="history-row">
        <div>
          <strong>{{ s.name }}</strong
          ><small>内容版本 {{ s.revision }} · {{ s.created_at }}</small>
        </div>
        <button
          class="btn small"
          :disabled="graph?.project.rights?.role !== 'ADMIN' || busy"
          @click="restoreSnapshot(s)"
        >
          恢复</button
        ><button class="text-btn" :disabled="busy" @click="deleteSnapshot(s)">删除恢复点</button>
      </div>
      <p v-if="!snapshots.length" class="empty">还没有恢复点。</p></Dialog
    ><Dialog
      v-if="dialog === 'project'"
      title="世界基础设定"
      :busy="busy"
      @close="!busy && (dialog = '')"
      ><div class="form">
        <label>项目名称<input v-model="projectForm.title" /></label
        ><label
          >故事题材<select v-model="projectForm.genreId">
            <option value="" :disabled="!projectForm.genre">
              {{ projectForm.genre ? `保留原题材：${projectForm.genre}` : '请选择故事题材' }}
            </option>
            <option v-for="g in projectGenres" :key="g.id" :value="g.id">
              {{ g.name }}
            </option></select
          ><small v-if="!projectGenres.length" class="muted"
            >暂无可选题材，请联系管理员维护。</small
          ></label
        ><label>世界概述<textarea v-model="projectForm.overview" rows="4" /></label
        ><label>文风<textarea v-model="projectForm.style" rows="2" /></label
        ><label>创作硬约束<textarea v-model="projectForm.constraints" rows="3" /></label>
      </div>
      <template #footer
        ><button
          class="btn primary"
          :disabled="!editable || busy || (!projectForm.genreId && !projectForm.genre)"
          @click="saveProject"
        >
          保存基础设定
        </button></template
      ></Dialog
    ><Dialog v-if="dialog === 'permissions'" title="项目成员授权" @close="dialog = ''"
      ><p class="muted">先在空间页面邀请成员，再授予项目权限。</p>
      <div class="form">
        <label
          >成员<select v-model.number="permission.userId">
            <option :value="0">选择成员</option>
            <option v-for="m in permissionMembers" :key="m.user_id" :value="m.user_id">
              {{ m.name }} · {{ m.email }}
            </option>
          </select></label
        ><label
          >项目角色<select v-model="permission.role">
            <option value="ADMIN">项目管理员</option>
            <option value="EDITOR">编辑者</option>
            <option value="VIEWER">查看者</option>
          </select></label
        ><label class="check"
          ><input v-model="permission.canExport" type="checkbox" />允许导出</label
        ><label class="check"
          ><input v-model="permission.canSecrets" type="checkbox" />允许查看作者秘密</label
        >
      </div>
      <section>
        <div v-for="g in grants" :key="g.user_id" class="history-row">
          <div>
            <strong>{{
              permissionMembers.find((m) => m.user_id === g.user_id)?.name || g.user_id
            }}</strong
            ><small
              >{{ g.role }} · {{ g.can_export ? '可导出' : '不可导出' }} ·
              {{ g.can_secrets ? '可查看秘密' : '不含秘密' }}</small
            >
          </div>
          <button class="text-btn" @click="revokeGrant(Number(g.user_id))">撤销授权</button>
        </div>
      </section>
      <p class="muted small">空间所有者和管理员始终可以管理本空间项目。</p>
      <template #footer
        ><button class="btn primary" :disabled="!permission.userId" @click="grant">
          保存授权
        </button></template
      ></Dialog
    ><Dialog
      v-if="dialog === 'export'"
      title="把这个世界，交给下一段创作"
      wide
      :busy="busy"
      @close="!busy && (dialog = '')"
      ><div class="export-grid">
        <div class="form">
          <label
            >导出格式<select v-model="exportForm.format">
              <option value="MARKDOWN">Markdown 提示词</option>
              <option value="COMPACT">精简提示词</option>
              <option value="JSON">结构化 JSON</option>
              <option v-if="secretAllowed" value="BACKUP">作者全量备份</option>
            </select></label
          ><label
            >导出视角<select v-model="exportForm.perspective">
              <option value="READER">读者已知（按章节）</option>
              <option value="AUTHOR">作者规划</option>
            </select></label
          >
          <div class="two-fields">
            <label
              >起始揭示章节<input
                v-model.number="exportForm.chapterFrom"
                type="number"
                min="1" /></label
            ><label
              >截至章节<input v-model.number="exportForm.chapterTo" type="number" min="1"
            /></label>
          </div>
          <label
            >人物范围<select v-model="exportForm.characterIds" multiple>
              <option v-for="r in activeRows('characters')" :key="r.id" :value="r.id">
                {{ r.name }}
              </option></select
            ><small>不选择表示全部可见人物。</small></label
          ><label
            >关系层数<select v-model.number="exportForm.relationDepth">
              <option :value="0">仅所选人物</option>
              <option :value="1">一层关系</option>
              <option :value="2">两层关系</option>
            </select></label
          >
          <div class="two-fields">
            <label>故事起点序号<input v-model="exportForm.storyFrom" type="number" /></label
            ><label>故事截止序号<input v-model="exportForm.storyTo" type="number" /></label>
          </div>
          <label
            >剧情支线<input v-model="exportForm.branch" placeholder="不填写表示全部支线" /></label
          ><label
            >事件范围<select v-model="exportForm.eventIds" multiple>
              <option v-for="r in activeRows('events')" :key="r.id" :value="r.id">
                {{ r.name }}
              </option></select
            ><small>不选择表示全部可见事件。</small></label
          >
          <label class="check"
            ><input
              v-model="exportForm.includeSecrets"
              type="checkbox"
              :disabled="!secretAllowed"
            />包含当前视角可见的秘密</label
          ><label
            >本次写作任务<textarea
              v-model="exportForm.writingTask"
              rows="3"
              placeholder="描述任务，不会调用AI。"
            /></label
          ><button class="btn primary" :disabled="busy" @click="exportPreview">生成预览</button>
        </div>
        <section class="export-preview">
          <div class="panel-heading">
            <strong>设定包预览</strong><small>{{ preview?.characters || 0 }} 字符</small>
          </div>
          <p v-if="preview?.containsUnrevealed" class="notice">
            作者模式可能包含未揭示秘密，请确认用途。
          </p>
          <pre v-if="preview">{{ preview.content }}</pre>
          <div v-else class="empty">先选择范围，再生成确定性的设定包。</div>
        </section>
      </div>
      <template #footer
        ><button class="btn" :disabled="!preview || busy" @click="copyExport">复制提示词</button
        ><button class="btn primary" :disabled="!preview || busy" @click="downloadExport">
          下载文件
        </button></template
      ></Dialog
    >
  </div>
</template>

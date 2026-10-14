<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { Plus, ArrowUpRight, Network, FileUp, Search, Trash2, Copy } from 'lucide-vue-next';
import { useSession } from './store';
import { request } from './api';
import type { Project } from './types';
import Dialog from './Dialog.vue';
const session = useSession(),
  router = useRouter(),
  projects = ref<Project[]>([]),
  query = ref(''),
  dialog = ref(''),
  title = ref(''),
  genre = ref(''),
  genres = ref<{ id: number; name: string }[]>([]),
  overview = ref(''),
  busy = ref(false),
  target = ref<Project | null>(null),
  confirmName = ref(''),
  loading = ref(true),
  importPack = ref<Record<string, unknown> | null>(null),
  importInfo = ref('');
const filtered = computed(() =>
  projects.value.filter((p) => (p.title + p.genre).includes(query.value)),
);
async function load() {
  loading.value = true;
  try {
    genres.value = await request('/genres');
    projects.value = await request<Project[]>(`/tenants/${session.tenantId}/projects`);
  } catch (e) {
    session.notify((e as Error).message, 'error');
  } finally {
    loading.value = false;
  }
}
onMounted(load);
async function openCreate() {
  try {
    genres.value = await request('/genres');
    title.value = '';
    genre.value = '';
    overview.value = '';
    dialog.value = 'create';
  } catch (e) {
    session.notify((e as Error).message, 'error');
  }
}
async function save() {
  busy.value = true;
  try {
    const p = await request<Project>(`/tenants/${session.tenantId}/projects`, 'POST', {
      title: title.value,
      genreId: Number(genre.value),
      overview: overview.value,
    });
    dialog.value = '';
    await load();
    router.push(`/projects/${p.id}/editor`);
  } catch (e) {
    session.notify((e as Error).message, 'error');
  } finally {
    busy.value = false;
  }
}
async function remove() {
  if (!target.value || confirmName.value !== target.value.title) return;
  busy.value = true;
  try {
    await request(`/projects/${target.value.id}`, 'DELETE');
    dialog.value = '';
    await load();
    session.notify('项目及关联设定、快照、布局已永久删除');
  } catch (e) {
    session.notify((e as Error).message, 'error');
  } finally {
    busy.value = false;
  }
}
async function copy(p: Project) {
  try {
    await request(`/projects/${p.id}/copy`, 'POST', {});
    await load();
    session.notify('已复制为独立项目');
  } catch (e) {
    session.notify((e as Error).message, 'error');
  }
}
async function pick(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0];
  if (!file) return;
  try {
    if (file.size > 10000000) throw new Error('文件超过10MB，请拆分');
    importPack.value = JSON.parse(await file.text());
    const result = await request('/imports/preview', 'POST', importPack.value);
    importInfo.value = `${result.title} · ${result.entities} 条资料${result.partial ? ' · 部分设定包' : ' · 作者全量包'}`;
    dialog.value = 'import';
  } catch (e) {
    session.notify((e as Error).message, 'error');
  }
}
async function importNow() {
  busy.value = true;
  try {
    await request(`/tenants/${session.tenantId}/imports`, 'POST', importPack.value);
    dialog.value = '';
    await load();
    session.notify('设定包已导入为新项目');
  } catch (e) {
    session.notify((e as Error).message, 'error');
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <main class="page">
    <header class="page-heading">
      <div>
        <span class="eyebrow">BUILD YOUR STORY WORLD</span>
        <h1>我的世界<span class="heading-dot">.</span></h1>
        <p class="muted">把零散的灵感，连接成一个完整的故事。</p>
      </div>
      <div class="actions">
        <label class="btn"
          ><FileUp :size="16" />导入设定<input
            hidden
            type="file"
            accept="application/json,.json"
            @change="pick" /></label
        ><button class="btn primary" @click="openCreate"><Plus :size="17" />新建世界</button>
      </div>
    </header>
    <section class="hero-banner">
      <div>
        <span class="eyebrow">YOUR STORY, YOUR RULES</span>
        <h2>先搭建世界，<br />再让故事<span>自由生长。</span></h2>
        <p>人物的来处、事件的因果、还未揭开的秘密。<br />在这里，每一条线索都拥有自己的位置。</p>
        <button class="btn dark" @click="openCreate">
          开启新的故事 <ArrowUpRight :size="16" />
        </button>
      </div>
      <div class="hero-map" aria-hidden="true">
        <svg viewBox="0 0 430 240">
          <path d="M70 85L210 45L340 130L215 200L70 85M210 45L215 200M70 85L340 130" />
        </svg>
        <div class="hero-node one">人物<span>CHARACTER</span></div>
        <div class="hero-node two">事件<span>EVENT</span></div>
        <div class="hero-node three">秘密<span>SECRET</span></div>
        <div class="hero-node four">关系<span>RELATION</span></div>
        <span class="map-star">✦</span>
      </div>
    </section>
    <div class="project-section">
      <div>
        <h2>
          你的小说世界 <small>{{ projects.length }}</small>
        </h2>
        <p class="muted">{{ session.tenant?.name }} · 所有设定由你掌握</p>
      </div>
      <label class="search"
        ><Search :size="16" /><input v-model="query" placeholder="搜索名称或题材"
      /></label>
    </div>
    <div v-if="loading" class="empty">正在整理创作空间…</div>
    <section v-else class="project-grid">
      <article v-for="(p, index) in filtered" :key="p.id" class="project-card">
        <button
          class="project-cover"
          :class="`cover-${index % 4}`"
          @click="router.push(`/projects/${p.id}/editor`)"
        >
          <span class="genre">{{ p.genre || '未分类' }}</span>
          <div class="cover-pattern"><i></i><i></i><i></i></div>
          <h3>{{ p.title }}</h3>
          <ArrowUpRight :size="22" />
        </button>
        <div class="project-info">
          <h3>{{ p.title }}</h3>
          <p>{{ p.overview || '一个等待你定义的世界。' }}</p>
          <div class="project-meta">
            <span>设定版本 {{ p.revision }}</span>
            <div class="actions">
              <button class="icon" :aria-label="`复制${p.title}`" @click="copy(p)">
                <Copy :size="15" /></button
              ><button
                class="icon"
                :aria-label="`删除${p.title}`"
                @click="
                  target = p;
                  confirmName = '';
                  dialog = 'delete';
                "
              >
                <Trash2 :size="15" />
              </button>
            </div>
          </div>
          <button class="project-enter" @click="router.push(`/projects/${p.id}/editor`)">
            进入世界编辑器 <ArrowUpRight :size="16" />
          </button>
        </div>
      </article>
      <button class="new-world" @click="openCreate">
        <Network :size="30" />
        <h3>下一个世界，等你定义</h3>
        <p>人物、关系与剧情，从这里开始</p>
        <span>创建小说项目 ＋</span>
      </button>
    </section>
    <div v-if="!filtered.length && query" class="empty">没有匹配的世界，换个关键词试试。</div>
    <footer class="page-foot">
      NovelGraph · 把灵感连接成故事<span>作者主导，事实可追溯。</span>
    </footer>
    <Dialog
      v-if="dialog === 'create'"
      title="开启一个新的世界"
      :busy="busy"
      @close="!busy && (dialog = '')"
      ><form class="form" @submit.prevent="save">
        <label
          >小说名称<input
            v-model="title"
            required
            maxlength="200"
            placeholder="给这个世界一个名字" /></label
        ><label
          >故事题材<select v-model="genre" required>
            <option disabled value="">请选择故事题材</option>
            <option v-for="g in genres" :key="g.id" :value="String(g.id)">
              {{ g.name }}
            </option></select
          ><small v-if="!genres.length">暂无可选题材，请联系管理员添加。</small></label
        ><label
          >世界概述<textarea
            v-model="overview"
            rows="4"
            placeholder="世界的基础规则、故事起点或核心冲突。"
          /></label
        ><button class="btn primary block" :disabled="busy || !title.trim() || !genre">
          {{ busy ? '正在创建…' : '创建并进入编辑器' }}
        </button>
      </form></Dialog
    ><Dialog
      v-if="dialog === 'delete'"
      title="永久删除这个世界"
      :busy="busy"
      @close="!busy && (dialog = '')"
      ><p>「{{ target?.title }}」的人物、事件、关系、秘密、布局和快照都会删除，无法恢复。</p>
      <label>输入项目名称确认<input v-model="confirmName" :placeholder="target?.title" /></label
      ><template #footer
        ><button class="btn" :disabled="busy" @click="dialog = ''">取消</button
        ><button
          class="btn danger"
          :disabled="busy || confirmName !== target?.title"
          @click="remove"
        >
          永久删除
        </button></template
      ></Dialog
    ><Dialog
      v-if="dialog === 'import'"
      title="导入为新的小说世界"
      :busy="busy"
      @close="!busy && (dialog = '')"
      ><p>{{ importInfo }}</p>
      <p class="muted">资料关联将重新映射，已有项目不会被覆盖。</p>
      <template #footer
        ><button class="btn primary" :disabled="busy" @click="importNow">确认导入</button></template
      ></Dialog
    >
  </main>
</template>

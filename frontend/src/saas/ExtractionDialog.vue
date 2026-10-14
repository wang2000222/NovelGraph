<script setup lang="ts">
import { ref, watch } from 'vue';
import Dialog from './Dialog.vue';
import { request } from './api';
import { useSession } from './store';
const props = defineProps<{ project: number }>();
const emit = defineEmits<{ close: []; imported: [] }>();
const session = useSession();
const step = ref('prompt'),
  text = ref(''),
  chapter = ref<number | null>(null),
  instructions = ref(''),
  prompt = ref(''),
  response = ref(''),
  busy = ref(false);
type Item = Record<string, unknown> & {
  编号: string;
  名称: string;
  说明?: string;
  属性?: Record<string, unknown>;
};
type Preview = {
  counts: Record<string, number>;
  total: number;
  items?: Item[];
  warnings?: unknown[];
  revision: number;
  duplicate: boolean;
};
const preview = ref<Preview | null>(null);
watch(response, () => {
  preview.value = null;
});
watch([text, chapter, instructions], () => {
  prompt.value = '';
});
async function generate() {
  busy.value = true;
  try {
    const result = await request<{ prompt: string }>(
      `/projects/${props.project}/extractions/prompt`,
      'POST',
      { text: text.value, chapter: chapter.value, instructions: instructions.value },
    );
    prompt.value = result.prompt;
  } catch (e) {
    session.notify((e as Error).message, 'error');
  } finally {
    busy.value = false;
  }
}
async function copy() {
  try {
    await navigator.clipboard.writeText(prompt.value);
    session.notify('已复制完整提示词，请粘贴到AI对话中');
  } catch {
    session.notify('复制失败，请手动选中提示词复制', 'error');
  }
}
async function upload(event: Event) {
  const input = event.target as HTMLInputElement,
    file = input.files?.[0];
  if (!file) return;
  try {
    if (file.size > 2000000) throw new Error('回答文件超过2MB，请分批导入');
    response.value = await file.text();
  } catch (e) {
    session.notify((e as Error).message, 'error');
  } finally {
    input.value = '';
  }
}
async function check() {
  busy.value = true;
  try {
    preview.value = await request<Preview>(
      `/projects/${props.project}/extractions/preview`,
      'POST',
      { response: response.value },
      120000,
    );
  } catch (e) {
    preview.value = null;
    session.notify((e as Error).message, 'error');
  } finally {
    busy.value = false;
  }
}
async function commit() {
  if (!preview.value || preview.value.duplicate) return;
  busy.value = true;
  try {
    const result = await request<Preview>(
      `/projects/${props.project}/extractions/commit`,
      'POST',
      {
        response: response.value,
        baseRevision: preview.value.revision,
      },
      120000,
    );
    session.notify(
      result.duplicate ? '这批资料已经导入，未重复创建' : `已导入 ${result.total} 条资料到当前小说`,
    );
    emit('imported');
  } catch (e) {
    preview.value = null;
    session.notify((e as Error).message, 'error');
  } finally {
    busy.value = false;
  }
}
function close() {
  if (busy.value) return;
  if (
    (text.value || response.value || prompt.value) &&
    !confirm('关闭后本次输入和未导入的回答不会保留，确认关闭吗？')
  )
    return;
  emit('close');
}
function details(item: Item) {
  return Object.entries(item).filter(
    ([key, value]) =>
      !['编号', '名称', '说明', '属性'].includes(key) && value !== null && value !== '',
  );
}
function show(value: unknown) {
  return typeof value === 'object' ? JSON.stringify(value) : String(value ?? '');
}
</script>
<template>
  <Dialog title="小说文本提取与AI回答导入" wide :busy="busy" @close="close">
    <div class="tabs">
      <button :disabled="busy" :class="{ active: step === 'prompt' }" @click="step = 'prompt'">
        1 · 生成提取提示词</button
      ><button :disabled="busy" :class="{ active: step === 'import' }" @click="step = 'import'">
        2 · 导入AI回答
      </button>
    </div>
    <section v-if="step === 'prompt'" class="extract-section">
      <p class="muted">
        填写小说文本，复制生成的完整提示词交给任意AI。AI按中文JSON模板回答后，切换到“导入AI回答”。
      </p>
      <form class="form" @submit.prevent="generate">
        <label
          >来源章节（可选）<input
            v-model.number="chapter"
            type="number"
            min="1"
            step="1"
            :disabled="busy"
            placeholder="例如 2"
        /></label>
        <label
          >提取要求（可选）<textarea
            v-model="instructions"
            rows="2"
            :disabled="busy"
            placeholder="例如：提取主角的人物目标、旧井事件及导致的关系变化，不推测后续剧情。"
          />
        </label>
        <label
          >小说文本<textarea
            v-model="text"
            rows="9"
            required
            :disabled="busy"
            placeholder="粘贴需要整理的小说原文…"
          />
        </label>
        <button class="btn primary" :disabled="busy || !text.trim()">
          {{ busy ? '正在生成…' : '生成提取提示词' }}
        </button>
      </form>
      <template v-if="prompt"
        ><label
          >完整提示词（含原文与回答模板）<textarea :value="prompt" readonly rows="10" />
        </label>
        <div class="actions">
          <button class="btn" @click="copy">复制完整提示词</button
          ><button class="btn primary" @click="step = 'import'">下一步：粘贴AI回答</button>
        </div></template
      >
    </section>
    <section v-else class="extract-section">
      <p class="muted">
        粘贴或上传AI按模板生成的JSON回答。校验通过后会显示预览，点击确认才会写入当前小说。已有资料只允许引用，导入不会覆盖它们。
      </p>
      <label class="btn"
        >上传AI回答<input
          type="file"
          hidden
          accept=".json,.txt,application/json,text/plain"
          :disabled="busy"
          @change="upload"
      /></label>
      <label
        >AI回答<textarea
          v-model="response"
          rows="12"
          :disabled="busy"
          placeholder="粘贴包含版本、批次编号和各类资料数组的JSON，可包含json代码围栏。"
        />
      </label>
      <button class="btn" :disabled="busy || !response.trim()" @click="check">
        {{ busy ? '正在处理…' : '解析并校验预览' }}
      </button>
      <section v-if="preview" class="panel extraction-preview">
        <p v-if="preview.duplicate" class="notice">这批资料已导入，不能重复导入。</p>
        <h3>导入预览 · {{ preview.total }} 条资料</h3>
        <div class="actions">
          <template v-for="(count, name) in preview.counts" :key="name"
            ><span v-if="count" class="pill">{{ name }} {{ count }}</span></template
          >
        </div>
        <div class="extraction-items">
          <article v-for="item in preview.items || []" :key="item.编号">
            <strong>{{ item.名称 }}</strong
            ><small class="muted"> · {{ item.编号 }}</small>
            <p v-if="item.说明">{{ item.说明 }}</p>
            <p v-for="[name, value] in details(item)" :key="name">
              <span class="muted">{{ name }}：</span>{{ show(value) }}
            </p>
            <p v-for="(value, name) in item.属性" :key="name">
              <span class="muted">{{ name }}：</span>{{ show(value) }}
            </p>
          </article>
        </div>
        <section v-if="preview.warnings?.length">
          <h4>待作者确认（不会入库）</h4>
          <p v-for="(warning, index) in preview.warnings" :key="index">{{ show(warning) }}</p>
        </section>
        <p class="small muted">
          请核对AI提取是否符合原文。缺失引用、循环因果、时间冲突或超出额度会阻止整批导入。
        </p>
        <button
          class="btn primary"
          :disabled="busy || preview.duplicate || !preview.total"
          @click="commit"
        >
          {{ busy ? '正在导入…' : '确认导入当前小说' }}
        </button>
      </section>
    </section>
  </Dialog>
</template>
<style scoped>
.extract-section {
  display: grid;
  gap: 16px;
  margin-top: 18px;
}
.extract-section label {
  display: grid;
  gap: 8px;
}
.extract-section textarea {
  width: 100%;
  resize: vertical;
  line-height: 1.7;
}
.extraction-items {
  max-height: 340px;
  overflow: auto;
  margin: 16px 0;
}
.extraction-items article {
  padding: 12px 0;
  border-bottom: 1px solid var(--line);
}
.extraction-items p {
  margin: 5px 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.extraction-preview h3 {
  margin-bottom: 12px;
}
</style>

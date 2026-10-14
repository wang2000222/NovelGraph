<script setup lang="ts">
import { ref, onMounted, watch } from 'vue';
import { useSession } from './store';
import { request } from './api';
import Dialog from './Dialog.vue';
const s = useSession(),
  tab = ref('users'),
  list = ref<Record<string, unknown>[]>([]),
  busy = ref(false),
  email = ref(s.supportEmail),
  editing = ref<Record<string, unknown> | null>(null),
  price = ref(0),
  planName = ref(''),
  limitText = ref(''),
  genreDialog = ref(false),
  genreId = ref<number | null>(null),
  genreName = ref(''),
  genreDescription = ref(''),
  genreSort = ref(0),
  genreEnabled = ref(true);
const tabs: Record<string, string> = {
  users: '用户账号',
  tenants: '创作空间',
  orders: '人工订单',
  plans: '套餐配置',
  genres: '故事题材',
  audit: '操作记录',
  mail: '邮件队列',
};
async function load() {
  if (s.user?.platformRole !== 'ADMIN') return;
  try {
    list.value = await request<Record<string, unknown>[]>(`/admin/${tab.value}`);
  } catch (e) {
    s.notify((e as Error).message, 'error');
  }
}
onMounted(load);
watch(tab, load);
async function update(id: number, body: unknown, resource = tab.value) {
  busy.value = true;
  try {
    await request(`/admin/${resource}/${id}`, 'PATCH', body);
    await load();
    s.notify('操作已生效并记录审计');
  } catch (e) {
    s.notify((e as Error).message, 'error');
  } finally {
    busy.value = false;
  }
}
function edit(p: Record<string, unknown>) {
  editing.value = p;
  price.value = Number(p.monthly_price);
  planName.value = String(p.name);
  limitText.value =
    typeof p.limits === 'string'
      ? JSON.stringify(JSON.parse(p.limits), null, 2)
      : JSON.stringify(p.limits, null, 2);
}
async function savePlan() {
  try {
    const limits = JSON.parse(limitText.value);
    await update(
      Number(editing.value?.id),
      { name: planName.value, monthlyPrice: price.value, limits },
      'plans',
    );
    editing.value = null;
    await s.public();
  } catch (e) {
    s.notify((e as Error).message, 'error');
  }
}
function editGenre(row?: Record<string, unknown>) {
  genreId.value = row ? Number(row.id) : null;
  genreName.value = String(row?.name || '');
  genreDescription.value = String(row?.description || '');
  genreSort.value = Number(row?.sortOrder || 0);
  genreEnabled.value = !row || Number(row.enabled) === 1;
  genreDialog.value = true;
}
async function saveGenre() {
  busy.value = true;
  try {
    await request(
      genreId.value ? `/admin/genres/${genreId.value}` : '/admin/genres',
      genreId.value ? 'PATCH' : 'POST',
      {
        name: genreName.value,
        description: genreDescription.value,
        sortOrder: genreSort.value,
        enabled: genreEnabled.value ? 1 : 0,
      },
    );
    genreDialog.value = false;
    await load();
    s.notify('故事题材已保存');
  } catch (e) {
    s.notify((e as Error).message, 'error');
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <main class="page">
    <header class="page-heading">
      <div>
        <span class="eyebrow">PLATFORM OPERATIONS</span>
        <h1>运营后台<span class="heading-dot">.</span></h1>
        <p class="muted">管理账号、空间、订阅和运行记录。</p>
      </div>
      <button class="btn" @click="load">刷新数据</button>
    </header>
    <p v-if="s.user?.platformRole !== 'ADMIN'" class="notice">需要平台管理员权限。</p>
    <template v-else
      ><div class="tabs">
        <button v-for="(label, k) in tabs" :key="k" :class="{ active: tab === k }" @click="tab = k">
          {{ label }}
        </button>
      </div>
      <button v-if="tab === 'genres'" class="btn primary" @click="editGenre()">新增故事题材</button>
      <section class="panel">
        <div class="table-scroll">
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>对象</th>
                <th>状态 / 内容</th>
                <th>时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in list" :key="Number(r.id)">
                <td>{{ r.id }}</td>
                <td>
                  {{ r.email || r.name || r.plan_code || r.action
                  }}<small>{{ r.title || r.subject || r.code }}</small>
                </td>
                <td>
                  <span class="pill">{{
                    tab === 'genres'
                      ? Number(r.enabled) === 1
                        ? '启用'
                        : '停用'
                      : r.status || r.role || r.action || '—'
                  }}</span
                  ><small v-if="tab === 'orders'"
                    >空间 #{{ r.tenant_id }} · ¥{{ r.amount }} · {{ r.months }} 月</small
                  ><small v-if="tab === 'mail'"
                    >尝试 {{ r.attempt }} 次 · {{ r.error_code || '无错误' }}</small
                  ><small v-if="tab === 'audit'"
                    >用户 #{{ r.user_id }} · 空间 #{{ r.tenant_id }} · 目标 #{{
                      r.target_id
                    }}</small
                  >
                </td>
                <td>{{ tab === 'genres' ? `排序 ${r.sortOrder}` : r.created_at }}</td>
                <td>
                  <button
                    v-if="tab === 'users' && r.id !== s.user.id"
                    class="btn small"
                    :disabled="busy"
                    @click="
                      update(Number(r.id), {
                        status: r.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE',
                      })
                    "
                  >
                    {{ r.status === 'ACTIVE' ? '禁用' : '启用' }}</button
                  ><button
                    v-if="tab === 'tenants'"
                    class="btn small"
                    :disabled="busy"
                    @click="
                      update(Number(r.id), { status: r.status === 'ACTIVE' ? 'FROZEN' : 'ACTIVE' })
                    "
                  >
                    {{ r.status === 'ACTIVE' ? '冻结' : '解冻' }}
                  </button>
                  <div v-if="tab === 'orders' && r.status === 'PENDING'" class="actions">
                    <button
                      class="btn small primary"
                      :disabled="busy"
                      @click="update(Number(r.id), { status: 'PAID' })"
                    >
                      确认开通</button
                    ><button
                      class="btn small"
                      :disabled="busy"
                      @click="update(Number(r.id), { status: 'CANCELLED' })"
                    >
                      取消
                    </button>
                  </div>
                  <button v-if="tab === 'genres'" class="btn small" @click="editGenre(r)">
                    编辑题材</button
                  ><button v-if="tab === 'plans'" class="btn small" @click="edit(r)">
                    修改套餐
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-if="!list.length" class="empty">暂无记录。</p>
      </section>
      <section class="panel">
        <h2>客服配置</h2>
        <form class="actions" @submit.prevent="update(0, { email }, 'settings')">
          <input v-model="email" type="email" required aria-label="客服邮箱" /><button
            class="btn primary"
            :disabled="busy"
          >
            保存客服邮箱
          </button>
        </form>
      </section></template
    ><Dialog v-if="editing" title="编辑套餐" :busy="busy" @close="!busy && (editing = null)"
      ><form class="form" @submit.prevent="savePlan">
        <label>套餐名称<input v-model="planName" required /></label
        ><label
          >月价<input v-model.number="price" type="number" min="0" step="0.01" required /></label
        ><label>额度 JSON<textarea v-model="limitText" rows="8" required /></label>
        <p class="muted">已付款订阅保留订单中的权益快照。新订单使用更新后的权益。</p>
        <button class="btn primary" :disabled="busy">保存套餐</button>
      </form></Dialog
    >
    <Dialog
      v-if="genreDialog"
      :title="genreId ? '编辑故事题材' : '新增故事题材'"
      :busy="busy"
      @close="!busy && (genreDialog = false)"
      ><form class="form" @submit.prevent="saveGenre">
        <label>题材名称<input v-model="genreName" required maxlength="50" /></label
        ><label>题材说明<textarea v-model="genreDescription" maxlength="500" rows="3" /></label
        ><label
          >显示排序<input
            v-model.number="genreSort"
            required
            type="number"
            min="0"
            max="99999"
            step="1" /></label
        ><label><input v-model="genreEnabled" type="checkbox" />启用，允许创建项目时选择</label>
        <p class="muted">停用后不再出现在下拉框，已有作品的题材保持原样。</p>
        <button class="btn primary" :disabled="busy || !genreName.trim()">保存题材</button>
      </form></Dialog
    >
  </main>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useSession } from './store';
import { request } from './api';
import Dialog from './Dialog.vue';
const s = useSession(),
  members = ref<Record<string, unknown>[]>([]),
  invites = ref<Record<string, unknown>[]>([]),
  name = ref(''),
  email = ref(''),
  role = ref('MEMBER'),
  token = ref(''),
  busy = ref(false),
  dialog = ref(''),
  tenantInfo = ref<Record<string, unknown>>({}),
  memberCanCreate = ref(false),
  transferUser = ref(0),
  confirmName = ref('');
const manager = computed(() => ['OWNER', 'ADMIN'].includes(s.tenant?.role || ''));
const labels: Record<string, string> = {
  OWNER: '所有者',
  ADMIN: '管理员',
  MEMBER: '成员',
  BILLING: '财务',
};
async function load() {
  const info = await request<Record<string, unknown>>(`/tenants/${s.tenantId}`);
  tenantInfo.value = info.tenant as Record<string, unknown>;
  memberCanCreate.value = Boolean(tenantInfo.value.member_can_create);
  if (!manager.value) return;
  try {
    const r = await request<{
      members: Record<string, unknown>[];
      invitations: Record<string, unknown>[];
    }>(`/tenants/${s.tenantId}/members`);
    members.value = r.members;
    invites.value = r.invitations;
  } catch (e) {
    s.notify((e as Error).message, 'error');
  }
}
onMounted(() => load().catch((e) => s.notify(e.message, 'error')));
async function spaceAction(action: string, body: unknown = {}) {
  busy.value = true;
  try {
    await request(`/tenants/${s.tenantId}/actions/${action}`, 'POST', body);
    dialog.value = '';
    await s.load();
    await load();
    s.notify('空间操作已完成');
  } catch (e) {
    s.notify((e as Error).message, 'error');
  } finally {
    busy.value = false;
  }
}
async function invitation(id: number, resend: boolean) {
  try {
    await request(
      `/tenants/${s.tenantId}/invitations/${id}${resend ? '/resend' : ''}`,
      resend ? 'POST' : 'DELETE',
    );
    await load();
    s.notify(resend ? '新邀请已加入邮件队列' : '邀请已撤销');
  } catch (e) {
    s.notify((e as Error).message, 'error');
  }
}
async function act(kind: string) {
  busy.value = true;
  try {
    if (kind === 'create') {
      const t = await request<{ id: number }>('/tenants', 'POST', { name: name.value });
      await s.load();
      s.tenantId = t.id;
      await load();
    } else if (kind === 'invite') {
      await request(`/tenants/${s.tenantId}/invitations`, 'POST', {
        email: email.value,
        role: role.value,
      });
      await load();
      s.notify('邀请已加入发送队列');
    } else {
      const r = await request<{ tenantId: number }>('/invitations/accept', 'POST', {
        token: token.value,
      });
      await s.load();
      s.tenantId = r.tenantId;
      await load();
      s.notify('已加入团队');
    }
    dialog.value = '';
  } catch (e) {
    s.notify((e as Error).message, 'error');
  } finally {
    busy.value = false;
  }
}
async function change(m: Record<string, unknown>, event: Event) {
  try {
    await request(`/tenants/${s.tenantId}/members/${m.user_id}`, 'PATCH', {
      role: (event.target as HTMLSelectElement).value,
    });
    await load();
  } catch (e) {
    s.notify((e as Error).message, 'error');
    await load();
  }
}
async function remove(m: Record<string, unknown>) {
  if (!confirm(`移除 ${m.name} 的空间访问和项目权限？`)) return;
  try {
    await request(`/tenants/${s.tenantId}/members/${m.user_id}`, 'DELETE');
    await load();
    s.notify('成员权限已撤销');
  } catch (e) {
    s.notify((e as Error).message, 'error');
  }
}
</script>
<template>
  <main class="page">
    <header class="page-heading">
      <div>
        <span class="eyebrow">CREATE TOGETHER</span>
        <h1>空间与成员<span class="heading-dot">.</span></h1>
        <p class="muted">成员共享一个创作空间，项目权限按需分配。</p>
      </div>
      <div class="actions">
        <button class="btn" @click="dialog = 'accept'">接受邀请</button
        ><button class="btn primary" @click="dialog = 'create'">创建团队空间</button>
      </div>
    </header>
    <section class="panel">
      <div class="panel-heading">
        <div>
          <h2>{{ s.tenant?.name }}</h2>
          <p class="muted">
            {{ s.tenant?.kind === 'TEAM' ? '团队' : '个人' }}空间 ·
            {{ labels[s.tenant?.role || ''] }}
          </p>
        </div>
        <button v-if="manager" class="btn primary" @click="dialog = 'invite'">邀请成员</button>
      </div>
      <p v-if="!manager" class="notice">成员列表由空间管理员管理。请联系管理员获取项目授权。</p>
      <div v-else class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>成员</th>
              <th>用户 ID</th>
              <th>邮箱</th>
              <th>空间角色</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="m in members" :key="Number(m.id)">
              <td>{{ m.name }}</td>
              <td>{{ m.user_id }}</td>
              <td>{{ m.email }}</td>
              <td>
                <select
                  v-if="s.tenant?.role === 'OWNER' && m.role !== 'OWNER'"
                  :value="m.role"
                  @change="change(m, $event)"
                >
                  <option value="MEMBER">成员</option>
                  <option value="ADMIN">管理员</option>
                  <option value="BILLING">财务</option></select
                ><span v-else>{{ labels[String(m.role)] }}</span>
              </td>
              <td>
                <button
                  v-if="m.role !== 'OWNER' && (s.tenant?.role === 'OWNER' || m.role === 'MEMBER')"
                  class="btn small danger-text"
                  @click="remove(m)"
                >
                  移除
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
    <section v-if="manager" class="panel">
      <h2>邀请记录</h2>
      <div v-for="i in invites" :key="Number(i.id)" class="history-row">
        <div>
          <strong>{{ i.email }}</strong
          ><small>{{ labels[String(i.role)] }} · 截止 {{ i.expires_at }}</small>
        </div>
        <div class="actions">
          <span class="pill">{{ i.status }}</span
          ><button
            v-if="i.status === 'PENDING'"
            class="btn small"
            @click="invitation(Number(i.id), true)"
          >
            重发</button
          ><button
            v-if="i.status === 'PENDING'"
            class="btn small"
            @click="invitation(Number(i.id), false)"
          >
            撤销
          </button>
        </div>
      </div>
      <p v-if="!invites.length" class="empty">尚未发送邀请。</p>
    </section>
    <section class="panel muted">
      所有者管理角色与账单；管理员管理成员和项目；财务只处理账单。普通成员需获得单独的项目编辑、导出和秘密资料权限。
    </section>
    <section class="panel">
      <h2>空间设置</h2>
      <div v-if="s.tenant?.role === 'OWNER'" class="form" style="margin-top: 20px">
        <label
          >空间名称<input
            :value="String(tenantInfo.name || '')"
            @input="tenantInfo.name = ($event.target as HTMLInputElement).value" /></label
        ><label class="check"
          ><input v-model="memberCanCreate" type="checkbox" />允许普通成员创建项目</label
        ><button
          class="btn"
          :disabled="busy"
          @click="spaceAction('settings', { name: tenantInfo.name, memberCanCreate })"
        >
          保存空间设置</button
        ><template v-if="s.tenant.kind === 'TEAM'"
          ><label
            >转移给成员<select v-model.number="transferUser">
              <option :value="0">选择接收方</option>
              <option
                v-for="m in members.filter((m) => m.user_id !== s.user?.id)"
                :key="Number(m.user_id)"
                :value="m.user_id"
              >
                {{ m.name }} · {{ m.email }}
              </option>
            </select></label
          ><button
            class="btn"
            :disabled="!transferUser || busy"
            @click="spaceAction('transfer', { userId: transferUser })"
          >
            申请转移所有权
          </button>
          <p v-if="tenantInfo.pending_owner_id" class="notice">
            等待用户 #{{ tenantInfo.pending_owner_id }} 在24小时内确认。<button
              class="text-btn"
              @click="spaceAction('cancel-transfer')"
            >
              撤销申请
            </button>
          </p>
          <button class="btn danger-text" @click="dialog = 'delete-space'">
            永久删除团队空间
          </button></template
        >
      </div>
      <button v-else class="btn" :disabled="busy" @click="spaceAction('leave')">
        退出当前空间
      </button>
      <div v-if="Number(tenantInfo.pending_owner_id) === s.user?.id" class="notice">
        所有者邀请你接管这个团队。<button
          class="btn primary small"
          @click="spaceAction('accept-transfer')"
        >
          确认接管</button
        ><button class="btn small" @click="spaceAction('cancel-transfer')">拒绝</button>
      </div>
    </section>
    <Dialog
      v-if="dialog === 'delete-space'"
      title="永久删除团队空间"
      :busy="busy"
      @close="!busy && (dialog = '')"
      ><p>空间内所有小说、成员授权、设定和恢复点都会永久删除，其他空间保持独立。</p>
      <label>输入空间名称<input v-model="confirmName" /></label
      ><template #footer
        ><button
          class="btn danger"
          :disabled="busy || confirmName !== s.tenant?.name"
          @click="spaceAction('delete', { confirmName })"
        >
          永久删除
        </button></template
      ></Dialog
    >
    <Dialog
      v-if="['create', 'invite', 'accept'].includes(dialog)"
      :title="
        dialog === 'create' ? '创建团队空间' : dialog === 'invite' ? '邀请创作伙伴' : '接受团队邀请'
      "
      :busy="busy"
      @close="!busy && (dialog = '')"
      ><form class="form" @submit.prevent="act(dialog)">
        <label v-if="dialog === 'create'"
          >空间名称<input v-model="name" required maxlength="100" /></label
        ><template v-if="dialog === 'invite'"
          ><label>受邀邮箱<input v-model="email" type="email" required /></label
          ><label
            >空间角色<select v-model="role">
              <option value="MEMBER">成员</option>
              <option v-if="s.tenant?.role === 'OWNER'" value="ADMIN">管理员</option>
              <option v-if="s.tenant?.role === 'OWNER'" value="BILLING">财务</option>
            </select></label
          >
          <p class="muted">
            邀请通过邮件发送，请确认平台已配置发件服务。待接受邀请会预占成员席位。
          </p></template
        ><label v-if="dialog === 'accept'"
          >邮件中的邀请代码<input v-model="token" required autocomplete="off" /></label
        ><button class="btn primary" :disabled="busy">{{ busy ? '处理中…' : '确认' }}</button>
      </form></Dialog
    >
  </main>
</template>

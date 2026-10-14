<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useSession } from './store';
import { request } from './api';
import Dialog from './Dialog.vue';
const s = useSession(),
  info = ref<Record<string, unknown>>({}),
  orders = ref<Record<string, unknown>[]>([]),
  limits = ref<Record<string, number>>({}),
  usage = ref<Record<string, number>>({}),
  plan = ref<Record<string, unknown> | null>(null),
  months = ref(1),
  busy = ref(false),
  key = ref('');
const canBill = computed(() => ['OWNER', 'ADMIN', 'BILLING'].includes(s.tenant?.role || ''));
const labels: Record<string, string> = {
  projects: '小说项目',
  seats: '成员席位',
  entities: '人物与事件',
  snapshots: '恢复点',
  associations: '关联与其他资料',
  storageBytes: '设定存储（字节）',
};
function parse(v: unknown): Record<string, number> {
  return typeof v === 'string' ? JSON.parse(v) : (v as Record<string, number>);
}
async function load() {
  try {
    const r = await request<Record<string, unknown>>(`/tenants/${s.tenantId}`);
    info.value = r.tenant as Record<string, unknown>;
    limits.value = parse(r.limits);
    usage.value = parse(r.usage);
    orders.value = r.orders as Record<string, unknown>[];
  } catch (e) {
    s.notify((e as Error).message, 'error');
  }
}
onMounted(load);
function choose(p: Record<string, unknown>) {
  plan.value = p;
  months.value = 1;
  key.value = crypto.randomUUID();
}
async function order() {
  busy.value = true;
  try {
    await request(`/tenants/${s.tenantId}/orders`, 'POST', {
      planCode: plan.value?.code,
      months: months.value,
      requestKey: key.value,
    });
    plan.value = null;
    await load();
    s.notify('订单已创建，请联系客服确认付款与人工开通');
  } catch (e) {
    s.notify((e as Error).message, 'error');
  } finally {
    busy.value = false;
  }
}
async function cancel(id: number) {
  try {
    await request(`/tenants/${s.tenantId}/orders/${id}/cancel`, 'POST');
    await load();
    s.notify('订单已取消');
  } catch (e) {
    s.notify((e as Error).message, 'error');
  }
}
</script>
<template>
  <main class="page">
    <header class="page-heading">
      <div>
        <span class="eyebrow">SPACE FOR YOUR IDEAS</span>
        <h1>套餐与账单<span class="heading-dot">.</span></h1>
        <p class="muted">
          {{ s.tenant?.name }} · 当前 {{ info.plan_code }}
          <span v-if="info.subscription_until">· 到期 {{ info.subscription_until }}</span>
        </p>
      </div>
    </header>
    <section class="usage-grid">
      <div v-for="(label, k) in labels" :key="k" class="panel">
        <small class="muted">{{ label }}</small>
        <h2>
          {{ usage[k] || 0 }} <small>/ {{ limits[k] || 0 }}</small>
        </h2>
        <progress :value="usage[k] || 0" :max="limits[k] || 1" />
      </div>
    </section>
    <div class="plan-grid">
      <section v-for="p in s.plans" :key="String(p.code)" class="panel plan-card">
        <span class="eyebrow">{{ p.code }}</span>
        <h2>{{ p.name }}</h2>
        <div class="plan-price">¥{{ p.monthly_price }}<small>/ 月</small></div>
        <ul>
          <li v-for="(label, k) in labels" :key="k">{{ label }}：{{ parse(p.limits)[k] }}</li>
        </ul>
        <button
          class="btn primary block"
          :disabled="!canBill || p.code === 'FREE'"
          @click="choose(p)"
        >
          {{ p.code === 'FREE' ? '基础免费权益' : '申请开通' }}
        </button>
      </section>
    </div>
    <p class="notice">
      当前采用人工确认付款和开通。申请订单不会自动扣款；请通过
      <a :href="`mailto:${s.supportEmail}`">{{ s.supportEmail }}</a>
      咨询。价格及权益可由平台管理员调整。
    </p>
    <section class="panel">
      <h2>空间账单</h2>
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>订单</th>
              <th>套餐</th>
              <th>周期</th>
              <th>金额</th>
              <th>状态</th>
              <th>创建时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="o in orders" :key="Number(o.id)">
              <td>#{{ o.id }}</td>
              <td>{{ o.plan_code }}</td>
              <td>{{ o.months }} 月</td>
              <td>¥{{ o.amount }}</td>
              <td>
                <span class="pill">{{
                  o.status === 'PAID'
                    ? '已开通'
                    : o.status === 'CANCELLED'
                      ? '已取消'
                      : '待人工确认'
                }}</span>
              </td>
              <td>
                {{ o.created_at }}
                <button
                  v-if="o.status === 'PENDING'"
                  class="text-btn"
                  @click="cancel(Number(o.id))"
                >
                  取消订单
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-if="!orders.length" class="empty">
        {{ canBill ? '还没有订单。' : '账单仅空间所有者、管理员和财务可见。' }}
      </p>
    </section>
    <Dialog v-if="plan" title="申请人工开通" :busy="busy" @close="!busy && (plan = null)"
      ><form class="form" @submit.prevent="order">
        <p>{{ plan.name }} · ¥{{ plan.monthly_price }} / 月</p>
        <label
          >订阅月数<input v-model.number="months" type="number" min="1" max="12" required /></label
        ><strong>订单金额 ¥{{ Number(plan.monthly_price) * months }}</strong>
        <p class="muted">开通以管理员确认订单为准，续订同套餐将延长有效期。</p>
        <button class="btn primary" :disabled="busy">创建订单</button>
      </form></Dialog
    >
  </main>
</template>

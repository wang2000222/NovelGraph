<script setup lang="ts">
import { onMounted, ref, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  LayoutGrid,
  Network,
  UsersRound,
  Layers,
  ShieldCheck,
  Mail,
  LogOut,
  Menu,
  ArrowUpRight,
} from 'lucide-vue-next';
import { useSession } from './saas/store';
import Dialog from './saas/Dialog.vue';
const session = useSession(),
  route = useRoute(),
  router = useRouter(),
  help = ref(false),
  mobile = ref(false);
const navigation = computed(() => [
  { path: '/', label: '我的世界', icon: LayoutGrid },
  { path: '/team', label: '空间与成员', icon: UsersRound },
  { path: '/plans', label: '套餐与账单', icon: Layers },
  { path: '/account', label: '账号与安全', icon: ShieldCheck },
  ...(session.user?.platformRole === 'ADMIN'
    ? [{ path: '/admin', label: '运营后台', icon: ShieldCheck }]
    : []),
]);
onMounted(async () => {
  await Promise.all([session.load(), session.public()]);
  if (!session.user && route.path !== '/login') router.replace('/login');
});
async function logout() {
  try {
    await session.logout();
    router.push('/login');
  } catch (e) {
    session.notify((e as Error).message, 'error');
  }
}
async function copyEmail() {
  try {
    await navigator.clipboard.writeText(session.supportEmail);
    session.notify('邮箱已复制');
  } catch {
    session.notify('请手动复制邮箱地址', 'error');
  }
}
</script>
<template>
  <div v-if="!session.ready" class="boot">✦ 正在打开创作空间…</div>
  <RouterView v-else-if="route.path === '/login'" />
  <div v-else-if="session.user" class="app-shell" :class="{ editor: route.name === 'editor' }">
    <aside class="sidebar" :class="{ open: mobile }">
      <RouterLink class="brand" to="/">✦ NovelGraph<span>让故事，有迹可循</span></RouterLink>
      <div class="space-picker">
        <span class="eyebrow">CREATIVE WORKSPACE</span
        ><select v-model.number="session.tenantId" aria-label="当前空间" @change="router.push('/')">
          <option v-for="t in session.tenants" :key="t.id" :value="t.id">
            {{ t.name }}
          </option></select
        ><span class="workspace-plan"
          >{{ session.tenant?.kind === 'TEAM' ? '团队空间' : '个人空间' }} ·
          {{ session.tenant?.plan_code || 'FREE' }}</span
        >
      </div>
      <nav>
        <RouterLink v-for="n in navigation" :key="n.path" :to="n.path" @click="mobile = false"
          ><component :is="n.icon" :size="18" />{{ n.label
          }}<span v-if="route.path === n.path">↗</span></RouterLink
        >
      </nav>
      <div class="sidebar-note">
        <Network :size="25" />
        <p>连接人物，<br />也连接故事的可能。</p>
        <small>图谱 · 时间线 · 设定包</small>
      </div>
      <button class="sidebar-help" @click="help = true">
        <Mail :size="17" />帮助与客服<span>↗</span>
      </button>
      <div class="user-card">
        <span class="avatar">{{ session.user.name.slice(0, 1) }}</span>
        <div>
          <strong>{{ session.user.name }}</strong
          ><small>{{ session.user.email }}</small>
        </div>
        <button class="icon" title="退出登录" @click="logout"><LogOut :size="16" /></button>
      </div>
    </aside>
    <section class="app-main">
      <header class="topbar">
        <button class="icon mobile-only" aria-label="打开导航" @click="mobile = !mobile">
          <Menu :size="20" />
        </button>
        <div>
          <span class="muted">创作空间</span
          ><span class="crumb"> / {{ String(route.meta.title || '世界编辑器') }}</span>
        </div>
        <div class="top-actions">
          <span class="online-dot"></span><small>你的世界，由你设定</small
          ><a href="/doc.html#/default" target="_blank" rel="noopener"
            >接口文档<ArrowUpRight :size="13"
          /></a>
        </div>
      </header>
      <RouterView v-slot="{ Component }"
        ><Transition name="page" mode="out-in"
          ><component
            :is="Component"
            :key="
              ['/', '/team', '/plans'].includes(route.path)
                ? route.path + ':' + session.tenantId
                : route.fullPath
            " /></Transition
      ></RouterView>
    </section>
  </div>
  <div v-if="session.message" class="toast" :class="session.tone" role="status">
    {{ session.message
    }}<button class="icon" aria-label="关闭提示" @click="session.message = ''">×</button>
  </div>
  <Dialog v-if="help" title="我们在这里，帮你继续创作" @close="help = false"
    ><p>使用、账号或套餐遇到问题，欢迎通过 QQ 邮箱咨询。</p>
    <div class="support-email">{{ session.supportEmail }}</div>
    <p class="muted">请描述遇到的问题、发生时间和页面。无需提供密码或私密小说内容。</p>
    <template #footer
      ><button class="btn" @click="copyEmail">复制邮箱</button
      ><a
        class="btn primary"
        :href="`mailto:${session.supportEmail}?subject=${encodeURIComponent('[NovelGraph咨询] 使用问题')}`"
        >发送邮件</a
      ></template
    ></Dialog
  >
</template>

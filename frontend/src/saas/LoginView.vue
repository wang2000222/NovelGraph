<script setup lang="ts">
import { ref, computed } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { request } from './api';
import { useSession } from './store';
const session = useSession(),
  router = useRouter(),
  route = useRoute(),
  mode = ref('login'),
  email = ref(''),
  name = ref(''),
  password = ref(''),
  busy = ref(false),
  feedback = ref('');
const action = computed(() => String(route.query.action || ''));
async function submit() {
  busy.value = true;
  feedback.value = '';
  try {
    if (action.value === 'VERIFY') {
      await request('/auth/verify', 'POST', { token: route.query.token });
      feedback.value = '邮箱验证完成，请登录';
      await router.replace('/login');
      return;
    }
    if (action.value === 'RESET') {
      await request('/auth/reset', 'POST', { token: route.query.token, password: password.value });
      feedback.value = '密码已更新，请登录';
      await router.replace('/login');
      return;
    }
    const r = await request('/auth/' + mode.value, 'POST', {
      email: email.value,
      password: password.value,
      name: name.value,
    });
    if (mode.value === 'login') {
      await session.load();
      await router.push('/');
    } else if (mode.value === 'register') {
      feedback.value = r.verificationRequired
        ? '账号已创建，请查看邮箱验证邮件后登录。'
        : '账号已创建，请登录。';
      mode.value = 'login';
    } else feedback.value = '如账号存在，重置邮件将发出，请查看邮箱。';
  } catch (e) {
    feedback.value = (e as Error).message;
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <main class="login-page">
    <section class="login-art">
      <span class="brand">✦ NovelGraph</span><span class="eyebrow">A WORLD WORTH BUILDING</span>
      <h1>每个人物有来处，<br />每场故事有回声。</h1>
      <p>用图谱梳理关系，用时间线连接因果。<br />把确定的世界，交给无限的灵感。</p>
      <div class="orbit-graphic"><i>庄周</i><i>旧井事件</i><i>陈禾</i><i>外婆</i></div>
      <small>作者主导 · 设定稳定 · 团队协作</small>
    </section>
    <section class="login-card">
      <span class="eyebrow">YOUR CREATIVE SPACE</span>
      <h2>
        {{
          action === 'VERIFY'
            ? '验证邮箱'
            : action === 'RESET'
              ? '设置新密码'
              : mode === 'register'
                ? '开启你的创作空间'
                : mode === 'forgot'
                  ? '找回登录密码'
                  : '欢迎回到你的世界'
        }}
      </h2>
      <p class="muted">
        {{ mode === 'register' ? '创建账号，开始连接人物与故事。' : '登录后继续你的世界构建。' }}
      </p>
      <form @submit.prevent="submit">
        <label v-if="mode === 'register'"
          >显示名称<input v-model="name" required maxlength="100" autocomplete="name" /></label
        ><label v-if="!action"
          >邮箱<input
            v-model="email"
            type="email"
            required
            autocomplete="email"
            placeholder="you@example.com" /></label
        ><label v-if="action === 'RESET' || (!action && mode !== 'forgot')"
          >密码<input
            v-model="password"
            type="password"
            :minlength="mode === 'register' || action === 'RESET' ? 10 : undefined"
            required
            :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
            placeholder="至少 10 个字符"
        /></label>
        <p v-if="feedback" class="notice" role="status">{{ feedback }}</p>
        <button class="btn primary block" :disabled="busy">
          {{
            busy
              ? '正在处理…'
              : action === 'VERIFY'
                ? '验证邮箱'
                : action === 'RESET'
                  ? '更新密码'
                  : mode === 'login'
                    ? '登录工作台'
                    : mode === 'register'
                      ? '创建账号'
                      : '发送重置邮件'
          }}
        </button>
      </form>
      <div v-if="!action" class="login-switch">
        <button
          class="text-btn"
          @click="
            mode = mode === 'login' ? 'register' : 'login';
            feedback = '';
          "
        >
          {{ mode === 'login' ? '还没有账号？注册' : '已有账号？登录' }}</button
        ><button
          v-if="mode === 'login'"
          class="text-btn"
          @click="
            mode = 'forgot';
            feedback = '';
          "
        >
          忘记密码
        </button>
      </div>
      <div class="login-help">
        需要帮助？<a :href="`mailto:${session.supportEmail}`"
          >联系客服 {{ session.supportEmail }}</a
        >
      </div>
    </section>
  </main>
</template>

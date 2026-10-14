<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { useSession } from './store';
import { request } from './api';
const s = useSession(),
  router = useRouter(),
  oldPassword = ref(''),
  password = ref(''),
  repeat = ref(''),
  busy = ref(false);
async function revoke() {
  try {
    await request('/auth/revoke-others', 'POST');
    s.notify('其他设备的登录会话已注销');
  } catch (e) {
    s.notify((e as Error).message, 'error');
  }
}
async function change() {
  if (password.value !== repeat.value) {
    s.notify('两次新密码不一致', 'error');
    return;
  }
  busy.value = true;
  try {
    await request('/auth/change-password', 'POST', {
      oldPassword: oldPassword.value,
      password: password.value,
    });
    await s.load();
    await router.push('/login');
    s.notify('密码已修改，请重新登录');
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
        <span class="eyebrow">ACCOUNT SECURITY</span>
        <h1>账号与安全<span class="heading-dot">.</span></h1>
        <p class="muted">{{ s.user?.name }} · {{ s.user?.email }}</p>
      </div>
    </header>
    <section class="panel">
      <h2>登录设备</h2>
      <p class="muted">注销其他设备上的会话，保留当前登录。</p>
      <button class="btn" @click="revoke">注销其他登录会话</button>
    </section>
    <section class="panel" style="max-width: 560px">
      <h2>修改密码</h2>
      <form class="form" style="margin-top: 22px" @submit.prevent="change">
        <label
          >原密码<input
            v-model="oldPassword"
            type="password"
            required
            autocomplete="current-password" /></label
        ><label
          >新密码<input
            v-model="password"
            type="password"
            minlength="10"
            maxlength="200"
            required
            autocomplete="new-password" /></label
        ><label
          >再次输入新密码<input
            v-model="repeat"
            type="password"
            required
            autocomplete="new-password"
        /></label>
        <p class="muted small">修改后将注销所有登录会话，请使用新密码登录。</p>
        <button class="btn primary" :disabled="busy">修改并重新登录</button>
      </form>
    </section>
  </main>
</template>

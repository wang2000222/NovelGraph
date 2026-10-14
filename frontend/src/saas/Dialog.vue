<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue';
const props = defineProps<{ title: string; busy?: boolean; wide?: boolean }>();
const emit = defineEmits<{ close: [] }>();
const box = ref<HTMLElement | null>(null);
let prior: Element | null = null;
let overflow = '';
function keys(e: KeyboardEvent) {
  if (e.key === 'Escape' && !props.busy) emit('close');
  if (e.key === 'Tab' && box.value) {
    const list = Array.from(
      box.value.querySelectorAll<HTMLElement>(
        'button:not(:disabled),input,textarea,select,a[href]',
      ),
    );
    const first = list[0],
      last = list[list.length - 1];
    if (e.shiftKey && document.activeElement === first) {
      e.preventDefault();
      last?.focus();
    } else if (!e.shiftKey && document.activeElement === last) {
      e.preventDefault();
      first?.focus();
    }
  }
}
onMounted(() => {
  prior = document.activeElement;
  overflow = document.body.style.overflow;
  document.body.style.overflow = 'hidden';
  box.value?.querySelector<HTMLElement>('button,input')?.focus();
  document.addEventListener('keydown', keys);
});
onBeforeUnmount(() => {
  document.body.style.overflow = overflow;
  document.removeEventListener('keydown', keys);
  if (prior instanceof HTMLElement) prior.focus();
});
</script>
<template>
  <div class="dialog-shade" @mousedown.self="!busy && emit('close')">
    <section
      ref="box"
      role="dialog"
      aria-modal="true"
      :aria-label="title"
      class="dialog"
      :class="{ wide }"
    >
      <header>
        <div>
          <span class="eyebrow">NOVELGRAPH WORKSPACE</span>
          <h2>{{ title }}</h2>
        </div>
        <button class="icon" :disabled="busy" aria-label="关闭窗口" @click="emit('close')">
          ×
        </button>
      </header>
      <div class="dialog-content"><slot /></div>
      <footer><slot name="footer" /></footer>
    </section>
  </div>
</template>

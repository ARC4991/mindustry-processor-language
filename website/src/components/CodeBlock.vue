<script setup lang="ts">
import { computed, ref } from "vue";
import { Check, Copy } from "lucide-vue-next";
import type { CodeLanguage } from "../types/tutorial";
import { highlightCode } from "../utils/codeHighlight";

const props = defineProps<{
  source: string;
  language: CodeLanguage;
  title?: string;
}>();

const copied = ref(false);
const tokens = computed(() => highlightCode(props.source, props.language));

async function copyCode() {
  try {
    await navigator.clipboard.writeText(props.source);
    copied.value = true;
    window.setTimeout(() => { copied.value = false; }, 1600);
  } catch {
    copied.value = false;
  }
}
</script>

<template>
  <figure class="code-block">
    <figcaption>
      <span>{{ title ?? language.toUpperCase() }}</span>
      <span class="code-language">{{ language }}</span>
      <button type="button" :title="copied ? '已复制' : '复制代码'" :aria-label="copied ? '已复制' : '复制代码'" @click="copyCode">
        <Check v-if="copied" :size="16" />
        <Copy v-else :size="16" />
      </button>
    </figcaption>
    <pre><code><span v-for="(token, index) in tokens" :key="index" :class="`token-${token.kind}`">{{ token.text }}</span></code></pre>
  </figure>
</template>

<script setup lang="ts">
import type { PageContent, PageKey } from "../types/site";

defineProps<{ current: PageKey; pages: Record<PageKey, PageContent> }>();
const emit = defineEmits<{ select: [page: PageKey] }>();
const sourceUrl = "https://github.com/ARC4991/mindustry-processor-language";
</script>

<template>
  <header class="topbar"><div class="shell nav-shell">
    <button class="brand" type="button" @click="emit('select', 'home')">MPL<span>///</span></button>
    <nav aria-label="主导航"><button v-for="(value, key) in pages" :key="key" type="button" :class="{ active: current === key }" @click="emit('select', key)">{{ value.label }}</button></nav>
    <a class="source" :href="sourceUrl" target="_blank" rel="noreferrer">GitHub ↗</a>
  </div></header>
</template>

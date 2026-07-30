<script setup lang="ts">
import { computed } from "vue";

interface Segment {
  text: string;
  highlighted: boolean;
}

const props = defineProps<{ text: string; terms?: string[] }>();

const segments = computed<Segment[]>(() => {
  const terms = [...new Set(props.terms ?? [])].filter(Boolean).sort((left, right) => right.length - left.length);
  if (terms.length === 0) return [{ text: props.text, highlighted: false }];
  const escaped = terms.map(term => term.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"));
  const matcher = new RegExp(`(${escaped.join("|")})`, "g");
  return props.text.split(matcher).filter(Boolean).map(text => ({ text, highlighted: terms.includes(text) }));
});
</script>

<template>
  <span><template v-for="(segment, index) in segments" :key="index"><strong v-if="segment.highlighted" class="term">{{ segment.text }}</strong><template v-else>{{ segment.text }}</template></template></span>
</template>

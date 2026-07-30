<script setup lang="ts">
import { computed, ref } from "vue";
import { BookOpen, Braces, ChevronLeft, ChevronRight } from "lucide-vue-next";
import CodeBlock from "./CodeBlock.vue";
import RichText from "./RichText.vue";
import { mplLessons } from "../content/mplLessons";
import { milLessons } from "../content/milLessons";
import type { TutorialLesson, TutorialTrack } from "../types/tutorial";

const track = ref<TutorialTrack>("mpl");
const selectedId = ref(mplLessons[0].id);
const courseTop = ref<HTMLElement>();

const lessons = computed(() => track.value === "mpl" ? mplLessons : milLessons);
const selectedIndex = computed(() => Math.max(0, lessons.value.findIndex(lesson => lesson.id === selectedId.value)));
const lesson = computed<TutorialLesson>(() => lessons.value[selectedIndex.value]);
const previous = computed(() => lessons.value[selectedIndex.value - 1]);
const next = computed(() => lessons.value[selectedIndex.value + 1]);

function chooseTrack(value: TutorialTrack) {
  track.value = value;
  selectedId.value = value === "mpl" ? mplLessons[0].id : milLessons[0].id;
}

function chooseLesson(id: string) {
  selectedId.value = id;
  courseTop.value?.scrollIntoView({ behavior: "smooth", block: "start" });
}
</script>

<template>
  <section ref="courseTop" class="course-shell">
    <aside class="course-sidebar">
      <div class="track-switch" aria-label="教程语言">
        <button type="button" :class="{ active: track === 'mpl' }" @click="chooseTrack('mpl')"><BookOpen :size="16" />MPL</button>
        <button type="button" :class="{ active: track === 'mil' }" @click="chooseTrack('mil')"><Braces :size="16" />MIL</button>
      </div>
      <p class="course-progress">{{ track.toUpperCase() }} 课程 · {{ lessons.length }} 节</p>
      <nav class="lesson-nav" aria-label="课程章节">
        <button v-for="item in lessons" :key="item.id" type="button" :class="{ active: item.id === lesson.id }" @click="chooseLesson(item.id)">
          <span>{{ item.number }}</span>
          <span><strong>{{ item.title }}</strong><small>{{ item.summary }}</small></span>
        </button>
      </nav>
    </aside>

    <article class="lesson-page">
      <header class="lesson-header">
        <p class="eyebrow">{{ track.toUpperCase() }} COURSE / {{ lesson.number }}</p>
        <h2>{{ lesson.title }}</h2>
        <p>{{ lesson.summary }}</p>
        <div class="keyword-strip" aria-label="本节重点关键词">
          <span class="keyword-label">重点</span>
          <strong v-for="keyword in lesson.keywords" :key="keyword">{{ keyword }}</strong>
        </div>
      </header>

      <section v-for="section in lesson.sections" :key="section.title" class="lesson-section">
        <h3>{{ section.title }}</h3>
        <p v-for="paragraph in section.paragraphs" :key="paragraph"><RichText :text="paragraph" :terms="section.terms" /></p>
        <CodeBlock v-if="section.code" :source="section.code.source" :language="section.code.language" :title="section.code.title" />
        <aside v-if="section.callout" class="callout" :class="`callout-${section.callout.tone}`">
          <strong>{{ section.callout.title }}</strong>
          <p>{{ section.callout.text }}</p>
        </aside>
      </section>

      <nav class="lesson-pager" aria-label="课程翻页">
        <button v-if="previous" type="button" @click="chooseLesson(previous.id)"><ChevronLeft :size="18" /><span><small>上一节</small>{{ previous.title }}</span></button>
        <span v-else></span>
        <button v-if="next" type="button" class="next" @click="chooseLesson(next.id)"><span><small>下一节</small>{{ next.title }}</span><ChevronRight :size="18" /></button>
      </nav>
    </article>
  </section>
</template>

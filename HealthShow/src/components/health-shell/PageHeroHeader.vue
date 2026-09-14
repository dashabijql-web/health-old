<template>
  <section :class="['hm-page-hero', variantClass]">
    <div class="hm-page-hero__main">
      <div class="hm-page-hero__eyebrow-row">
        <span v-if="eyebrow" class="hm-page-hero__eyebrow">{{ eyebrow }}</span>
        <slot name="meta" />
      </div>
      <div class="hm-page-hero__heading-row">
        <div class="hm-page-hero__titles">
          <h1 class="hm-page-hero__title">{{ title }}</h1>
          <p v-if="description" class="hm-page-hero__description">{{ description }}</p>
        </div>
        <div v-if="$slots.actions" class="hm-page-hero__actions">
          <slot name="actions" />
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface PageHeroHeaderProps {
  eyebrow?: string
  title: string
  description?: string
  variant?: string
}

const props = withDefaults(defineProps<PageHeroHeaderProps>(), {
  eyebrow: '',
  description: '',
  variant: 'workbench'
})

const variantClass = computed<string>(() => `hm-page-hero--${props.variant}`)
</script>

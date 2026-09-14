<template>
  <section class="hm-panel-shell">
    <header class="hm-panel-shell__head">
      <div class="hm-panel-shell__lead">
        <span :class="['hm-panel-shell__accent', accentClass]"></span>
        <div class="hm-panel-shell__titles">
          <div class="hm-panel-shell__title-row">
            <h2 class="hm-panel-shell__title">{{ title }}</h2>
            <span v-if="badge" :class="['hm-status-chip', badgeToneClass]">{{ badge }}</span>
          </div>
          <p v-if="subtitle" class="hm-panel-shell__subtitle">{{ subtitle }}</p>
        </div>
      </div>
      <div v-if="$slots.actions" class="hm-panel-shell__actions">
        <slot name="actions" />
      </div>
    </header>
    <div :class="['hm-panel-shell__body', bodyClass]">
      <slot />
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface PanelShellProps {
  title: string
  subtitle?: string
  accent?: string
  badge?: string
  badgeTone?: string
  bodyClass?: string
}

const props = withDefaults(defineProps<PanelShellProps>(), {
  subtitle: '',
  accent: 'primary',
  badge: '',
  badgeTone: 'primary',
  bodyClass: ''
})

const accentClass = computed<string>(() => (
  props.accent === 'primary' ? '' : `hm-panel-shell__accent--${props.accent}`
))

const badgeToneClass = computed<string>(() => (
  props.badgeTone === 'primary' ? '' : `hm-status-chip--${props.badgeTone}`
))
</script>

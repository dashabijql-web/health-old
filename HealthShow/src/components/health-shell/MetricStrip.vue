<template>
  <div :class="['hm-metric-strip', dense ? 'hm-metric-strip--dense' : '']">
    <button
      v-for="item in items"
      :key="item.key || item.label"
      type="button"
      :data-tone="item.tone || 'primary'"
      :class="['hm-metric-strip__item', isClickable(item) ? 'is-clickable' : '']"
      :disabled="!isClickable(item)"
      @click="handleSelect(item)"
    >
      <span class="hm-metric-strip__label">{{ item.label }}</span>
      <span class="hm-metric-strip__value">{{ item.value }}</span>
      <span v-if="item.note" class="hm-metric-strip__note">{{ item.note }}</span>
    </button>
  </div>
</template>

<script setup lang="ts">
interface MetricStripItem {
  key?: string | number
  label: string
  value: string | number
  note?: string | number
  tone?: string
  clickable?: boolean
  [key: string]: unknown
}

interface MetricStripProps {
  items?: MetricStripItem[]
  dense?: boolean
  clickable?: boolean
}

const props = withDefaults(defineProps<MetricStripProps>(), {
  items: () => [],
  dense: false,
  clickable: false
})

const emit = defineEmits<{
  select: [item: MetricStripItem]
}>()

function isClickable(item: MetricStripItem): boolean {
  return props.clickable || Boolean(item.clickable)
}

function handleSelect(item: MetricStripItem): void {
  if (!isClickable(item)) return
  emit('select', item)
}
</script>

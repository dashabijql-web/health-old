<template>
  <div class="dm-model-video-col">
    <div class="dm-event-list-wrap">
      <div class="dm-event-header">
        <div class="dm-event-copy">
          <span class="dm-event-title">实时预警流</span>
        </div>
        <div class="dm-event-summary">
          <span class="dm-event-chip is-pending">待处理 {{ pendingCount }}</span>
          <span class="dm-event-chip">已处理 {{ handledCount }}</span>
        </div>
      </div>
      <div
        class="dm-event-list dm-event-list--scroll"
        ref="warningListMid"
        @mouseenter="onWarnMouseEnter"
        @mouseleave="onWarnMouseLeave"
      >
        <div
          v-for="(ev, i) in warningEvents"
          :key="ev.id || ev.createTime || i"
          :class="['dm-event', dashboardWarningLevelEventClass(ev.level), ev.level === 'danger' ? 'alert-item--critical' : '', ev.handled ? 'ev-handled' : '']"
          @click="openWarnCurve(ev)"
        >
          <div class="dm-ev-row1">
            <span :class="['dm-ev-badge', dashboardWarningLevelBadgeClass(ev.level)]">
              {{ dashboardWarningLevelLabel(ev.level) }}
            </span>
            <span class="dm-ev-type">{{ ev.type }}</span>
            <span class="dm-ev-time">{{ formatTimeAgo(ev.time) }}</span>
          </div>
          <div class="dm-ev-row2">
            <span class="dm-ev-user">{{ ev.userName }}</span>
            <span class="dm-ev-owner" :title="ev.slaText">责任：{{ ev.owner }}</span>
            <span class="dm-ev-val">{{ ev.indicator }}: <em>{{ ev.value }}</em></span>
            <span v-if="ev.handled" class="dm-ev-done">已处理</span>
            <span v-else class="dm-ev-actions">
              <span class="dm-ev-pending">待处理</span>
              <button type="button" class="dm-ev-handle-btn" @click.stop="openCommandIncident(ev)">指挥处置</button>
              <button type="button" class="dm-ev-handle-btn" @click.stop="openHandleDialog(ev)">处理</button>
            </span>
          </div>
        </div>
        <PageEmptyState
          v-if="!warningEvents.length"
          compact
          title="当前时段无预警事件"
          description="当前时段未发现新的风险预警，并不代表所有设备离线或系统绝对安全。"
        />
      </div>
    </div>

    <div class="dm-latest-warn" v-if="latestDangerEvent">
      <span class="dm-lw-dot"></span>
      <span class="dm-lw-name">{{ latestDangerEvent.userName }}</span>
      <span class="dm-lw-sep">·</span>
      <span class="dm-lw-type">{{ latestDangerEvent.type }}</span>
      <span class="dm-lw-sep">·</span>
      <em class="dm-lw-val">{{ latestDangerEvent.value }}</em>
      <span class="dm-lw-pending">待处理</span>
    </div>
    <div class="dm-latest-warn dm-lw-empty" v-else>
      <span class="dm-lw-dot dm-lw-dot--safe"></span>
      <span class="dm-lw-safe-text">当前无危险预警</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch, type PropType } from 'vue'
import { useScrollLoop } from '@/composables/useScrollLoop'
import PageEmptyState from '@/components/health-shell/PageEmptyState.vue'
import {
  dashboardWarningLevelBadgeClass,
  dashboardWarningLevelEventClass,
  dashboardWarningLevelLabel
} from '../dashboard-warning-level'

interface WarningEvent {
  id?: number | string
  time?: string
  createTime?: string
  type?: string
  level?: string
  userName?: string
  owner?: string
  indicator?: string
  value?: string | number
  handled?: boolean
  slaText?: string
  [key: string]: unknown
}

const props = defineProps({
  formatTimeAgo: { type: Function as PropType<(time: unknown) => string>, required: true },
  latestDangerEvent: { type: Object as PropType<WarningEvent | null>, default: null },
  openHandleDialog: { type: Function as PropType<(event: WarningEvent) => void>, required: true },
  openCommandIncident: { type: Function as PropType<(event: WarningEvent) => void>, required: true },
  openWarnCurve: { type: Function as PropType<(event: WarningEvent) => void>, required: true },
  warningEvents: { type: Array as PropType<WarningEvent[]>, required: true }
})

const warningListMid = ref<HTMLElement | null>(null)
const warnHovered = ref(false)
const handledCount = computed(() => props.warningEvents.filter((event) => event.handled).length)
const pendingCount = computed(() => Math.max(0, props.warningEvents.length - handledCount.value))

const warningScroll = useScrollLoop({
  getElement: () => warningListMid.value,
  intervalMs: 40,
  endPauseMs: 2000,
  shouldScroll: () => !warnHovered.value && props.warningEvents.length > 0,
  onReachEnd: () => {
    if (warningListMid.value) warningListMid.value.scrollTop = 0
  }
})

onMounted(() => {
  warningScroll.start()
})

watch(() => props.warningEvents.length, () => {
  warningScroll.start()
})

function onWarnMouseEnter() {
  warnHovered.value = true
  warningScroll.pause()
}

function onWarnMouseLeave(e: MouseEvent) {
  warnHovered.value = false
  const target = e.currentTarget as HTMLElement | null
  if (target) target.scrollTop = target.scrollTop
  warningScroll.resume()
}
</script>

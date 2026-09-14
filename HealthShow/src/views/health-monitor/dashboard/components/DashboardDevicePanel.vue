<template>
  <div class="db-panel dm-main-device">
    <div class="db-track">
      <span class="db-track-title">设备运行概览</span>
      <span class="db-track-sub">总数 / 在线 / 异常</span>
    </div>
    <div class="dm-device-body">
      <div class="dm-device-cards">
        <div
          v-for="c in deviceCards"
          :key="c.label"
          class="dm-dcard"
          :class="[c.cls, c.route ? 'dm-dcard-clickable' : '']"
          @click="c.route && emit('go-device', c)"
        >
          <div class="dm-dcard-val">{{ c.val }}</div>
          <div class="dm-dcard-label">{{ c.label }}</div>
        </div>
      </div>
      <div class="dm-device-gauges">
        <div class="dm-gauge-item">
          <div id="onlineRateChart" class="dm-gauge-chart"></div>
          <div class="dm-gauge-label">在线率</div>
        </div>
        <div class="dm-gauge-item">
          <div id="activeRateChart" class="dm-gauge-chart"></div>
          <div class="dm-gauge-label">激活率</div>
        </div>
        <div class="dm-gauge-item">
          <div id="usageRateChart" class="dm-gauge-chart"></div>
          <div class="dm-gauge-label">使用率</div>
        </div>
        <div class="dm-gauge-item">
          <div id="warningRateChart" class="dm-gauge-chart"></div>
          <div class="dm-gauge-label">预警率</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { PropType } from 'vue'

defineOptions({ name: 'DashboardDevicePanel' })

interface DeviceCard {
  label: string
  val?: string | number
  cls?: string
  route?: string
  [key: string]: unknown
}

defineProps({
  deviceCards: { type: Array as PropType<DeviceCard[]>, default: () => [] }
})

const emit = defineEmits<{
  'go-device': [card: DeviceCard]
}>()
</script>

<style scoped lang="scss">
@import '../dashboard.scss';
</style>

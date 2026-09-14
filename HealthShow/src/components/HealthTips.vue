<template>
  <div class="ht-root">
    <div class="ht-header">
      <span class="ht-dot"></span>
      <span class="ht-title">健康小贴士</span>
      <span class="ht-date">{{ dateLabel }}</span>
      <button class="ht-refresh" @click="refresh" title="换一批">
        <svg viewBox="0 0 1024 1024" width="14" height="14">
          <path d="M960 416V192l-73.056 73.056a447.712 447.712 0 0 0-373.6-201.088C265.92 63.968 65.312 264.544 65.312 512S265.92 960.032 513.344 960.032a448.064 448.064 0 0 0 415.232-279.488 38.368 38.368 0 1 0-71.136-28.896 371.36 371.36 0 0 1-344.096 231.584C308.32 883.232 142.112 717.024 142.112 512S308.32 140.768 513.344 140.768c132.448 0 251.936 70.08 318.016 179.84L736 416h224z" fill="currentColor"/>
        </svg>
      </button>
    </div>
    <ul
      class="ht-list"
      ref="list"
      @mouseenter="pause"
      @mouseleave="resume">
      <li v-for="tip in todayTips" :key="tip.id" class="ht-item">
        <span class="ht-emoji">{{ tip.emoji }}</span>
        <span class="ht-text">{{ tip.text }}</span>
      </li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import dayjs from 'dayjs'
import { useIntervalTask } from '@/composables/useIntervalTask'
import { useTimeoutTask } from '@/composables/useTimeoutTask'

interface HealthTip {
  id: number
  emoji: string
  text: string
}

interface HealthTipsProps {
  count?: number
}

const ALL_TIPS: HealthTip[] = [
  { id: 1,  emoji: '饮水', text: '每天至少喝 1500–2000ml 水，分次少量补充，出汗多时适量补充电解质。' },
  { id: 2,  emoji: '步行', text: '久坐超过 1 小时请起身活动 3–5 分钟，做肩颈后仰伸展，缓解肌肉紧张。' },
  { id: 3,  emoji: '心率', text: '心率偏快时先放慢呼吸，避免咖啡因与情绪波动；持续异常请及时就医评估。' },
  { id: 4,  emoji: '血压', text: '高血压家族史人群建议每周自测 2–3 次血压，同一时间段、坐位静息测量。' },
  { id: 6,  emoji: '饮食', text: '每天 300–500g 蔬果，至少 3 种颜色，优先整果整蔬，减少果汁和糖分摄入。' },
  { id: 7,  emoji: '少盐', text: '成人每日食盐不超过 5g；外卖重口味选少盐少酱料，养成看营养成分表的习惯。' },
  { id: 8,  emoji: '减压', text: '压力大时试试 4-7-8 呼吸法：吸气4秒、屏气7秒、呼气8秒，循环 4 次。' },
  { id: 9,  emoji: '冥想', text: '午后疲劳时做 2 分钟闭眼冥想或身体扫描，有助于恢复专注与情绪稳定。' },
  { id: 10, emoji: '护眼', text: '用眼遵循 20-20-20 法则：每 20 分钟，看 20 英尺外 20 秒，减轻眼部疲劳。' },
  { id: 11, emoji: '运动', text: '每周至少 150 分钟中等强度运动；走路、骑行与爬楼都是高性价比选择。' },
  { id: 12, emoji: '防护', text: '在高温或粉尘环境工作注意补水与防护，必要时佩戴口罩与护目镜。' },
  { id: 13, emoji: '餐盘', text: '餐盘法：蔬菜占一半，蛋白质和主食各占四分之一，减少油炸高脂肪食物。' },
  { id: 14, emoji: '控烟', text: '吸烟伤心血管与肺部，尽量减少二手烟暴露；需要时寻求专业戒烟帮助。' },
  { id: 15, emoji: '口腔', text: '每天认真刷牙两次各 2–3 分钟，使用牙线清理齿缝，半年到一年洗牙一次。' },
  { id: 16, emoji: '体温', text: '体温持续超过 37.5°C 超过两天，或伴随其他症状，请及时就医检查。' },
  { id: 17, emoji: '诊疗', text: '收缩压超过 140 或舒张压超过 90 为高血压，需在医生指导下调整生活方式或用药。' },
  { id: 18, emoji: '控糖', text: '含糖饮料每周不超过 1–2 次；优先白水、无糖茶或淡咖啡，避免能量饮料。' },
  { id: 19, emoji: '恢复', text: '运动前热身 5–10 分钟，结束后做静态拉伸，有助于降低受伤风险并加速恢复。' }
]

const props = withDefaults(defineProps<HealthTipsProps>(), {
  count: 5
})

const list = ref<HTMLElement | null>(null)
const refreshSeed = ref<number>(0)
const paused = ref<boolean>(false)

const dateLabel = computed<string>(() => dayjs().format('MM月DD日 健康提示'))
const todayTips = computed<HealthTip[]>(() => {
  const seed = parseInt(dayjs().format('YYYYMMDD')) + refreshSeed.value
  const shuffled = [...ALL_TIPS].sort((a, b) => {
    const ha = Math.sin(seed * a.id) * 10000
    const hb = Math.sin(seed * b.id) * 10000
    return (ha - Math.floor(ha)) - (hb - Math.floor(hb))
  })
  return shuffled.slice(0, props.count)
})

const { start: startScrollLoop, stop: stopScrollLoop } = useIntervalTask(() => {
  if (paused.value) return
  const el = list.value
  if (!el) return
  const max = el.scrollHeight - el.clientHeight
  if (max <= 0) return
  el.scrollTop += 1
  if (el.scrollTop >= max) {
    paused.value = true
    stopScrollLoop()
    scheduleResume()
  }
}, 40)

const { start: scheduleResume } = useTimeoutTask(() => {
  if (!list.value) return
  list.value.scrollTop = 0
  paused.value = false
  startScrollLoop()
}, 1500)

function refresh(): void {
  refreshSeed.value += 1
  nextTick(() => {
    if (list.value) list.value.scrollTop = 0
  })
}

function pause(): void {
  paused.value = true
}

function resume(): void {
  paused.value = false
}

startScrollLoop()
</script>

<style lang="scss" scoped>
.ht-root {
  background: rgba(10, 18, 48, 0.65);
  border: 1px solid rgba(0, 212, 255, 0.2);
  border-radius: 10px;
  padding: 14px 16px;
  height: 100%;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}

.ht-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  flex-shrink: 0;
}

.ht-dot {
  width: 6px; height: 6px;
  background: #00d4ff;
  border-radius: 50%;
  box-shadow: 0 0 6px #00d4ff;
}

.ht-title {
  font-size: 14px;
  font-weight: bold;
  color: #e0f0ff;
  border-left: 3px solid #00d4ff;
  padding-left: 8px;
}

.ht-date {
  font-size: 11px;
  color: #8ba6c8;
  margin-left: auto;
}

.ht-refresh {
  width: 24px; height: 24px;
  border: none;
  background: rgba(0, 212, 255, 0.1);
  border-radius: 4px;
  color: #00d4ff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s;
  flex-shrink: 0;
  &:hover { background: rgba(0, 212, 255, 0.2); transform: rotate(180deg); }
  &:active { transform: rotate(180deg) scale(0.9); }
  svg { display: block; }
}

.ht-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
  overflow-y: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
  &::-webkit-scrollbar { display: none; }
}

.ht-item {
  flex-shrink: 0;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  background: rgba(0, 40, 90, 0.35);
  border: 1px solid rgba(0, 212, 255, 0.1);
  border-radius: 6px;
  padding: 8px 12px;
  transition: border-color 0.2s, background 0.2s;
  &:hover { border-color: rgba(0, 212, 255, 0.3); background: rgba(0, 50, 110, 0.45); }
}

.ht-emoji {
  min-width: 44px;
  padding: 2px 6px;
  border-radius: 999px;
  border: 1px solid rgba(0, 212, 255, 0.18);
  background: rgba(0, 212, 255, 0.08);
  color: #8fe6ff;
  font-size: 11px;
  font-weight: 700;
  text-align: center;
  flex-shrink: 0;
  line-height: 1.6;
}

.ht-text {
  font-size: 12px;
  color: #a8c5e6;
  line-height: 1.6;
}
</style>

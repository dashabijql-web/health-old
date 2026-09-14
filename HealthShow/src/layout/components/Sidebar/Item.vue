<template>
  <span>
    <!-- Element Plus 图标（通过全局注册的组件名） -->
    <el-icon v-if="icon && !isSvgIcon" class="sub-el-icon" :size="16">
      <component :is="icon" />
    </el-icon>
    <!-- SVG 图标 -->
    <svg-icon v-else-if="icon && isSvgIcon" :icon-class="icon" class="sub-el-icon" />
    <span v-if="title" class="menu-title t-ellipsis">{{ title }}</span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

// Element Plus 图标名列表（用于判断是否是EP图标还是SVG）
const EP_ICONS = new Set([
  'Odometer','DataAnalysis','Share','MagicStick','Moon','View','Warning','Aim',
  'User','UserFilled','Lock','Avatar','Monitor','Bell','Setting','HomeFilled',
  'Grid','Menu','Document','Folder','Edit','Delete','Search','Plus','Minus',
  'Check','Close','ArrowLeft','ArrowRight','ArrowUp','ArrowDown',
  'ChatDotSquare','ChatDotRound','Notification','InfoFilled','SuccessFilled','WarningFilled',
  'CircleCheck','CircleClose','QuestionFilled','StarFilled','Location','Phone',
  'Message','Timer','Calendar','Histogram','PieChart','TrendCharts','DataLine',
  'Key','Cpu','Pointer',
  'OfficeBuilding','SetUp','Stamp','Clock','FirstAidKit','List','Suitcase',
  'Ship','Orange','Apple','Cherry','Watermelon','Grape','Pear','Strawberry'
])

defineOptions({ name: 'MenuItem' })

const props = defineProps({
  icon: { type: String, default: '' },
  title: { type: String, default: '' }
})

const isSvgIcon = computed(() => {
  // el-icon-xxx 是旧 ElementUI 图标（Vue3 不支持，降级为无图标）
  // 不在 EP_ICONS 列表里的当作 SVG 处理
  if (!props.icon) return false
  if (props.icon.startsWith('el-icon')) return false
  return !EP_ICONS.has(props.icon)
})
</script>

<style scoped>
.sub-el-icon {
  color: currentColor;
  vertical-align: middle;
  margin-right: 4px;
}
.menu-title {
  vertical-align: middle;
}
</style>

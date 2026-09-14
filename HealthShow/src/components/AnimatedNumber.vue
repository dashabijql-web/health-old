<template>
  <span :class="['animated-number', className]" :style="numberStyle">
    {{ displayValue }}
  </span>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

type EasingFunction = (progress: number) => number

interface AnimatedNumberProps {
  value?: number
  duration?: number
  decimals?: number
  separator?: boolean
  prefix?: string
  suffix?: string
  fontSize?: string
  color?: string
  fontWeight?: string | number
  className?: string
  ease?: string
}

const props = withDefaults(defineProps<AnimatedNumberProps>(), {
  value: 0,
  duration: 2000,
  decimals: 0,
  separator: true,
  prefix: '',
  suffix: '',
  fontSize: '32px',
  color: '#00f6ff',
  fontWeight: 'bold',
  className: '',
  ease: 'power2.out'
})

// 当前显示的数值
const currentValue = ref<number>(0)
const frameId = ref<number | null>(null)

const EASING_MAP: Record<string, EasingFunction> = {
  linear: (t) => t,
  'power1.out': (t) => 1 - Math.pow(1 - t, 1),
  'power2.out': (t) => 1 - Math.pow(1 - t, 2),
  'power3.out': (t) => 1 - Math.pow(1 - t, 3),
  'power4.out': (t) => 1 - Math.pow(1 - t, 4)
}

// 格式化数字
const formatNumber = (num: number): string => {
  // 保留小数位
  let result = num.toFixed(props.decimals)

  // 添加千分位分隔符
  if (props.separator) {
    const parts = result.split('.')
    parts[0] = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ',')
    result = parts.join('.')
  }

  return props.prefix + result + props.suffix
}

// 计算显示值
const displayValue = computed(() => formatNumber(currentValue.value))

// 计算样式
const numberStyle = computed(() => ({
  fontSize: props.fontSize,
  color: props.color,
  fontWeight: props.fontWeight,
  fontFamily: "'Orbitron', 'Arial Black', sans-serif",
  letterSpacing: '2px',
  textShadow: `0 0 10px ${props.color}40`,
  display: 'inline-block',
  transition: 'all 0.3s ease'
}))

const roundValue = (value: number): number => {
  const factor = Math.pow(10, props.decimals)
  return Math.round(value * factor) / factor
}

const stopAnimation = (): void => {
  if (frameId.value !== null) {
    cancelAnimationFrame(frameId.value)
    frameId.value = null
  }
}

const resolveEase = (easeName: string): EasingFunction => EASING_MAP[easeName] || EASING_MAP['power2.out']

// 执行动画
const animateTo = (target: number): void => {
  stopAnimation()

  const from = Number(currentValue.value) || 0
  const to = Number(target) || 0

  if (props.duration <= 0 || from === to) {
    currentValue.value = roundValue(to)
    return
  }

  const easeFn = resolveEase(props.ease)
  const startedAt = performance.now()

  const step = (now: number): void => {
    const progress = Math.min((now - startedAt) / props.duration, 1)
    const nextValue = from + (to - from) * easeFn(progress)
    currentValue.value = roundValue(progress >= 1 ? to : nextValue)

    if (progress < 1) {
      frameId.value = requestAnimationFrame(step)
      return
    }

    frameId.value = null
  }

  frameId.value = requestAnimationFrame(step)
}

// 监听value变化
watch(() => props.value, (newVal) => {
  animateTo(newVal)
}, { immediate: false })

// 组件挂载时执行初始动画
onMounted(() => {
  currentValue.value = 0
  frameId.value = requestAnimationFrame(() => animateTo(props.value))
})

onBeforeUnmount(() => {
  stopAnimation()
})
</script>

<style scoped>
.animated-number {
  display: inline-block;
  font-variant-numeric: tabular-nums;
  user-select: none;
}

/* 数字跳动动画 */
@keyframes pulse {
  0%, 100% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.05);
  }
}

.animated-number:hover {
  animation: pulse 0.5s ease-in-out;
}

</style>

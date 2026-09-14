<template>
  <div class="heart-rate-container" :style="containerStyle">
    <canvas ref="canvasRef" :width="width" :height="height" class="wave-canvas"></canvas>

    <!-- 扫描线 -->
    <div class="scan-line" :style="scanLineStyle"></div>

    <!-- 心率显示 -->
    <div class="heart-rate-display">
      <div class="heart-icon">ECG</div>
      <div class="heart-value">{{ currentHeartRate }}</div>
      <div class="heart-unit">BPM</div>
    </div>

    <!-- 网格背景 -->
    <div class="grid-background"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'

interface HeartRateWaveProps {
  width?: number
  height?: number
  heartRate?: number
  waveColor?: string
  gridColor?: string
  scanColor?: string
  speed?: number
  showWarning?: boolean
}

const props = withDefaults(defineProps<HeartRateWaveProps>(), {
  width: 800,
  height: 200,
  heartRate: 0,
  waveColor: '#00ff00',
  gridColor: 'rgba(0, 255, 0, 0.1)',
  scanColor: '#00ff00',
  speed: 2,
  showWarning: true
})

const canvasRef = ref<HTMLCanvasElement | null>(null)
const currentHeartRate = ref<number>(75)
const scanPosition = ref<number>(0)
let animationId: number | null = null
let dataPoints: number[] = []
let currentX = 0

// 容器样式
const containerStyle = computed(() => ({
  width: `${props.width}px`,
  height: `${props.height}px`
}))

// 扫描线样式
const scanLineStyle = computed(() => ({
  left: `${scanPosition.value}px`,
  height: `${props.height}px`,
  boxShadow: `0 0 10px ${props.scanColor}`
}))

// 生成ECG波形数据点
const generateECGPoint = (x: number): number => {
  const baseY = props.height / 2
  const amplitude = props.height * 0.35

  // ECG波形特征点（一个心跳周期）
  const cycleLength = 200 // 心跳周期长度
  const position = x % cycleLength

  let y = baseY

  // P波（0-20）
  if (position < 20) {
    const t = position / 20
    y = baseY - amplitude * 0.15 * Math.sin(t * Math.PI)
  }
  // PR段（20-50）
  else if (position < 50) {
    y = baseY
  }
  // QRS波群（50-90）
  else if (position < 90) {
    const t = (position - 50) / 40
    if (t < 0.25) {
      // Q波
      y = baseY + amplitude * 0.2 * (t / 0.25)
    } else if (t < 0.5) {
      // R波
      y = baseY - amplitude * ((t - 0.25) / 0.25)
    } else if (t < 0.75) {
      // S波
      y = baseY + amplitude * 0.3 * ((t - 0.5) / 0.25)
    } else {
      // 回到基线
      y = baseY - amplitude * 0.3 * (1 - (t - 0.75) / 0.25)
    }
  }
  // ST段（90-120）
  else if (position < 120) {
    y = baseY
  }
  // T波（120-170）
  else if (position < 170) {
    const t = (position - 120) / 50
    y = baseY - amplitude * 0.25 * Math.sin(t * Math.PI)
  }
  // 基线（170-200）
  else {
    y = baseY
  }

  return y
}

// 绘制波形
const drawWave = (): void => {
  const canvas = canvasRef.value
  if (!canvas) return

  const ctx = canvas.getContext('2d')
  if (!ctx) return

  // 清空画布
  ctx.clearRect(0, 0, props.width, props.height)

  // 设置线条样式
  ctx.strokeStyle = props.waveColor
  ctx.lineWidth = 2
  ctx.lineCap = 'round'
  ctx.lineJoin = 'round'
  ctx.shadowBlur = 5
  ctx.shadowColor = props.waveColor

  // 绘制波形
  ctx.beginPath()

  for (let i = 0; i < dataPoints.length - 1; i++) {
    const x = i
    const y = dataPoints[i]

    if (i === 0) {
      ctx.moveTo(x, y)
    } else {
      ctx.lineTo(x, y)
    }
  }

  ctx.stroke()

  // 移动扫描线
  scanPosition.value = currentX % props.width
}

// 动画循环
const animate = (): void => {
  // 添加新数据点
  const newY = generateECGPoint(currentX)
  dataPoints.push(newY)

  // 限制数据点数量
  if (dataPoints.length > props.width) {
    dataPoints.shift()
  }

  // 移动X坐标
  currentX += props.speed

  // 绘制
  drawWave()

  // 更新心率（模拟心率变化）
  if (props.heartRate > 0) {
    currentHeartRate.value = props.heartRate
  }

  animationId = requestAnimationFrame(animate)
}

// 初始化
onMounted(() => {
  // 初始化数据点
  for (let i = 0; i < props.width; i++) {
    dataPoints.push(props.height / 2)
  }

  // 开始动画
  animate()
})

// 清理
onUnmounted(() => {
  if (animationId !== null) {
    cancelAnimationFrame(animationId)
  }
})
</script>

<style scoped>
.heart-rate-container {
  position: relative;
  background: rgba(0, 20, 0, 0.9);
  border: 2px solid rgba(0, 255, 0, 0.3);
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 0 20px rgba(0, 255, 0, 0.2);
  max-width: 100%;
}

.wave-canvas {
  position: relative;
  z-index: 2;
  display: block;
}

/* 网格背景 */
.grid-background {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 1;
  background-image:
    linear-gradient(rgba(0, 255, 0, 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0, 255, 0, 0.05) 1px, transparent 1px);
  background-size: 20px 20px;
}

/* 扫描线 */
.scan-line {
  position: absolute;
  top: 0;
  width: 2px;
  background: linear-gradient(to bottom,
    transparent 0%,
    rgba(0, 255, 0, 0.8) 50%,
    transparent 100%
  );
  z-index: 3;
  pointer-events: none;
  animation: scanPulse 1s ease-in-out infinite;
}

@keyframes scanPulse {
  0%, 100% {
    opacity: 0.8;
  }
  50% {
    opacity: 1;
  }
}

/* 心率显示 */
.heart-rate-display {
  position: absolute;
  top: 10px;
  right: 10px;
  z-index: 4;
  display: flex;
  align-items: center;
  gap: 8px;
  background: rgba(0, 0, 0, 0.7);
  padding: 10px 15px;
  border-radius: 8px;
  border: 1px solid rgba(0, 255, 0, 0.3);
  box-shadow: 0 0 15px rgba(0, 255, 0, 0.3);
}

.heart-icon {
  min-width: 44px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(0, 255, 136, 0.14);
  border: 1px solid rgba(0, 255, 136, 0.28);
  color: #94ffd1;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-align: center;
  animation: heartBeat 1s ease-in-out infinite;
}

@keyframes heartBeat {
  0%, 100% {
    transform: scale(1);
  }
  10%, 30% {
    transform: scale(1.2);
  }
  20%, 40% {
    transform: scale(1);
  }
}

.heart-value {
  font-size: 32px;
  font-weight: bold;
  color: #00ff00;
  font-family: 'Orbitron', 'Courier New', monospace;
  text-shadow: 0 0 10px rgba(0, 255, 0, 0.8);
  letter-spacing: 2px;
  min-width: 60px;
  text-align: center;
}

.heart-unit {
  font-size: 14px;
  color: rgba(0, 255, 0, 0.7);
  font-weight: bold;
}

</style>

<template>
  <section class="mine-gis" aria-label="矿井巷道 GIS 地图">
    <canvas ref="canvasRef" class="mine-gis__canvas"></canvas>
    <div class="mine-gis__hud mine-gis__hud--top">
      <div class="mine-gis__card mine-gis__summary">
        <strong>矿井巷道 GIS</strong>
        <span>CAD 平面坐标 · 真实巷道与注记</span>
        <div class="mine-gis__stats">
          <span>巷道 <b>{{ formatNumber(mapData?.lineCount) }}</b></span>
          <span>顶点 <b>{{ formatNumber(mapData?.pointCount) }}</b></span>
          <span>注记 <b>{{ formatNumber(mapData?.textCount) }}</b></span>
        </div>
      </div>
      <div class="mine-gis__card mine-gis__controls">
        <input v-model="searchText" type="search" placeholder="搜索巷道 / 水平 / 井筒" aria-label="搜索巷道、水平或井筒" />
        <label><input v-model="showLines" type="checkbox" /> 巷道</label>
        <label><input v-model="showTexts" type="checkbox" /> 注记</label>
        <button type="button" title="适应窗口" @click="fitMap">适应</button>
        <button type="button" title="放大" @click="zoomAtCenter(1.25)">＋</button>
        <button type="button" title="缩小" @click="zoomAtCenter(.8)">−</button>
      </div>
    </div>
    <div v-if="selectedLabel" class="mine-gis__hit" :style="hitStyle">
      <strong>{{ selectedLabel.t }}</strong>
      <span>CAD X {{ selectedLabel.x.toFixed(2) }}</span>
      <span>CAD Y {{ selectedLabel.y.toFixed(2) }}</span>
    </div>
    <div class="mine-gis__hud mine-gis__hud--bottom">
      <div class="mine-gis__card mine-gis__hint">滚轮缩放 · 拖动平移 · 点击注记查看 CAD 坐标<br />Y 轴北向上，坐标不是经纬度</div>
      <div class="mine-gis__card mine-gis__coord">{{ coordinateText }}<small>{{ scaleText }}</small></div>
    </div>
    <div v-if="loading || error" class="mine-gis__state" role="status">
      <strong v-if="loading">正在加载矿井 GIS 图</strong>
      <strong v-else>GIS 图暂不可用</strong>
      <span v-if="error">{{ error }}</span>
    </div>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const canvasRef = ref(null)
const mapData = ref(null)
const loading = ref(true)
const error = ref('')
const searchText = ref('')
const showLines = ref(true)
const showTexts = ref(true)
const selectedLabel = ref(null)
const hitPosition = ref({ x: 0, y: 0 })
const coordinateText = ref('X —  Y —')
const scaleText = ref('比例尺')

const state = { scale: 1, panX: 0, panY: 0, dragging: false, lastX: 0, lastY: 0, startX: 0, startY: 0 }
let ctx
let resizeObserver
let searchTimer

const hitStyle = computed(() => ({ left: `${hitPosition.value.x + 12}px`, top: `${hitPosition.value.y + 12}px` }))

function formatNumber(value) {
  return Number.isFinite(Number(value)) ? Number(value).toLocaleString('zh-CN') : '—'
}

function dpr() { return Math.min(window.devicePixelRatio || 1, 2) }
function extent() { return { w: mapData.value.extent[2], h: mapData.value.extent[3] } }
function worldToScreen(x, y) {
  return { x: x * state.scale + state.panX, y: (extent().h - y) * state.scale + state.panY }
}
function screenToWorld(x, y) {
  return { x: (x - state.panX) / state.scale, y: extent().h - (y - state.panY) / state.scale }
}

function labelRank(text) {
  if (/(工作面|井底车场|炸药库|西风井|东风井|主井|付井|新付井)$/.test(text)) return 0
  if (/巷|上山|下山|车场|硐室|石门|风道/.test(text)) return 1
  if (text.length <= 1 || /^[+\-]?\d+(\.\d+)?$/.test(text)) return 3
  return 2
}
function isMajor(text) { return labelRank(text) === 0 }
function isMedium(text) { return labelRank(text) === 1 }
function isNoise(text) { return labelRank(text) === 3 }

function fitTo(x0, y0, x1, y1, pad = 48) {
  if (!mapData.value || !canvasRef.value) return
  const width = Math.max(20, x1 - x0)
  const height = Math.max(20, y1 - y0)
  const top = 72
  const viewportWidth = canvasRef.value.clientWidth - pad * 2
  const viewportHeight = canvasRef.value.clientHeight - pad * 2 - top
  state.scale = Math.min(viewportWidth / width, viewportHeight / height)
  const worldHeight = extent().h
  const sx = x0 * state.scale
  const sy = (worldHeight - y1) * state.scale
  state.panX = (canvasRef.value.clientWidth - width * state.scale) / 2 - sx
  state.panY = top + (canvasRef.value.clientHeight - top - height * state.scale) / 2 - sy
  draw()
}
function fitMap() {
  if (!mapData.value) return
  fitTo(0, 0, extent().w, extent().h)
}
function fitSearch() {
  const query = searchText.value.trim()
  if (!query) return fitMap()
  const hits = mapData.value?.texts?.filter(item => item.t.includes(query)) || []
  if (!hits.length) return draw()
  const bounds = hits.reduce((acc, item) => ({
    x0: Math.min(acc.x0, item.x), y0: Math.min(acc.y0, item.y),
    x1: Math.max(acc.x1, item.x), y1: Math.max(acc.y1, item.y)
  }), { x0: Infinity, y0: Infinity, x1: -Infinity, y1: -Infinity })
  fitTo(bounds.x0 - 400, bounds.y0 - 300, bounds.x1 + 400, bounds.y1 + 300)
}
function zoomAt(sx, sy, factor) {
  if (!mapData.value) return
  const before = screenToWorld(sx, sy)
  state.scale = Math.min(48, Math.max(.02, state.scale * factor))
  const after = worldToScreen(before.x, before.y)
  state.panX += sx - after.x
  state.panY += sy - after.y
  draw()
}
function zoomAtCenter(factor) {
  if (!canvasRef.value) return
  zoomAt(canvasRef.value.clientWidth / 2, canvasRef.value.clientHeight / 2, factor)
}
function niceScale() {
  const steps = [5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000]
  const value = steps.find(item => item * state.scale >= 60) || steps[steps.length - 1]
  scaleText.value = value >= 1000 ? `${value / 1000} km` : `${value} m`
}

function draw() {
  if (!ctx || !mapData.value || !canvasRef.value) return
  const width = canvasRef.value.clientWidth
  const height = canvasRef.value.clientHeight
  ctx.setTransform(dpr(), 0, 0, dpr(), 0, 0)
  ctx.clearRect(0, 0, width, height)
  const gradient = ctx.createRadialGradient(width * .5, height * .45, 40, width * .5, height * .5, Math.max(width, height) * .7)
  gradient.addColorStop(0, '#122433')
  gradient.addColorStop(1, '#081018')
  ctx.fillStyle = gradient
  ctx.fillRect(0, 0, width, height)
  if (showLines.value) {
    ctx.lineJoin = 'round'; ctx.lineCap = 'round'; ctx.strokeStyle = 'rgba(143,212,232,.82)'
    ctx.lineWidth = Math.max(.6, Math.min(1.8, state.scale * .9)); ctx.beginPath()
    mapData.value.lines.forEach(line => {
      const start = worldToScreen(line[0][0], line[0][1]); ctx.moveTo(start.x, start.y)
      for (let i = 1; i < line.length; i += 1) { const point = worldToScreen(line[i][0], line[i][1]); ctx.lineTo(point.x, point.y) }
    })
    ctx.stroke()
  }
  if (showTexts.value) {
    const query = searchText.value.trim(); const boxes = []
    mapData.value.texts.slice().sort((a, b) => labelRank(a.t) - labelRank(b.t)).forEach(item => {
      if (!query) {
        if (isNoise(item.t) && state.scale < .22) return
        if (!isMajor(item.t) && !isMedium(item.t) && state.scale < .14) return
        if (!isMajor(item.t) && state.scale < .09) return
      } else if (!item.t.includes(query)) return
      const point = worldToScreen(item.x, item.y)
      if (point.x < -80 || point.y < -40 || point.x > width + 80 || point.y > height + 40) return
      const fontSize = Math.max(state.scale < .18 ? 10 : 9, Math.min(16, item.s * state.scale * .9))
      const box = { x: point.x - 4, y: point.y - fontSize - 4, w: item.t.length * fontSize * .92 + 8, h: fontSize + 8 }
      if (!query && boxes.some(other => box.x < other.x + other.w && box.x + box.w > other.x && box.y < other.y + other.h && box.y + box.h > other.y)) return
      boxes.push(box)
      ctx.save(); ctx.translate(point.x, point.y); ctx.rotate(-(item.a || 0) * Math.PI / 180)
      ctx.font = `${isMajor(item.t) ? 600 : 400} ${fontSize}px "PingFang SC","Noto Sans SC",sans-serif`
      ctx.lineWidth = 3; ctx.strokeStyle = 'rgba(8,16,24,.85)'; ctx.fillStyle = query && item.t.includes(query) ? '#f0c36a' : (isMajor(item.t) ? '#3ee0c4' : '#d7ecf4')
      ctx.strokeText(item.t, 0, 0); ctx.fillText(item.t, 0, 0); ctx.restore()
    })
  }
  niceScale()
}

function pickLabel(x, y) {
  if (!showTexts.value || !mapData.value) return null
  let best = null; let distance = 18
  mapData.value.texts.forEach(item => { const point = worldToScreen(item.x, item.y); const current = Math.hypot(point.x - x, point.y - y); if (current < distance) { best = item; distance = current } })
  return best
}
function resize() {
  if (!canvasRef.value || !ctx) return
  const ratio = dpr(); canvasRef.value.width = Math.floor(canvasRef.value.clientWidth * ratio); canvasRef.value.height = Math.floor(canvasRef.value.clientHeight * ratio); draw()
}
function onPointerDown(event) { state.dragging = true; state.lastX = event.clientX; state.lastY = event.clientY; state.startX = event.clientX; state.startY = event.clientY; canvasRef.value.setPointerCapture(event.pointerId) }
function onPointerMove(event) {
  if (!mapData.value) return
  const point = screenToWorld(event.offsetX, event.offsetY); const [originX, originY] = mapData.value.origin
  coordinateText.value = `X ${(originX + point.x).toFixed(2)}   Y ${(originY + point.y).toFixed(2)}`
  if (!state.dragging) return
  state.panX += event.clientX - state.lastX; state.panY += event.clientY - state.lastY; state.lastX = event.clientX; state.lastY = event.clientY; selectedLabel.value = null; draw()
}
function onPointerUp(event) {
  const moved = Math.hypot(event.clientX - state.startX, event.clientY - state.startY); state.dragging = false
  if (canvasRef.value.hasPointerCapture(event.pointerId)) canvasRef.value.releasePointerCapture(event.pointerId)
  if (moved < 4) { const item = pickLabel(event.offsetX, event.offsetY); if (!item) return; const [originX, originY] = mapData.value.origin; selectedLabel.value = { t: item.t, x: originX + item.x, y: originY + item.y }; hitPosition.value = { x: event.offsetX, y: event.offsetY } }
}
function onWheel(event) { event.preventDefault(); zoomAt(event.offsetX, event.offsetY, event.deltaY > 0 ? .9 : 1.11) }

onMounted(async () => {
  await nextTick()
  const canvas = canvasRef.value; if (!canvas) return
  ctx = canvas.getContext('2d')
  if (!ctx) { loading.value = false; error.value = '当前浏览器不支持 Canvas。'; return }
  canvas.addEventListener('pointerdown', onPointerDown); canvas.addEventListener('pointermove', onPointerMove); canvas.addEventListener('pointerup', onPointerUp); canvas.addEventListener('wheel', onWheel, { passive: false })
  resizeObserver = new ResizeObserver(resize); resizeObserver.observe(canvas)
  try {
    const response = await fetch('/maps/mine-map.json')
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    mapData.value = await response.json(); loading.value = false; resize(); fitMap()
  } catch (loadError) { loading.value = false; error.value = '矿井 GIS 数据加载失败，请检查静态资源。' }
})

watch([showLines, showTexts], draw)
watch(searchText, () => { clearTimeout(searchTimer); searchTimer = setTimeout(fitSearch, 180) })
onBeforeUnmount(() => {
  clearTimeout(searchTimer); resizeObserver?.disconnect(); const canvas = canvasRef.value
  canvas?.removeEventListener('pointerdown', onPointerDown); canvas?.removeEventListener('pointermove', onPointerMove); canvas?.removeEventListener('pointerup', onPointerUp); canvas?.removeEventListener('wheel', onWheel)
})
</script>

<style scoped lang="scss">
.mine-gis { position: absolute; inset: 0; overflow: hidden; background: #081018; color: #d7ecf4; font-size: 11px; }
.mine-gis__canvas { display: block; width: 100%; height: 100%; cursor: grab; }
.mine-gis__canvas:active { cursor: grabbing; }
.mine-gis__hud { position: absolute; z-index: 3; display: flex; gap: 8px; pointer-events: none; }
.mine-gis__hud--top { top: 10px; left: 10px; right: 10px; justify-content: space-between; align-items: flex-start; }
.mine-gis__hud--bottom { right: 10px; bottom: 10px; left: 10px; justify-content: space-between; align-items: flex-end; }
.mine-gis__card { pointer-events: auto; border: 1px solid rgba(62,224,196,.22); border-radius: 7px; background: rgba(8,16,24,.82); backdrop-filter: blur(8px); box-shadow: 0 8px 20px rgba(0,0,0,.22); }
.mine-gis__summary { display: grid; gap: 3px; padding: 8px 10px; min-width: 190px; }
.mine-gis__summary strong { color: #e5fbff; font-size: 12px; }
.mine-gis__summary > span, .mine-gis__hint { color: rgba(215,236,244,.65); font-size: 9px; }
.mine-gis__stats { display: flex; gap: 9px; color: rgba(215,236,244,.72); font-size: 9px; }
.mine-gis__stats b { color: #3ee0c4; font-variant-numeric: tabular-nums; }
.mine-gis__controls { display: flex; gap: 5px; align-items: center; padding: 7px; flex-wrap: wrap; }
.mine-gis__controls input[type='search'] { width: 154px; min-width: 0; padding: 5px 7px; border: 1px solid rgba(62,224,196,.22); border-radius: 5px; outline: none; background: rgba(8,16,24,.75); color: #d7ecf4; font: inherit; }
.mine-gis__controls label, .mine-gis__controls button { padding: 4px 6px; border: 1px solid rgba(62,224,196,.18); border-radius: 4px; background: rgba(62,224,196,.07); color: #d7ecf4; font: inherit; white-space: nowrap; }
.mine-gis__controls button { cursor: pointer; }
.mine-gis__controls button:hover { background: rgba(62,224,196,.16); }
.mine-gis__hint, .mine-gis__coord { padding: 7px 9px; }
.mine-gis__coord { display: grid; gap: 3px; min-width: 104px; color: rgba(215,236,244,.72); font-variant-numeric: tabular-nums; }
.mine-gis__coord small { color: #3ee0c4; text-align: right; }
.mine-gis__hit { position: absolute; z-index: 4; display: grid; gap: 2px; max-width: 220px; padding: 7px 9px; border: 1px solid #3ee0c4; border-radius: 6px; background: rgba(8,16,24,.9); color: #d7ecf4; pointer-events: none; }
.mine-gis__hit strong { color: #3ee0c4; }
.mine-gis__state { position: absolute; inset: 0; z-index: 5; display: grid; place-content: center; gap: 5px; text-align: center; background: rgba(8,16,24,.72); color: #d7ecf4; }
.mine-gis__state span { color: #ffb76b; font-size: 10px; }
@media (max-width: 720px) { .mine-gis__hud--top { flex-direction: column; } .mine-gis__controls { width: max-content; } .mine-gis__summary > span, .mine-gis__hint { display: none; } .mine-gis__controls input[type='search'] { width: 132px; } }
</style>

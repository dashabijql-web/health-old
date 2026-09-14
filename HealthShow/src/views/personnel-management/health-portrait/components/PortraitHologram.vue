<template>
  <section class="portrait-hologram" aria-label="三维人体健康信号展示">
    <div class="hologram-grid"></div>
    <div class="hologram-vignette"></div>
    <header class="hologram-header">
      <div>
        <span class="hologram-eyebrow">3D BODY SIGNAL VIEW</span>
        <strong>{{ personName || '当前人员' }}</strong>
      </div>
      <label class="hologram-motion-toggle">
        <input v-model="reducedMotion" type="checkbox" />
        <span>减少动态</span>
      </label>
    </header>

    <div ref="canvasHost" class="hologram-canvas">
      <div v-if="sceneError" class="hologram-error" role="status">
        <strong>三维人体视图暂不可用</strong>
        <span>{{ sceneError }}</span>
      </div>
      <div v-else-if="modelLoading" class="hologram-loading" role="status">正在加载三维人体模型…</div>
    </div>

    <div class="hologram-label hologram-label--heart">
      <span class="hologram-dot hologram-dot--heart"></span>
      <b>心率</b><strong>{{ formatValue(vitals?.heartRate) }}</strong><small>BPM</small>
    </div>
    <div class="hologram-label hologram-label--oxygen">
      <span class="hologram-dot hologram-dot--oxygen"></span>
      <b>血氧</b><strong>{{ formatValue(vitals?.bloodOxygen) }}</strong><small>%</small>
    </div>
    <div class="hologram-label hologram-label--temp">
      <span class="hologram-dot hologram-dot--temp"></span>
      <b>体温</b><strong>{{ formatValue(vitals?.temperature) }}</strong><small>°C</small>
    </div>

    <footer class="hologram-footer">
      <span>采集时间 {{ lastCollected || '暂无记录' }}</span>
      <span :class="['hologram-status', `is-${statusTone}`]">{{ statusLabel }}</span>
    </footer>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as THREE from 'three'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'

const props = defineProps({
  personName: { type: String, default: '' },
  vitals: { type: Object, default: () => ({}) },
  freshnessStatus: { type: String, default: 'no_data' },
  lastCollected: { type: String, default: '' }
})

const canvasHost = ref(null)
const reducedMotion = ref(false)
const modelLoading = ref(true)
const sceneError = ref('')
let scene
let camera
let renderer
let modelPivot
let particleField
let scanLine
let resizeObserver
let animationFrame
let dragging = false
let lastPointerX = 0
let particleVelocity = []
const orbitRings = []

const statusTone = computed(() => {
  if (props.freshnessStatus === 'fresh') return 'normal'
  if (props.freshnessStatus === 'stale') return 'warning'
  if (props.freshnessStatus === 'offline') return 'offline'
  return 'unknown'
})

const statusLabel = computed(() => ({
  fresh: '数据新鲜',
  stale: '数据陈旧',
  offline: '设备离线',
  no_data: '暂无有效体征'
}[props.freshnessStatus] || '状态未知'))

function formatValue(value) {
  return value === null || value === undefined || value === '' || Number(value) === 0 ? '--' : value
}

function initScene() {
  if (!canvasHost.value) return
  try {
    scene = new THREE.Scene()
    camera = new THREE.PerspectiveCamera(32, 1, 0.1, 100)
    camera.position.set(0, 1.48, 6)
    camera.lookAt(0, 1.42, 0)
    renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: 'high-performance' })
  } catch (error) {
    modelLoading.value = false
    sceneError.value = '当前浏览器未提供可用的 WebGL。'
    return
  }
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.6))
  renderer.setClearColor(0x000000, 0)
  canvasHost.value.appendChild(renderer.domElement)

  const canvas = renderer.domElement
  canvas.addEventListener('pointerdown', onPointerDown)
  canvas.addEventListener('pointermove', onPointerMove)
  canvas.addEventListener('pointerup', onPointerUp)
  canvas.addEventListener('pointercancel', onPointerUp)

  scene.add(new THREE.HemisphereLight(0x9bdcff, 0x061120, 1.6))
  const rim = new THREE.PointLight(0x35c7ff, 3.2, 8)
  rim.position.set(2.2, 3.1, 2.5)
  scene.add(rim)
  const fill = new THREE.PointLight(0x2655c8, 2, 7)
  fill.position.set(-2.5, 1.2, -2)
  scene.add(fill)

  addPlatform()
  addOrbitRings()
  addParticleField()
  addScanLine()
  loadModel()

  resizeObserver = new ResizeObserver(resizeScene)
  resizeObserver.observe(canvasHost.value)
  resizeScene()
  animate()
}

function addPlatform() {
  const group = new THREE.Group()
  group.position.y = 0.03
  scene.add(group)
  group.add(new THREE.Mesh(
    new THREE.CylinderGeometry(1.62, 1.76, 0.12, 96),
    new THREE.MeshStandardMaterial({ color: 0x071728, metalness: 0.8, roughness: 0.32, emissive: 0x061a31, emissiveIntensity: 0.6 })
  ))
  ;[1.05, 0.78, 0.52].forEach((radius, index) => {
    const ring = new THREE.Mesh(
      new THREE.TorusGeometry(radius, 0.018, 8, 96),
      new THREE.MeshBasicMaterial({ color: index === 1 ? 0x58d7f4 : 0x1674a8, transparent: true, opacity: index === 1 ? 0.82 : 0.58 })
    )
    ring.rotation.x = Math.PI / 2
    ring.position.y = 0.085 + index * 0.018
    group.add(ring)
  })
}

function addOrbitRings() {
  ;[
    { radius: 1.32, y: 1.52, tilt: 0.08, color: 0x1c94c9, opacity: 0.42 },
    { radius: 1.08, y: 2.12, tilt: -0.28, color: 0x4bd7ef, opacity: 0.28 },
    { radius: 0.88, y: 1.04, tilt: 0.24, color: 0x55d6b7, opacity: 0.24 }
  ].forEach((item) => {
    const ring = new THREE.Mesh(
      new THREE.TorusGeometry(item.radius, 0.012, 8, 96),
      new THREE.MeshBasicMaterial({ color: item.color, transparent: true, opacity: item.opacity })
    )
    ring.rotation.x = Math.PI / 2 + item.tilt
    ring.position.y = item.y
    scene.add(ring)
    orbitRings.push(ring)
  })
}

function addParticleField() {
  const count = 150
  const positions = new Float32Array(count * 3)
  const colors = new Float32Array(count * 3)
  particleVelocity = new Float32Array(count)
  const palette = [new THREE.Color(0x39d7ff), new THREE.Color(0x63a5ff), new THREE.Color(0x6de3c1)]
  for (let i = 0; i < count; i += 1) {
    const angle = Math.random() * Math.PI * 2
    const radius = 1.05 + Math.random() * 1.45
    positions[i * 3] = Math.cos(angle) * radius
    positions[i * 3 + 1] = 0.18 + Math.random() * 3.35
    positions[i * 3 + 2] = Math.sin(angle) * radius * 0.52
    const color = palette[i % palette.length]
    colors[i * 3] = color.r
    colors[i * 3 + 1] = color.g
    colors[i * 3 + 2] = color.b
    particleVelocity[i] = 0.035 + Math.random() * 0.07
  }
  const geometry = new THREE.BufferGeometry()
  geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3))
  geometry.setAttribute('color', new THREE.BufferAttribute(colors, 3))
  particleField = new THREE.Points(geometry, new THREE.PointsMaterial({ size: 0.042, vertexColors: true, transparent: true, opacity: 0.76, depthWrite: false }))
  scene.add(particleField)
}

function addScanLine() {
  scanLine = new THREE.Mesh(
    new THREE.BoxGeometry(2.8, 0.009, 0.012),
    new THREE.MeshBasicMaterial({ color: 0x42d9ff, transparent: true, opacity: 0.34 })
  )
  scanLine.position.set(0, 0.62, 0.35)
  scene.add(scanLine)
}

function loadModel() {
  new GLTFLoader().load('/models/wireframe_man.glb', (gltf) => {
    const model = gltf.scene
    const box = new THREE.Box3().setFromObject(model)
    const height = Math.max(box.max.y - box.min.y, 0.1)
    model.scale.setScalar(2.48 / height)
    const after = new THREE.Box3().setFromObject(model)
    model.position.set(-((after.min.x + after.max.x) / 2), 0.18 - after.min.y, -((after.min.z + after.max.z) / 2))
    model.traverse((child) => {
      if (!child.isMesh) return
      child.material = new THREE.MeshStandardMaterial({ color: 0x35d9ff, emissive: 0x0c9fc5, emissiveIntensity: 1.42, metalness: 0.2, roughness: 0.42, transparent: true, opacity: 0.93 })
    })
    modelPivot = new THREE.Group()
    modelPivot.add(model)
    scene.add(modelPivot)
    modelLoading.value = false
  }, undefined, () => {
    modelLoading.value = false
    sceneError.value = '人体模型资源加载失败，请检查静态资源服务。'
  })
}

function onPointerDown(event) {
  dragging = true
  lastPointerX = event.clientX
  renderer.domElement.setPointerCapture(event.pointerId)
}

function onPointerMove(event) {
  if (!dragging || !modelPivot) return
  modelPivot.rotation.y += (event.clientX - lastPointerX) * 0.008
  lastPointerX = event.clientX
}

function onPointerUp(event) {
  dragging = false
  if (event?.pointerId !== undefined && renderer?.domElement.hasPointerCapture(event.pointerId)) renderer.domElement.releasePointerCapture(event.pointerId)
}

function animate() {
  animationFrame = requestAnimationFrame(animate)
  const time = performance.now() * 0.001
  if (modelPivot && !reducedMotion.value && !dragging) modelPivot.rotation.y += 0.0032
  if (particleField) {
    const positions = particleField.geometry.attributes.position.array
    for (let i = 0; i < particleVelocity.length; i += 1) {
      const yIndex = i * 3 + 1
      positions[yIndex] += (reducedMotion.value ? particleVelocity[i] * 0.12 : particleVelocity[i]) * 0.016
      if (positions[yIndex] > 3.7) positions[yIndex] = 0.18
    }
    particleField.geometry.attributes.position.needsUpdate = true
    particleField.rotation.y = reducedMotion.value ? 0 : time * 0.018
  }
  if (scanLine) {
    scanLine.position.y = 0.5 + ((time * (reducedMotion.value ? 0.03 : 0.11)) % 2.65)
    scanLine.material.opacity = reducedMotion.value ? 0.16 : 0.28 + Math.sin(time * 0.55) * 0.05
  }
  orbitRings.forEach((ring, index) => { ring.rotation.z += (reducedMotion.value ? 0.0006 : 0.0022) * (index % 2 ? -1 : 1) })
  renderer?.render(scene, camera)
}

function resizeScene() {
  if (!renderer || !canvasHost.value) return
  const width = canvasHost.value.clientWidth
  const height = Math.max(canvasHost.value.clientHeight, 1)
  const aspect = width / height
  const fov = THREE.MathUtils.degToRad(camera.fov * 0.5)
  const verticalDistance = 3.42 / (2 * Math.tan(fov))
  const horizontalDistance = 3.82 / (2 * Math.tan(fov) * Math.max(aspect, 0.1))
  camera.aspect = aspect
  camera.position.z = Math.max(verticalDistance, horizontalDistance)
  camera.lookAt(0, 1.42, 0)
  camera.updateProjectionMatrix()
  renderer.setSize(width, height, true)
}

function disposeObject(object) {
  object?.traverse?.((child) => {
    child.geometry?.dispose?.()
    if (Array.isArray(child.material)) child.material.forEach((material) => material.dispose?.())
    else child.material?.dispose?.()
  })
}

onMounted(async () => {
  await nextTick()
  initScene()
})

onBeforeUnmount(() => {
  cancelAnimationFrame(animationFrame)
  resizeObserver?.disconnect()
  renderer?.domElement?.removeEventListener('pointerdown', onPointerDown)
  renderer?.domElement?.removeEventListener('pointermove', onPointerMove)
  renderer?.domElement?.removeEventListener('pointerup', onPointerUp)
  renderer?.domElement?.removeEventListener('pointercancel', onPointerUp)
  disposeObject(scene)
  renderer?.dispose?.()
})

watch(() => props.vitals, () => {}, { deep: true })
</script>

<style scoped lang="scss">
.portrait-hologram {
  position: relative;
  min-height: 300px;
  overflow: hidden;
  border: 1px solid rgba(37, 124, 171, .7);
  border-radius: 8px;
  background: radial-gradient(circle at 50% 43%, rgba(20, 75, 101, .34), transparent 39%), #071827;
  box-shadow: inset 0 0 80px rgba(0, 168, 227, .08), 0 16px 46px rgba(0, 0, 0, .28);
}
.hologram-grid { position: absolute; inset: 0; opacity: .22; background-image: linear-gradient(rgba(55, 171, 218, .18) 1px, transparent 1px), linear-gradient(90deg, rgba(55, 171, 218, .18) 1px, transparent 1px); background-size: 42px 42px; mask-image: linear-gradient(to bottom, rgba(0, 0, 0, .9), transparent 86%); }
.hologram-vignette { position: absolute; inset: 0; pointer-events: none; box-shadow: inset 0 0 70px rgba(0, 0, 0, .52); }
.hologram-header, .hologram-footer { position: absolute; z-index: 2; left: 16px; right: 16px; display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.hologram-header { top: 14px; }
.hologram-footer { bottom: 12px; color: #7c9ab1; font-size: 11px; }
.hologram-header strong { display: block; margin-top: 4px; color: #e9f7ff; font-size: 15px; }
.hologram-eyebrow { color: #3bc8f5; font-size: 9px; font-weight: 700; letter-spacing: .16em; }
.hologram-motion-toggle { display: inline-flex; align-items: center; gap: 5px; color: #a2bfd1; font-size: 11px; }
.hologram-motion-toggle input { accent-color: #2eb8e9; }
.hologram-canvas { position: absolute; inset: 40px 8px 32px; }
.hologram-canvas canvas { display: block; width: 100%; height: 100%; cursor: grab; }
.hologram-canvas canvas:active { cursor: grabbing; }
.hologram-loading, .hologram-error { position: absolute; inset: 50% auto auto 50%; z-index: 1; transform: translate(-50%, -50%); display: grid; gap: 5px; width: min(80%, 240px); padding: 12px 14px; border: 1px solid rgba(66, 202, 239, .32); border-radius: 6px; background: rgba(4, 28, 46, .84); color: #9fc4d5; text-align: center; font-size: 11px; }
.hologram-error strong { color: #ffd08a; font-size: 12px; }
.hologram-label { position: absolute; z-index: 2; display: grid; grid-template-columns: auto auto; column-gap: 5px; align-items: center; padding: 5px 7px; border: 1px solid rgba(55, 190, 226, .3); border-radius: 5px; background: rgba(4, 28, 46, .76); box-shadow: 0 8px 22px rgba(0, 0, 0, .18); font-size: 10px; pointer-events: none; }
.hologram-label b { color: #bfeeff; font-size: 10px; }
.hologram-label strong { color: #f0fbff; font-size: 16px; line-height: 1; }
.hologram-label small { grid-column: 2; color: #72a0b8; font-size: 9px; }
.hologram-dot { grid-row: 1 / span 2; width: 7px; height: 7px; border-radius: 50%; background: #ff718c; box-shadow: 0 0 10px currentColor; }
.hologram-dot--heart { background: #ff718c; }
.hologram-dot--oxygen { background: #56e1b1; }
.hologram-dot--temp { background: #ffc565; }
.hologram-label--heart { top: 39%; right: 12%; }
.hologram-label--oxygen { top: 42%; left: 10%; }
.hologram-label--temp { top: 59%; right: 13%; }
.hologram-status { color: #65d9bf; }
.hologram-status.is-warning { color: #ffc064; }
.hologram-status.is-offline, .hologram-status.is-unknown { color: #91a4b7; }
@media (max-width: 720px) {
  .portrait-hologram { min-height: 280px; }
  .hologram-label { transform: scale(.88); }
  .hologram-label--heart { right: 3%; }
  .hologram-label--oxygen { left: 2%; }
  .hologram-label--temp { right: 4%; }
}
</style>

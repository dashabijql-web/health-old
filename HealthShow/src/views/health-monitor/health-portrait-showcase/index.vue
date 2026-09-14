<template>
  <div class="portrait-showcase">
    <header class="portrait-topbar">
      <div class="portrait-title"><span class="title-mark"></span><span>人员健康画像</span></div>
      <form class="portrait-search" @submit.prevent="submitSearch">
        <span class="search-icon" aria-hidden="true"></span>
        <input
          v-model="query"
          type="search"
          placeholder="输入姓名、工号、手机号或 IMEI"
          aria-label="搜索人员"
          @focus="showSuggestions = true"
        />
        <button type="submit" :disabled="searching">{{ searching ? '查询中' : '查询' }}</button>
        <div v-if="showSuggestions && suggestions.length" class="search-suggestions">
          <button v-for="item in suggestions" :key="item.empCode || item.imei" type="button" @click="selectPerson(item)">
            <strong>{{ item.empName || '未命名人员' }}</strong>
            <span>{{ item.empCode || item.imei || '暂无编号' }} · {{ item.deptName || '未分配部门' }}</span>
          </button>
        </div>
      </form>
      <div class="portrait-hint">选中后进入完整健康画像，可继续联系或应急处置</div>
    </header>

    <main class="portrait-main">
      <section class="scene-panel" aria-label="360度人体健康可视化">
        <div class="scene-grid"></div>
        <div class="scene-vignette"></div>
        <div class="scene-header">
          <div><span class="eyebrow">LIVE BODY MONITOR</span><strong>{{ person.empName || '演示人员' }}</strong></div>
          <div class="scene-actions">
            <span class="connection-dot"></span><span>{{ loadingModel ? '模型加载中' : '实时连接' }}</span>
            <label class="motion-toggle"><input v-model="reducedMotion" type="checkbox" /><span>减少动态</span></label>
          </div>
        </div>

        <div ref="canvasHost" class="canvas-host"></div>
        <div class="model-label model-label--head"><span class="hotspot-dot"></span><b>体征信号</b><small>实时同步</small></div>
        <div class="model-label model-label--heart"><span class="hotspot-dot hotspot-dot--heart"></span><b>心率</b><strong>{{ metrics.heartRate.value }}</strong><small>BPM</small></div>
        <div class="model-label model-label--oxygen"><span class="hotspot-dot hotspot-dot--oxygen"></span><b>血氧</b><strong>{{ metrics.bloodOxygen.value }}</strong><small>%</small></div>
        <div class="model-label model-label--temp"><span class="hotspot-dot hotspot-dot--temp"></span><b>体温</b><strong>{{ metrics.temperature.value }}</strong><small>°C</small></div>
        <div class="scene-footer"><span>采集时间 {{ lastCollected }}</span><span class="scene-status">{{ statusText }}</span></div>
      </section>

      <aside class="telemetry-panel">
        <div class="person-summary">
          <div class="avatar">{{ (person.empName || '演').charAt(0) }}</div>
          <div><span class="eyebrow">HEALTH PROFILE</span><h1>{{ person.empName || '演示人员' }}</h1><p>{{ person.empCode || 'DEMO-001' }} · {{ person.deptName || '健康监测中心' }}</p></div>
        </div>
        <div class="metric-list">
          <article v-for="metric in metricList" :key="metric.key" class="metric-row">
            <div class="metric-icon" :class="`metric-icon--${metric.tone}`">{{ metric.symbol }}</div>
            <div class="metric-copy"><span>{{ metric.label }}</span><small>{{ metric.note }}</small></div>
            <div class="metric-value"><strong>{{ metric.value }}</strong><em>{{ metric.unit }}</em></div>
          </article>
        </div>
        <div class="state-panel" :class="`state-panel--${overallTone}`"><span class="state-dot"></span><div><small>当前状态</small><strong>{{ statusText }}</strong><p>{{ statusDetail }}</p></div></div>
        <div class="panel-footer"><span>数据来源：{{ dataSourceText }}</span><button type="button" class="profile-button" @click="openProfile">查看完整画像</button></div>
      </aside>
    </main>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as THREE from 'three'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'
import { searchEmployeesForCommand } from '@/api/employee'
import { getHealthPortrait } from '@/api/health-portrait'

const router = useRouter()
const canvasHost = ref(null)
const query = ref('')
const suggestions = ref([])
const showSuggestions = ref(false)
const searching = ref(false)
const loadingModel = ref(true)
const reducedMotion = ref(false)
const person = reactive({ empName: '', empCode: '', deptName: '', imei: '' })
const portraitData = ref(null)
const lastCollected = ref('等待采集')
let scene; let camera; let renderer; let model; let modelPivot; let animationFrame; let resizeObserver
let particleField; let scanLine; let orbitAccents = []
let particleVelocity
let draggingModel = false; let lastPointerX = 0
const hotspots = []
const desktopViewHeight = 3.42
const minimumViewWidth = 3.82

const demo = { heartRate: 78, bloodOxygen: 98, temperature: 36.5, bloodPressure: '122/78' }
const metrics = reactive({ heartRate: { value: demo.heartRate }, bloodOxygen: { value: demo.bloodOxygen }, temperature: { value: demo.temperature }, bloodPressure: { value: demo.bloodPressure } })
const metricList = computed(() => [
  { key: 'heartRate', label: '心率', value: metrics.heartRate.value, unit: 'BPM', note: '节律稳定', tone: 'cyan', symbol: '♥' },
  { key: 'bloodOxygen', label: '血氧饱和度', value: metrics.bloodOxygen.value, unit: '%', note: '供氧良好', tone: 'green', symbol: 'O₂' },
  { key: 'temperature', label: '体温', value: metrics.temperature.value, unit: '°C', note: '体温正常', tone: 'amber', symbol: '°' },
  { key: 'bloodPressure', label: '血压', value: metrics.bloodPressure.value, unit: 'mmHg', note: '收缩 / 舒张', tone: 'blue', symbol: '↕' }
])
const overallTone = computed(() => portraitData.value?.status === 'warning' ? 'warning' : 'normal')
const statusText = computed(() => overallTone.value === 'warning' ? '需要关注' : (portraitData.value ? '状态稳定' : '演示状态'))
const statusDetail = computed(() => overallTone.value === 'warning' ? '存在需要进一步核实的体征信号' : (portraitData.value ? '未发现需要立即处置的异常体征' : '当前展示使用演示数据，接入人员后自动替换'))
const dataSourceText = computed(() => portraitData.value ? '实时健康数据' : '演示数据')

async function submitSearch() {
  const text = query.value.trim()
  if (!text) return
  searching.value = true
  try {
    const response = await searchEmployeesForCommand(text, 8)
    const rows = response?.data?.rows || response?.data?.list || response?.data || []
    suggestions.value = Array.isArray(rows) ? rows : []
    if (suggestions.value.length === 1) await selectPerson(suggestions.value[0])
  } catch (error) {
    suggestions.value = []
  } finally {
    searching.value = false
    showSuggestions.value = true
  }
}

async function selectPerson(item) {
  showSuggestions.value = false
  query.value = item.empName || item.empCode || item.imei || ''
  Object.assign(person, { empName: item.empName || '', empCode: item.empCode || '', deptName: item.deptName || '', imei: item.imei || '' })
  if (!person.empCode) return
  try {
    const response = await getHealthPortrait(person.empCode)
    portraitData.value = response?.data || null
    applyPortrait(portraitData.value)
  } catch (error) {
    portraitData.value = null
  }
}

function applyPortrait(data) {
  if (!data) return
  const latest = data.current || data.latest || data.vitals || data
  metrics.heartRate.value = latest.heartRate ?? latest.heart_rate ?? demo.heartRate
  metrics.bloodOxygen.value = latest.bloodOxygen ?? latest.blood_oxygen ?? demo.bloodOxygen
  metrics.temperature.value = latest.temperature ?? demo.temperature
  metrics.bloodPressure.value = latest.bloodPressure ?? latest.blood_pressure ?? demo.bloodPressure
  lastCollected.value = latest.collectedAt || latest.measureTime || '刚刚同步'
}

function openProfile() {
  if (person.empCode) router.push({ path: '/health-monitor/health-portrait', query: { empCode: person.empCode } })
}

function initScene() {
  scene = new THREE.Scene()
  camera = new THREE.PerspectiveCamera(32, 1, 0.1, 100)
  camera.position.set(0, 1.48, 6)
  camera.lookAt(0, 1.42, 0)
  renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: 'high-performance' })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 1.6))
  renderer.setClearColor(0x000000, 0)
  canvasHost.value.appendChild(renderer.domElement)
  renderer.domElement.addEventListener('pointerdown', (event) => { draggingModel = true; lastPointerX = event.clientX; renderer.domElement.setPointerCapture(event.pointerId) })
  renderer.domElement.addEventListener('pointermove', (event) => {
    if (!draggingModel || !modelPivot) return
    modelPivot.rotation.y += (event.clientX - lastPointerX) * 0.008
    lastPointerX = event.clientX
  })
  renderer.domElement.addEventListener('pointerup', (event) => { draggingModel = false; renderer.domElement.releasePointerCapture(event.pointerId) })
  renderer.domElement.addEventListener('pointercancel', () => { draggingModel = false })
  scene.add(new THREE.HemisphereLight(0x9bdcff, 0x061120, 1.6))
  const rim = new THREE.PointLight(0x35c7ff, 3.2, 8); rim.position.set(2.2, 3.1, 2.5); scene.add(rim)
  const fill = new THREE.PointLight(0x2655c8, 2, 7); fill.position.set(-2.5, 1.2, -2); scene.add(fill)
  addPlatform()
  addOrbitAccents()
  addParticleField()
  addScanLine()
  loadModel()
  resizeObserver = new ResizeObserver(resizeScene)
  resizeObserver.observe(canvasHost.value)
  resizeScene()
  animate()
}

function addOrbitAccents() {
  const accents = [
    { radius: 1.32, y: 1.52, tilt: 0.08, color: 0x1c94c9, opacity: 0.42 },
    { radius: 1.08, y: 2.12, tilt: -0.28, color: 0x4bd7ef, opacity: 0.28 },
    { radius: 0.88, y: 1.04, tilt: 0.24, color: 0x55d6b7, opacity: 0.24 }
  ]
  accents.forEach((item) => {
    const ring = new THREE.Mesh(
      new THREE.TorusGeometry(item.radius, 0.012, 8, 96),
      new THREE.MeshBasicMaterial({ color: item.color, transparent: true, opacity: item.opacity })
    )
    ring.rotation.x = Math.PI / 2 + item.tilt
    ring.position.y = item.y
    scene.add(ring)
    orbitAccents.push(ring)
  })
}

function addParticleField() {
  const count = 170
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
    colors[i * 3] = color.r; colors[i * 3 + 1] = color.g; colors[i * 3 + 2] = color.b
    particleVelocity[i] = 0.035 + Math.random() * 0.07
  }
  const geometry = new THREE.BufferGeometry()
  geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3))
  geometry.setAttribute('color', new THREE.BufferAttribute(colors, 3))
  const material = new THREE.PointsMaterial({ size: 0.042, vertexColors: true, transparent: true, opacity: 0.76, depthWrite: false, sizeAttenuation: true })
  particleField = new THREE.Points(geometry, material)
  scene.add(particleField)
}

function addScanLine() {
  const geometry = new THREE.BoxGeometry(2.8, 0.009, 0.012)
  const material = new THREE.MeshBasicMaterial({ color: 0x42d9ff, transparent: true, opacity: 0.34 })
  scanLine = new THREE.Mesh(geometry, material)
  scanLine.position.set(0, 0.62, 0.35)
  scene.add(scanLine)
}

function addPlatform() {
  const group = new THREE.Group(); group.position.y = 0.03; scene.add(group)
  const base = new THREE.Mesh(new THREE.CylinderGeometry(1.62, 1.76, 0.12, 96), new THREE.MeshStandardMaterial({ color: 0x071728, metalness: 0.8, roughness: 0.32, emissive: 0x061a31, emissiveIntensity: 0.6 }))
  group.add(base)
  ;[1.05, 0.78, 0.52].forEach((radius, index) => {
    const ring = new THREE.Mesh(new THREE.TorusGeometry(radius, 0.018, 8, 96), new THREE.MeshBasicMaterial({ color: index === 1 ? 0x58d7f4 : 0x1674a8, transparent: true, opacity: index === 1 ? 0.82 : 0.58 }))
    ring.rotation.x = Math.PI / 2; ring.position.y = 0.085 + index * 0.018; group.add(ring)
  })
  const glow = new THREE.Mesh(new THREE.CircleGeometry(0.39, 64), new THREE.MeshBasicMaterial({ color: 0x1d8fff, transparent: true, opacity: 0.12 }))
  glow.rotation.x = -Math.PI / 2; glow.position.y = 0.095; group.add(glow)
}

function loadModel() {
  const loader = new GLTFLoader()
  loader.load('/models/wireframe_man.glb', (gltf) => {
    model = gltf.scene
    const box = new THREE.Box3().setFromObject(model)
    const height = Math.max(box.max.y - box.min.y, 0.1)
    const scale = 2.48 / height
    model.scale.setScalar(scale)
    const after = new THREE.Box3().setFromObject(model)
    model.position.set(-((after.min.x + after.max.x) / 2), 0.18 - after.min.y, -((after.min.z + after.max.z) / 2))
    model.traverse((child) => {
      if (!child.isMesh) return
      child.material = new THREE.MeshStandardMaterial({ color: 0x35d9ff, emissive: 0x0c9fc5, emissiveIntensity: 1.42, metalness: 0.2, roughness: 0.42, transparent: true, opacity: 0.93 })
    })
    modelPivot = new THREE.Group()
    modelPivot.add(model)
    scene.add(modelPivot)
    addHotspot(0.28, 1.86, 0.28, 0x59e8ff)
    addHotspot(0.19, 1.53, 0.22, 0xff6c86)
    addHotspot(-0.24, 1.48, 0.18, 0x57e7b0)
    addHotspot(0.3, 1.17, 0.16, 0xffc260)
    loadingModel.value = false
  }, undefined, () => { loadingModel.value = false })
}

function addHotspot(x, y, z, color) {
  const dot = new THREE.Mesh(new THREE.SphereGeometry(0.035, 16, 16), new THREE.MeshBasicMaterial({ color, transparent: true, opacity: 0.9 }))
  dot.position.set(x, y, z); scene.add(dot); hotspots.push(dot)
}

function animate() {
  animationFrame = requestAnimationFrame(animate)
  const t = performance.now() * 0.001
  if (modelPivot && !reducedMotion.value && !draggingModel) modelPivot.rotation.y += 0.0032
  if (particleField) {
    const positions = particleField.geometry.attributes.position.array
    for (let i = 0; i < particleVelocity.length; i += 1) {
      const yIndex = i * 3 + 1
      positions[yIndex] += (reducedMotion.value ? particleVelocity[i] * 0.12 : particleVelocity[i]) * 0.016
      if (positions[yIndex] > 3.7) positions[yIndex] = 0.18
    }
    particleField.geometry.attributes.position.needsUpdate = true
    particleField.rotation.y = reducedMotion.value ? 0 : t * 0.018
  }
  if (scanLine) {
    scanLine.position.y = 0.5 + ((t * (reducedMotion.value ? 0.03 : 0.11)) % 2.65)
    scanLine.material.opacity = reducedMotion.value ? 0.16 : 0.28 + Math.sin(t * 0.55) * 0.05
  }
  orbitAccents.forEach((ring, index) => {
    ring.rotation.z += (reducedMotion.value ? 0.0006 : 0.0022) * (index % 2 ? -1 : 1)
  })
  hotspots.forEach((dot, index) => { dot.scale.setScalar(reducedMotion.value ? 1 : 1 + Math.sin(t * 1.4 + index) * 0.12) })
  renderer.render(scene, camera)
}

function resizeScene() {
  if (!renderer || !canvasHost.value) return
  const width = canvasHost.value.clientWidth; const height = canvasHost.value.clientHeight
  const aspect = width / Math.max(height, 1)
  const halfVerticalFov = THREE.MathUtils.degToRad(camera.fov * 0.5)
  const verticalDistance = desktopViewHeight / (2 * Math.tan(halfVerticalFov))
  const horizontalDistance = minimumViewWidth / (2 * Math.tan(halfVerticalFov) * Math.max(aspect, 0.1))
  camera.aspect = aspect
  camera.position.z = Math.max(verticalDistance, horizontalDistance)
  camera.lookAt(0, 1.42, 0)
  camera.updateProjectionMatrix()
  renderer.setSize(width, height, true)
}

onMounted(async () => { await nextTick(); initScene() })
onBeforeUnmount(() => { cancelAnimationFrame(animationFrame); resizeObserver?.disconnect(); renderer?.dispose() })
</script>

<style scoped>
.portrait-showcase{min-height:calc(100vh - 50px);padding:18px 18px 24px;color:#d9ecff;background:#06111e;box-sizing:border-box}.portrait-topbar{position:relative;display:grid;grid-template-columns:220px minmax(360px,1fr) minmax(260px,420px);align-items:center;gap:18px;max-width:1700px;margin:0 auto 16px;padding:10px 14px;border:1px solid rgba(35,145,202,.55);border-radius:8px;background:rgba(5,24,43,.88);box-shadow:0 10px 28px rgba(0,0,0,.2)}.portrait-title{display:flex;align-items:center;gap:12px;font-size:20px;font-weight:700;white-space:nowrap}.title-mark{width:18px;height:18px;border:1px solid #20adf0;border-radius:50%;position:relative}.title-mark:after{content:"";position:absolute;right:-5px;bottom:-3px;width:7px;height:1px;background:#20adf0;transform:rotate(45deg)}.portrait-search{position:relative;display:flex;align-items:center;min-height:56px;padding:0 12px;border:2px solid #1b5da7;border-radius:8px;background:#0c2944}.portrait-search input{flex:1;min-width:0;border:0;outline:0;color:#eef8ff;background:transparent;font-size:22px}.portrait-search input::placeholder{color:#e3edf7;opacity:.94}.search-icon{width:18px;height:18px;margin:0 13px 0 3px;border:2px solid #22aef2;border-radius:50%;position:relative;flex:none}.search-icon:after{content:"";position:absolute;right:-6px;bottom:-4px;width:7px;height:2px;background:#22aef2;transform:rotate(45deg)}.portrait-search button{height:34px;padding:0 13px;border:1px solid rgba(78,213,255,.58);border-radius:5px;color:#c9f4ff;background:#0f4365;cursor:pointer}.portrait-search button:disabled{opacity:.65;cursor:wait}.search-suggestions{position:absolute;z-index:5;top:64px;left:0;right:0;display:grid;gap:2px;padding:6px;border:1px solid #1c5b91;border-radius:7px;background:#092139;box-shadow:0 16px 30px rgba(0,0,0,.4)}.search-suggestions button{display:flex;flex-direction:column;align-items:flex-start;gap:3px;height:auto;padding:10px 12px;border:0;background:transparent;text-align:left}.search-suggestions button:hover{background:#103653}.search-suggestions strong{color:#e9f7ff;font-size:14px}.search-suggestions span{color:#8cacbf;font-size:12px}.portrait-hint{color:#8aa9c3;font-size:13px;line-height:1.45}.portrait-main{display:grid;grid-template-columns:minmax(0,1fr) 330px;gap:16px;max-width:1700px;min-height:calc(100vh - 145px);margin:0 auto}.scene-panel{position:relative;min-height:650px;overflow:hidden;border:1px solid rgba(37,124,171,.7);border-radius:8px;background:radial-gradient(circle at 50% 43%,rgba(20,75,101,.34),transparent 39%),#071827;box-shadow:inset 0 0 80px rgba(0,168,227,.08),0 16px 46px rgba(0,0,0,.28)}.scene-grid{position:absolute;inset:0;opacity:.22;background-image:linear-gradient(rgba(55,171,218,.18) 1px,transparent 1px),linear-gradient(90deg,rgba(55,171,218,.18) 1px,transparent 1px);background-size:54px 54px;mask-image:linear-gradient(to bottom,rgba(0,0,0,.9),transparent 86%)}.scene-grid:after{content:"";position:absolute;left:0;right:0;top:34%;height:1px;background:rgba(52,191,236,.3);box-shadow:0 110px rgba(52,191,236,.16),0 220px rgba(52,191,236,.1)}.scene-vignette{position:absolute;inset:0;box-shadow:inset 0 0 100px rgba(0,0,0,.52);pointer-events:none}.scene-header,.scene-footer{position:absolute;z-index:2;left:24px;right:24px;display:flex;align-items:center;justify-content:space-between}.scene-header{top:20px}.scene-footer{bottom:18px;color:#7c9ab1;font-size:12px}.scene-header strong{display:block;margin-top:6px;color:#e9f7ff;font-size:17px}.eyebrow{color:#3bc8f5;font-size:10px;font-weight:700;letter-spacing:.16em}.scene-actions{display:flex;align-items:center;gap:7px;color:#8fb3cc;font-size:12px}.connection-dot,.state-dot{width:8px;height:8px;border-radius:50%;background:#45dfbf;box-shadow:0 0 10px rgba(69,223,191,.8)}.motion-toggle{display:inline-flex;align-items:center;gap:6px;margin-left:10px;color:#a2bfd1}.motion-toggle input{accent-color:#2eb8e9}.canvas-host{position:absolute;inset:54px 16px 52px}.canvas-host canvas{display:block;width:100%;height:100%}.model-label{position:absolute;z-index:2;display:grid;grid-template-columns:auto auto;column-gap:7px;align-items:center;padding:8px 10px;border:1px solid rgba(55,190,226,.3);border-radius:5px;background:rgba(4,28,46,.76);box-shadow:0 8px 22px rgba(0,0,0,.18);font-size:11px;pointer-events:none}.model-label b{color:#bfeeff;font-size:11px}.model-label strong{color:#f0fbff;font-size:20px;line-height:1}.model-label small{grid-column:2;color:#72a0b8;font-size:10px}.hotspot-dot{grid-row:1 / span 2;width:8px;height:8px;border-radius:50%;background:#5be6ff;box-shadow:0 0 11px #5be6ff}.hotspot-dot--heart{background:#ff718c;box-shadow:0 0 11px #ff718c}.hotspot-dot--oxygen{background:#56e1b1;box-shadow:0 0 11px #56e1b1}.hotspot-dot--temp{background:#ffc565;box-shadow:0 0 11px #ffc565}.model-label--head{top:25%;left:57%}.model-label--heart{top:44%;left:60%}.model-label--oxygen{top:43%;left:22%}.model-label--temp{top:59%;left:59%}.scene-status{color:#65d9bf}.telemetry-panel{display:flex;flex-direction:column;gap:16px;padding:20px;border:1px solid rgba(41,118,160,.56);border-radius:8px;background:#081b2c;box-shadow:0 16px 42px rgba(0,0,0,.2)}.person-summary{display:flex;align-items:center;gap:13px;padding-bottom:16px;border-bottom:1px solid rgba(72,133,163,.28)}.avatar{display:grid;place-items:center;width:48px;height:48px;border:1px solid rgba(64,206,242,.5);border-radius:50%;color:#91ecff;background:#0d3a58;font-size:21px;font-weight:700}.person-summary h1{margin:5px 0 2px;color:#eff9ff;font-size:23px}.person-summary p{margin:0;color:#799cb6;font-size:12px}.metric-list{display:grid;gap:8px}.metric-row{display:grid;grid-template-columns:34px 1fr auto;align-items:center;gap:10px;padding:12px 0;border-bottom:1px solid rgba(72,133,163,.2)}.metric-icon{display:grid;place-items:center;width:30px;height:30px;border-radius:6px;color:#9eefff;background:#103c57;font-size:13px;font-weight:700}.metric-icon--green{color:#9ff4ce;background:#104c49}.metric-icon--amber{color:#ffdb92;background:#4d3d25}.metric-icon--blue{color:#b4c7ff;background:#1c315d}.metric-copy span{display:block;color:#dff1fc;font-size:13px}.metric-copy small{display:block;margin-top:3px;color:#7697ad;font-size:11px}.metric-value{text-align:right}.metric-value strong{display:block;color:#f4fbff;font-size:21px}.metric-value em{color:#6f9bb3;font-size:10px;font-style:normal}.state-panel{display:flex;gap:11px;padding:14px;border:1px solid rgba(62,210,183,.28);border-radius:6px;background:rgba(11,58,61,.34)}.state-panel--warning{border-color:rgba(255,173,96,.4);background:rgba(73,48,25,.32)}.state-panel--warning .state-dot{background:#ffc064;box-shadow:0 0 10px #ffc064}.state-panel small{display:block;color:#83a9b8;font-size:11px}.state-panel strong{display:block;margin:3px 0;color:#a4f2d9;font-size:17px}.state-panel--warning strong{color:#ffd48b}.state-panel p{margin:0;color:#82a6b6;font-size:11px;line-height:1.45}.panel-footer{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-top:auto;color:#6d95aa;font-size:11px}.profile-button{padding:8px 10px;border:1px solid rgba(67,194,228,.45);border-radius:5px;color:#a8eaff;background:transparent;cursor:pointer}.profile-button:hover{background:rgba(24,104,136,.28)}
@media (max-width:1100px){.portrait-topbar{grid-template-columns:1fr}.portrait-hint{display:none}.portrait-main{grid-template-columns:1fr}.telemetry-panel{min-height:auto}.scene-panel{height:620px;min-height:580px}}@media (max-width:700px){.portrait-showcase{padding:10px}.portrait-search input{font-size:17px}.portrait-main{min-height:0}.scene-panel{height:560px;min-height:530px}.model-label{transform:scale(.88);transform-origin:center}.model-label--head{left:53%}.model-label--heart{left:55%}.model-label--oxygen{left:7%}.model-label--temp{left:52%}.scene-header,.scene-footer{left:14px;right:14px}.scene-actions>span:not(.connection-dot){display:none}}
.portrait-main{min-height:0}.scene-panel,.telemetry-panel{height:clamp(440px,calc(100dvh - 180px),720px);min-height:0;box-sizing:border-box}.telemetry-panel{overflow:hidden}.canvas-host{inset:48px 10px 40px}
@media (max-height:820px) and (min-width:1101px){.portrait-showcase{padding:12px 18px 14px}.portrait-topbar{margin-bottom:12px;padding:6px 12px}.portrait-search{min-height:48px}.portrait-main{gap:14px}.scene-panel,.telemetry-panel{height:clamp(440px,calc(100dvh - 150px),620px)}.telemetry-panel{gap:8px;padding:14px}.person-summary{padding-bottom:8px}.avatar{width:40px;height:40px;font-size:18px}.person-summary h1{margin-top:3px;font-size:19px}.metric-list{gap:2px}.metric-row{padding:7px 0}.metric-icon{width:28px;height:28px}.metric-value strong{font-size:18px}.state-panel{padding:9px}.state-panel strong{font-size:15px}.panel-footer{padding-top:2px}.scene-header{top:14px}.scene-footer{bottom:12px}.canvas-host{inset:40px 8px 34px}}
.canvas-host canvas{cursor:grab}.canvas-host canvas:active{cursor:grabbing}
</style>

<template>
  <div class="login-container">
    <!-- 星空背景 -->
    <div class="login-starfield">
      <span
        v-for="(s, i) in stars"
        :key="'s' + i"
        class="login-star"
        :style="{
          left: s.left + '%',
          top: s.top + '%',
          width: s.size + 'px',
          height: s.size + 'px',
          animationDuration: s.duration + 's',
          animationDelay: s.delay + 's'
        }"
      ></span>
    </div>

    <!-- 星云光晕 -->
    <div class="login-bg-glow login-bg-glow--a"></div>
    <div class="login-bg-glow login-bg-glow--b"></div>
    <div class="login-bg-glow login-bg-glow--c"></div>

    <!-- 斜向能量光束 -->
    <div class="login-beam login-beam--1"></div>
    <div class="login-beam login-beam--2"></div>
    <div class="login-beam login-beam--3"></div>

    <!-- 全息扫描核心 -->
    <div class="login-hologram">
      <span class="login-holo-ring login-holo-ring--1"></span>
      <span class="login-holo-ring login-holo-ring--2"></span>
      <span class="login-holo-ring login-holo-ring--3"></span>
      <span class="login-holo-sweep"></span>
      <span class="login-holo-core"></span>
    </div>

    <!-- 悬浮粒子 -->
    <span
      v-for="(p, i) in particles"
      :key="'p' + i"
      :class="['login-particle', p.tone]"
      :style="{
        left: p.left + '%',
        top: p.top + '%',
        width: p.size + 'px',
        height: p.size + 'px',
        opacity: p.opacity,
        animationDuration: p.duration + 's',
        animationDelay: p.delay + 's'
      }"
    ></span>

    <!-- 侧边数据流 -->
    <div class="login-data-stream login-data-stream--left">
      <div class="login-data-stream-track">
        <p v-for="(line, i) in dataLines" :key="'dl-' + i">{{ line }}</p>
        <p v-for="(line, i) in dataLines" :key="'dl2-' + i">{{ line }}</p>
      </div>
    </div>
    <div class="login-data-stream login-data-stream--right">
      <div class="login-data-stream-track">
        <p v-for="(line, i) in dataLines" :key="'dr-' + i">{{ line }}</p>
        <p v-for="(line, i) in dataLines" :key="'dr2-' + i">{{ line }}</p>
      </div>
    </div>

    <!-- HUD 边角 -->
    <span class="login-hud-corner login-hud-corner--tl"></span>
    <span class="login-hud-corner login-hud-corner--tr"></span>
    <span class="login-hud-corner login-hud-corner--bl"></span>
    <span class="login-hud-corner login-hud-corner--br"></span>

    <!-- 底部心电图动画条 -->
    <div class="login-ecg">
      <div class="login-ecg-track">
        <svg viewBox="0 0 400 60" class="login-ecg-svg" preserveAspectRatio="none">
          <polyline points="0,30 40,30 55,30 65,6 75,54 85,20 95,30 140,30 180,30 195,30 205,6 215,54 225,20 235,30 280,30 320,30 335,30 345,6 355,54 365,20 375,30 400,30" />
        </svg>
        <svg viewBox="0 0 400 60" class="login-ecg-svg" preserveAspectRatio="none">
          <polyline points="0,30 40,30 55,30 65,6 75,54 85,20 95,30 140,30 180,30 195,30 205,6 215,54 225,20 235,30 280,30 320,30 335,30 345,6 355,54 365,20 375,30 400,30" />
        </svg>
      </div>
    </div>

    <div
      ref="loginPanel"
      class="login-panel"
      :style="panelStyle"
      @mousemove="onPanelMouseMove"
      @mouseleave="onPanelMouseLeave"
    >
      <span class="login-panel-spotlight" :style="spotlightStyle"></span>
      <span class="login-panel-corner login-panel-corner--tl"></span>
      <span class="login-panel-corner login-panel-corner--tr"></span>
      <span class="login-panel-corner login-panel-corner--bl"></span>
      <span class="login-panel-corner login-panel-corner--br"></span>

      <div class="login-brand">
        <div class="login-brand-mark">
          <svg viewBox="0 0 24 24" width="26" height="26">
            <path
              class="login-brand-pulse-path"
              d="M3 12h3l2-6 4 12 2-6h7"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
        </div>
        <div class="login-brand-text">
          <h1 class="login-brand-title">职业健康监测管理系统</h1>
          <p class="login-brand-sub">Occupational Health Monitoring Platform</p>
        </div>
      </div>

      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        class="login-form"
        auto-complete="on"
        label-position="left"
      >
        <el-form-item prop="username">
          <div class="login-field">
            <span class="login-field-icon">
              <svg-icon icon-class="user" />
            </span>
            <el-input
              ref="username"
              v-model="loginForm.username"
              placeholder="用户名"
              name="username"
              type="text"
              tabindex="1"
              auto-complete="on"
            />
          </div>
        </el-form-item>

        <el-form-item prop="password">
          <div class="login-field">
            <span class="login-field-icon">
              <svg-icon icon-class="password" />
            </span>
            <el-input
              :key="passwordType"
              ref="password"
              v-model="loginForm.password"
              :type="passwordType"
              placeholder="密码"
              name="password"
              tabindex="2"
              auto-complete="on"
              @keyup.enter="handleLogin"
            />
            <span class="login-field-toggle" @click="showPwd">
              <svg-icon :icon-class="passwordType === 'password' ? 'eye' : 'eye-open'" />
            </span>
          </div>
        </el-form-item>

        <el-button
          :loading="loading"
          class="login-submit"
          type="primary"
          @click.prevent="handleLogin"
        >
          登 录
        </el-button>

        <div class="login-tips">
          <span>账号：admin</span>
          <span>密码：admin123</span>
        </div>

        <div class="login-status">
          <span class="login-status-dot"></span>
          系统在线 · 实时监测中
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules, InputInstance } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { useStore } from 'vuex'

defineOptions({ name: 'Login' })

interface LoginForm {
  username: string
  password: string
}

function createStars(count: number) {
  return Array.from({ length: count }, () => ({
    left: Math.random() * 100,
    top: Math.random() * 100,
    size: Math.random() * 2 + 1,
    duration: Math.random() * 3 + 2,
    delay: Math.random() * 4
  }))
}

function createParticles(count: number) {
  const tones = ['is-cyan', 'is-green']
  return Array.from({ length: count }, () => ({
    left: Math.random() * 100,
    top: Math.random() * 100,
    size: Math.random() * 2.4 + 1.2,
    opacity: Math.random() * 0.5 + 0.35,
    duration: Math.random() * 6 + 5,
    delay: Math.random() * 6,
    tone: tones[Math.floor(Math.random() * tones.length)]
  }))
}

function createDataLines(count: number) {
  const labels = ['SYS', 'NODE', 'TEMP', 'HR', 'SPO2', 'SIG', 'NET', 'PWR', 'CORE', 'SCAN']
  const states = ['OK', 'SYNC', 'IDLE', 'LIVE', 'INIT']
  return Array.from({ length: count }, () => {
    const label = labels[Math.floor(Math.random() * labels.length)]
    const hex = Math.floor(Math.random() * 0xffff).toString(16).toUpperCase().padStart(4, '0')
    const state = states[Math.floor(Math.random() * states.length)]
    return `${label}-${hex} :: ${state}`
  })
}

const route = useRoute()
const router = useRouter()
const store = useStore()
const loginPanel = ref<HTMLElement | null>(null)
const loginFormRef = ref<FormInstance | null>(null)
const password = ref<InputInstance | null>(null)
const loginForm = reactive<LoginForm>({ username: 'admin', password: 'admin123' })
const loginRules: FormRules<LoginForm> = {
  username: [{ required: true, trigger: 'blur', message: '请输入用户名' }],
  password: [{ required: true, trigger: 'blur', message: '请输入密码' }]
}
const loading = ref(false)
const passwordType = ref('password')
const redirect = ref<string>()
const stars = createStars(150)
const particles = createParticles(34)
const dataLines = createDataLines(22)
const tiltX = ref(0)
const tiltY = ref(0)
const spotlightX = ref(50)
const spotlightY = ref(50)
const spotlightOpacity = ref(0)

const panelStyle = computed(() => ({
  transform: `rotateX(${tiltX.value}deg) rotateY(${tiltY.value}deg)`
}))
const spotlightStyle = computed(() => ({
  background: `radial-gradient(circle at ${spotlightX.value}% ${spotlightY.value}%, rgba(62, 183, 255, 0.22), transparent 55%)`,
  opacity: spotlightOpacity.value
}))

watch(
  () => route.query.redirect,
  (value) => {
    const nextValue = Array.isArray(value) ? value[0] : value
    redirect.value = typeof nextValue === 'string' ? nextValue : undefined
  },
  { immediate: true }
)

function onPanelMouseMove(event: MouseEvent) {
  const rect = loginPanel.value?.getBoundingClientRect()
  if (!rect) return
  const px = (event.clientX - rect.left) / rect.width
  const py = (event.clientY - rect.top) / rect.height
  tiltY.value = (px - 0.5) * 10
  tiltX.value = (0.5 - py) * 10
  spotlightX.value = px * 100
  spotlightY.value = py * 100
  spotlightOpacity.value = 1
}

function onPanelMouseLeave() {
  tiltX.value = 0
  tiltY.value = 0
  spotlightOpacity.value = 0
}

function showPwd() {
  passwordType.value = passwordType.value === 'password' ? '' : 'password'
  void nextTick(() => password.value?.focus())
}

async function handleLogin() {
  const valid = await loginFormRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await store.dispatch('user/login', loginForm)
    const safePath = redirect.value && redirect.value !== '/404' && redirect.value !== '/login'
      ? redirect.value
      : '/health-monitor/dashboard'
    loading.value = false
    await router.push({ path: safePath })
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void import('@/layout/index.vue')
  void import('@/views/health-monitor/dashboard/index.vue')
})
</script>

<!-- 非 scoped 样式：覆盖 Element Plus 组件内部结构 -->
<style lang="scss">
.login-container {
  .login-field .el-input__wrapper {
    background: transparent !important;
    border: 0 !important;
    border-radius: 0 !important;
    box-shadow: none !important;
    padding: 0 !important;
    height: 46px;
    flex: 1;
  }

  .login-field .el-input__inner {
    background: transparent !important;
    border: 0 !important;
    -webkit-appearance: none;
    border-radius: 0;
    padding: 0 6px;
    color: var(--text-strong) !important;
    height: 46px;
    caret-color: var(--accent-primary);
    font-size: 15px;

    &:-webkit-autofill {
      box-shadow: 0 0 0 1000px rgba(13, 40, 71, 0.9) inset !important;
      -webkit-text-fill-color: var(--text-strong) !important;
    }
  }

  .el-form-item {
    margin-bottom: 20px;
  }

  .el-form-item__error {
    color: var(--accent-danger);
    padding-top: 4px;
  }

  .login-submit.el-button--primary {
    background: linear-gradient(135deg, var(--accent-success) 0%, var(--accent-primary) 100%) !important;
    border: none !important;
    box-shadow: 0 12px 24px -8px rgba(62, 183, 255, 0.4) !important;

    &:hover {
      filter: brightness(1.08);
    }
  }
}
</style>

<!-- scoped 样式：登录页专属布局与视觉 -->
<style lang="scss" scoped>
@keyframes starTwinkle {
  0%, 100% { opacity: 0.15; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1.15); }
}

@keyframes beamPass {
  0% { left: -10%; opacity: 0; }
  12% { opacity: 0.6; }
  50% { opacity: 0.3; }
  88% { opacity: 0.6; }
  100% { left: 110%; opacity: 0; }
}

@keyframes holoSpin {
  from { transform: translate(-50%, -50%) rotate(0deg); }
  to { transform: translate(-50%, -50%) rotate(360deg); }
}

@keyframes holoSpinReverse {
  from { transform: translate(-50%, -50%) rotate(360deg); }
  to { transform: translate(-50%, -50%) rotate(0deg); }
}

@keyframes holoSweep {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

@keyframes holoCorePulse {
  0%, 100% { transform: translate(-50%, -50%) scale(1); opacity: 0.6; }
  50% { transform: translate(-50%, -50%) scale(1.8); opacity: 0.15; }
}

@keyframes dataScroll {
  from { transform: translateY(0); }
  to { transform: translateY(-50%); }
}

@keyframes particleFloat {
  0%, 100% { transform: translateY(0) scale(1); }
  50% { transform: translateY(-16px) scale(1.15); }
}

@keyframes glowDrift {
  0%, 100% { transform: translate(0, 0); }
  50% { transform: translate(30px, -20px); }
}

@keyframes ecgScroll {
  from { transform: translateX(0); }
  to { transform: translateX(-50%); }
}

@keyframes iconPulse {
  0% { transform: scale(1); opacity: 0.8; }
  100% { transform: scale(1.7); opacity: 0; }
}

@keyframes iconBreathe {
  0%, 100% { stroke-width: 2; filter: drop-shadow(0 0 0 rgba(62, 183, 255, 0)); }
  50% { stroke-width: 2.6; filter: drop-shadow(0 0 4px rgba(62, 183, 255, 0.8)); }
}

@keyframes titleShift {
  from { background-position: 0% 50%; }
  to { background-position: 100% 50%; }
}

@keyframes panelGlow {
  from { box-shadow: var(--shadow-elevated), var(--shadow-inner), 0 0 0 rgba(62, 183, 255, 0); }
  to { box-shadow: var(--shadow-elevated), var(--shadow-inner), 0 0 46px rgba(62, 183, 255, 0.28); }
}

@keyframes cornerPulse {
  0%, 100% { opacity: 0.35; }
  50% { opacity: 1; }
}

@keyframes btnShine {
  0% { left: -60%; }
  55% { left: 120%; }
  100% { left: 120%; }
}

@keyframes beaconBlink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.25; }
}

.login-container {
  position: relative;
  min-height: 100vh;
  width: 100%;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background:
    radial-gradient(circle at 12% 18%, rgba(62, 183, 255, 0.16), transparent 40%),
    radial-gradient(circle at 88% 78%, rgba(54, 211, 153, 0.14), transparent 42%),
    radial-gradient(circle at 65% 12%, rgba(124, 108, 255, 0.12), transparent 38%),
    radial-gradient(circle at 50% 100%, rgba(62, 183, 255, 0.08), transparent 55%),
    linear-gradient(160deg, #020509 0%, #050d1a 45%, #081a2f 100%);
  perspective: 1200px;
}

.login-starfield {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}

.login-star {
  position: absolute;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 0 6px rgba(255, 255, 255, 0.9);
  animation-name: starTwinkle;
  animation-timing-function: ease-in-out;
  animation-iteration-count: infinite;
}

.login-bg-glow {
  position: absolute;
  width: 480px;
  height: 480px;
  border-radius: 50%;
  filter: blur(90px);
  pointer-events: none;
  opacity: 0.55;
  animation: glowDrift 10s ease-in-out infinite;
  z-index: 0;

  &--a {
    top: -160px;
    left: -120px;
    background: rgba(62, 183, 255, 0.4);
  }

  &--b {
    bottom: -180px;
    right: -140px;
    background: rgba(54, 211, 153, 0.32);
    animation-delay: 2s;
  }

  &--c {
    top: 28%;
    right: 6%;
    width: 380px;
    height: 380px;
    background: rgba(124, 108, 255, 0.26);
    animation-delay: 4s;
  }
}

.login-beam {
  position: absolute;
  top: -30%;
  width: 2px;
  height: 160%;
  background: linear-gradient(180deg, transparent, rgba(62, 183, 255, 0.65), transparent);
  filter: blur(1.5px);
  transform: rotate(14deg);
  pointer-events: none;
  animation: beamPass linear infinite;
  z-index: 0;

  &--1 {
    animation-duration: 8s;
  }

  &--2 {
    animation-duration: 11s;
    animation-delay: 2.5s;
    background: linear-gradient(180deg, transparent, rgba(54, 211, 153, 0.55), transparent);
    transform: rotate(-10deg);
  }

  &--3 {
    animation-duration: 14s;
    animation-delay: 5s;
  }
}

.login-hologram {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 0;
  height: 0;
  pointer-events: none;
  z-index: 0;
}

.login-holo-ring {
  position: absolute;
  top: 0;
  left: 0;
  border-radius: 50%;
  border: 1px dashed rgba(62, 183, 255, 0.2);

  &--1 {
    width: 560px;
    height: 560px;
    margin: -280px 0 0 -280px;
    animation: holoSpin 34s linear infinite;
  }

  &--2 {
    width: 760px;
    height: 760px;
    margin: -380px 0 0 -380px;
    border-color: rgba(54, 211, 153, 0.16);
    border-style: solid;
    animation: holoSpinReverse 50s linear infinite;
  }

  &--3 {
    width: 940px;
    height: 940px;
    margin: -470px 0 0 -470px;
    border-color: rgba(124, 108, 255, 0.14);
    animation: holoSpin 70s linear infinite;
  }
}

.login-holo-sweep {
  position: absolute;
  width: 560px;
  height: 560px;
  margin: -280px 0 0 -280px;
  border-radius: 50%;
  background: conic-gradient(from 0deg, rgba(62, 183, 255, 0.3), rgba(62, 183, 255, 0.08) 12%, transparent 26%, transparent 100%);
  animation: holoSweep 5s linear infinite;
  opacity: 0.45;
  mask-image: radial-gradient(circle, transparent 0%, #000 8%, #000 92%, transparent 100%);
}

.login-holo-core {
  position: absolute;
  top: 0;
  left: 0;
  width: 10px;
  height: 10px;
  margin: -5px 0 0 -5px;
  border-radius: 50%;
  background: var(--accent-primary);
  box-shadow: 0 0 24px 6px rgba(62, 183, 255, 0.6);
  animation: holoCorePulse 2.4s ease-in-out infinite;
}

.login-particle {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
  animation-name: particleFloat;
  animation-timing-function: ease-in-out;
  animation-iteration-count: infinite;

  &.is-cyan {
    background: #7fd6ff;
    box-shadow: 0 0 6px rgba(62, 183, 255, 0.9);
  }

  &.is-green {
    background: #8ff0c9;
    box-shadow: 0 0 6px rgba(54, 211, 153, 0.9);
  }
}

.login-data-stream {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 130px;
  overflow: hidden;
  pointer-events: none;
  opacity: 0.32;
  font-family: var(--font-mono);
  font-size: 11px;
  line-height: 2;
  letter-spacing: 0.5px;
  color: var(--accent-primary);
  mask-image: linear-gradient(180deg, transparent, #000 15%, #000 85%, transparent);
  z-index: 0;

  &--left {
    left: 28px;
  }

  &--right {
    right: 28px;
    text-align: right;
    color: var(--accent-success);
  }

  @media (max-width: 980px) {
    display: none;
  }
}

.login-data-stream-track {
  display: flex;
  flex-direction: column;
  animation: dataScroll 22s linear infinite;

  p {
    margin: 0;
    white-space: nowrap;
  }
}

.login-data-stream--right .login-data-stream-track {
  animation-duration: 26s;
  animation-direction: reverse;
}

.login-hud-corner {
  position: fixed;
  width: 34px;
  height: 34px;
  border: 2px solid rgba(62, 183, 255, 0.4);
  pointer-events: none;
  z-index: 2;
  animation: cornerPulse 3s ease-in-out infinite;

  &--tl { top: 22px; left: 22px; border-right: 0; border-bottom: 0; }
  &--tr { top: 22px; right: 22px; border-left: 0; border-bottom: 0; }
  &--bl { bottom: 22px; left: 22px; border-right: 0; border-top: 0; }
  &--br { bottom: 22px; right: 22px; border-left: 0; border-top: 0; }
}

.login-ecg {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 32px;
  height: 60px;
  overflow: hidden;
  opacity: 0.4;
  mask-image: linear-gradient(90deg, transparent, #000 15%, #000 85%, transparent);
  pointer-events: none;
}

.login-ecg-track {
  display: flex;
  width: 200%;
  height: 100%;
  animation: ecgScroll 5s linear infinite;
}

.login-ecg-svg {
  width: 50%;
  height: 100%;
  flex-shrink: 0;

  polyline {
    fill: none;
    stroke: var(--accent-primary);
    stroke-width: 2;
    filter: drop-shadow(0 0 4px rgba(62, 183, 255, 0.7));
  }
}

.login-panel {
  position: relative;
  z-index: 1;
  width: 520px;
  max-width: calc(100vw - 40px);
  padding: 56px 52px 44px;
  border-radius: var(--radius-xl);
  border: 1px solid var(--border-soft);
  background: linear-gradient(145deg, rgba(13, 28, 51, 0.86), rgba(7, 16, 29, 0.82));
  box-shadow: var(--shadow-elevated), var(--shadow-inner);
  backdrop-filter: blur(18px);
  animation: panelGlow 4s ease-in-out infinite alternate;
  transform-style: preserve-3d;
  transition: transform 0.15s ease-out;
  will-change: transform;
}

.login-panel-spotlight {
  position: absolute;
  inset: 0;
  border-radius: inherit;
  pointer-events: none;
  transition: opacity 0.25s ease-out;
  z-index: 0;
}

.login-panel-corner {
  position: absolute;
  width: 22px;
  height: 22px;
  border: 2px solid var(--accent-primary);
  opacity: 0.7;
  pointer-events: none;
  animation: cornerPulse 2.4s ease-in-out infinite;

  &--tl { top: -1px; left: -1px; border-right: 0; border-bottom: 0; border-radius: 10px 0 0 0; }
  &--tr { top: -1px; right: -1px; border-left: 0; border-bottom: 0; border-radius: 0 10px 0 0; }
  &--bl { bottom: -1px; left: -1px; border-right: 0; border-top: 0; border-radius: 0 0 0 10px; }
  &--br { bottom: -1px; right: -1px; border-left: 0; border-top: 0; border-radius: 0 0 10px 0; }
}

.login-brand {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 32px;
}

.login-brand-mark {
  position: relative;
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: rgba(62, 183, 255, 0.1);
  border: 1px solid rgba(95, 189, 255, 0.28);
  color: var(--accent-primary);

  &::before,
  &::after {
    content: '';
    position: absolute;
    inset: -6px;
    border-radius: 14px;
    border: 1px solid rgba(62, 183, 255, 0.4);
    animation: iconPulse 2.4s ease-out infinite;
  }

  &::after {
    animation-delay: 1.2s;
  }
}

.login-brand-pulse-path {
  animation: iconBreathe 1.8s ease-in-out infinite;
}

.login-brand-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.5px;
  background: linear-gradient(90deg, var(--accent-success) 15%, var(--accent-primary) 50%, var(--accent-success) 85%);
  background-size: 200% auto;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  animation: titleShift 4s ease-in-out infinite alternate;
}

.login-brand-sub {
  margin: 4px 0 0;
  font-size: 12px;
  letter-spacing: 0.3px;
  color: var(--text-muted);
}

.login-field {
  display: flex;
  align-items: center;
  height: 46px;
  padding: 0 14px;
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-md);
  background: var(--bg-surface-soft);
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast);

  &:focus-within {
    border-color: var(--border-strong);
    box-shadow: 0 0 0 3px rgba(62, 183, 255, 0.16);
  }
}

.login-field-icon {
  display: flex;
  align-items: center;
  color: var(--text-secondary);
  font-size: 16px;
  margin-right: 4px;
}

.login-field-toggle {
  display: flex;
  align-items: center;
  color: var(--text-secondary);
  font-size: 16px;
  cursor: pointer;
  user-select: none;

  &:hover {
    color: var(--accent-primary);
  }
}

.login-submit {
  position: relative;
  overflow: hidden;
  width: 100%;
  height: 46px;
  margin: 8px 0 20px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 4px;
  border-radius: var(--radius-md);

  &::after {
    content: '';
    position: absolute;
    top: 0;
    left: -60%;
    width: 40%;
    height: 100%;
    background: linear-gradient(120deg, transparent, rgba(255, 255, 255, 0.35), transparent);
    transform: skewX(-20deg);
    animation: btnShine 3s ease-in-out infinite;
  }
}

.login-tips {
  display: flex;
  justify-content: center;
  gap: 16px;
  font-size: 13px;
  color: var(--text-muted);
}

.login-status {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-top: 16px;
  font-size: 12px;
  letter-spacing: 0.5px;
  color: var(--accent-success);
}

.login-status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent-success);
  box-shadow: 0 0 8px rgba(54, 211, 153, 0.8);
  animation: beaconBlink 2s ease-in-out infinite;
}
</style>

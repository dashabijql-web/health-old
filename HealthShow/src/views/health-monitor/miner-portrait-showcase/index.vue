<template>
  <div class="miner-showcase-page">
    <header class="showcase-header">
      <div>
        <div class="showcase-kicker">EMPLOYEE HEALTH PORTRAIT / VISUAL DEMO</div>
        <h1>矿工健康画像</h1>
        <p>独立截图展示页 · 人体热点、实时体征与动态监测框</p>
      </div>
      <button type="button" class="back-button" @click="goBack">返回上一页</button>
    </header>

    <main class="showcase-workspace">
      <section class="miner-frame" aria-label="矿工健康画像可视化">
        <div class="frame-grid"></div>
        <div class="frame-scan"></div>
        <div class="frame-orbit frame-orbit--one"></div>
        <div class="frame-orbit frame-orbit--two"></div>
        <div class="frame-corner frame-corner--tl"></div>
        <div class="frame-corner frame-corner--tr"></div>
        <div class="frame-corner frame-corner--bl"></div>
        <div class="frame-corner frame-corner--br"></div>

        <div class="frame-caption frame-caption--top">
          <span class="signal-dot"></span>
          <span>LIVE BODY MONITOR</span>
          <strong>矿工 · 001728</strong>
        </div>

        <img class="miner-figure" src="/assets/miner-worker.png" alt="矿工作业人员全身示意图" />

        <div
          v-for="hotspot in hotspots"
          :key="hotspot.key"
          :class="['body-hotspot', `body-hotspot--${hotspot.key}`]"
        >
          <span class="hotspot-pulse"></span>
          <div class="hotspot-line"></div>
          <div class="hotspot-card">
            <span>{{ hotspot.label }}</span>
            <strong>{{ hotspot.value }}</strong>
            <em>{{ hotspot.unit }}</em>
          </div>
        </div>

        <div class="frame-caption frame-caption--bottom">
          <span>采集时间 2026-08-19 21:56:08</span>
          <span class="caption-status">状态稳定 / SIGNAL OK</span>
        </div>
      </section>

      <aside class="telemetry-panel">
        <div class="telemetry-heading">
          <div>
            <span class="showcase-kicker">REALTIME TELEMETRY</span>
            <h2>实时体征</h2>
          </div>
          <span class="online-pill">在线</span>
        </div>

        <div class="telemetry-list">
          <article v-for="metric in metrics" :key="metric.label" class="metric-card">
            <div class="metric-card__head">
              <span :class="['metric-icon', `metric-icon--${metric.tone}`]"></span>
              <span>{{ metric.label }}</span>
              <small>{{ metric.status }}</small>
            </div>
            <div class="metric-card__value">
              <strong>{{ metric.value }}</strong>
              <span>{{ metric.unit }}</span>
            </div>
            <div class="metric-track"><span :class="`metric-track__fill metric-track__fill--${metric.tone}`" :style="{ width: `${metric.percent}%` }"></span></div>
          </article>
        </div>

        <div class="safety-card">
          <div class="safety-card__title"><span class="signal-dot"></span>工作状态</div>
          <strong>正常作业</strong>
          <p>未发现需要立即处置的异常体征</p>
        </div>

        <div class="timeline-card">
          <div class="timeline-card__title">监测记录</div>
          <div v-for="item in timeline" :key="item.time" class="timeline-item">
            <span class="timeline-item__dot"></span>
            <span>{{ item.label }}</span>
            <time>{{ item.time }}</time>
          </div>
        </div>
      </aside>
    </main>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

const hotspots = [
  { key: 'heart', label: '心率', value: '78', unit: 'BPM' },
  { key: 'oxygen', label: '血氧', value: '98', unit: '%' },
  { key: 'temperature', label: '体温', value: '36.5', unit: '°C' },
  { key: 'pressure', label: '血压', value: '122/78', unit: 'mmHg' }
]

const metrics = [
  { label: '心率', value: '78', unit: 'BPM', status: '正常', percent: 52, tone: 'cyan' },
  { label: '血氧', value: '98', unit: '%', status: '良好', percent: 96, tone: 'green' },
  { label: '体温', value: '36.5', unit: '°C', status: '正常', percent: 63, tone: 'amber' },
  { label: '血压', value: '122/78', unit: 'mmHg', status: '正常', percent: 58, tone: 'blue' }
]

const timeline = [
  { label: '心率采集完成', time: '21:56' },
  { label: '血氧采集完成', time: '21:54' },
  { label: '设备连接稳定', time: '21:50' }
]

function goBack() {
  void router.back()
}
</script>

<style scoped>
.miner-showcase-page {
  min-height: calc(100vh - 50px);
  box-sizing: border-box;
  padding: 24px 28px 34px;
  color: #dceeff;
  background: #06111e;
}

.showcase-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  max-width: 1540px;
  margin: 0 auto 18px;
}

.showcase-kicker {
  color: #42c9f5;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.16em;
}

h1,
h2,
p {
  margin: 0;
}

h1 {
  margin-top: 8px;
  color: #f1f8ff;
  font-size: clamp(24px, 2.4vw, 38px);
  letter-spacing: 0.04em;
}

.showcase-header p {
  margin-top: 8px;
  color: #7698b7;
  font-size: 13px;
}

.back-button {
  min-height: 38px;
  padding: 0 16px;
  border: 1px solid rgba(66, 201, 245, 0.42);
  border-radius: 6px;
  color: #bdeaff;
  background: rgba(11, 37, 61, 0.78);
  cursor: pointer;
}

.showcase-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 310px;
  gap: 18px;
  max-width: 1540px;
  min-height: 590px;
  margin: 0 auto;
}

.miner-frame {
  position: relative;
  min-height: 590px;
  overflow: hidden;
  border: 1px solid rgba(48, 133, 179, 0.68);
  border-radius: 8px;
  background: radial-gradient(circle at 50% 42%, rgba(19, 68, 94, 0.34), transparent 40%), #071725;
  box-shadow: inset 0 0 80px rgba(0, 161, 227, 0.08), 0 16px 50px rgba(0, 0, 0, 0.28);
}

.frame-grid,
.frame-scan,
.frame-orbit,
.miner-figure,
.frame-caption,
.frame-corner,
.body-hotspot {
  position: absolute;
}

.frame-grid {
  inset: 0;
  opacity: 0.23;
  background-image: linear-gradient(rgba(62, 167, 203, 0.11) 1px, transparent 1px), linear-gradient(90deg, rgba(62, 167, 203, 0.11) 1px, transparent 1px);
  background-size: 42px 42px;
}

.frame-scan {
  top: 0;
  bottom: 0;
  left: 18%;
  width: 1px;
  background: linear-gradient(transparent, rgba(79, 224, 255, 0.8), transparent);
  box-shadow: 0 0 16px rgba(52, 209, 255, 0.75);
  animation: scan 4.8s ease-in-out infinite;
}

.frame-orbit {
  left: 50%;
  top: 53%;
  border: 1px solid rgba(37, 195, 239, 0.27);
  border-radius: 50%;
  transform: translate(-50%, -50%);
  pointer-events: none;
}

.frame-orbit--one {
  width: min(68%, 620px);
  aspect-ratio: 1;
  animation: rotate 18s linear infinite;
}

.frame-orbit--two {
  width: min(54%, 500px);
  aspect-ratio: 1;
  border-style: dashed;
  opacity: 0.55;
  animation: rotate-reverse 24s linear infinite;
}

.frame-corner {
  width: 52px;
  height: 52px;
  border-color: #4bd9ff;
}

.frame-corner--tl { top: 24px; left: 24px; border-top: 2px solid; border-left: 2px solid; }
.frame-corner--tr { top: 24px; right: 24px; border-top: 2px solid; border-right: 2px solid; }
.frame-corner--bl { bottom: 24px; left: 24px; border-bottom: 2px solid; border-left: 2px solid; }
.frame-corner--br { right: 24px; bottom: 24px; border-right: 2px solid; border-bottom: 2px solid; }

.frame-caption {
  z-index: 4;
  display: flex;
  align-items: center;
  gap: 9px;
  color: #77a7c4;
  font-size: 10px;
  letter-spacing: 0.12em;
}

.frame-caption--top { top: 34px; left: 42px; }
.frame-caption--top strong { margin-left: 18px; color: #d6f1ff; font-weight: 600; letter-spacing: 0.06em; }
.frame-caption--bottom { right: 42px; bottom: 34px; left: 42px; justify-content: space-between; letter-spacing: 0.05em; }
.caption-status { color: #4de3a3; }

.signal-dot,
.timeline-item__dot {
  width: 7px;
  height: 7px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: #44e2a2;
  box-shadow: 0 0 0 5px rgba(68, 226, 162, 0.08), 0 0 14px rgba(68, 226, 162, 0.8);
}

.miner-figure {
  z-index: 2;
  bottom: 58px;
  left: 50%;
  width: min(44%, 520px);
  height: 82%;
  object-fit: contain;
  object-position: center bottom;
  filter: drop-shadow(0 18px 24px rgba(0, 0, 0, 0.42));
  transform: translateX(-50%);
  animation: miner-float 4.2s ease-in-out infinite;
}

.body-hotspot {
  z-index: 5;
  display: flex;
  align-items: center;
  gap: 8px;
}

.body-hotspot--heart { top: 33%; left: 14%; }
.body-hotspot--oxygen { top: 44%; right: 14%; flex-direction: row-reverse; }
.body-hotspot--temperature { top: 20%; right: 15%; flex-direction: row-reverse; }
.body-hotspot--pressure { top: 54%; left: 13%; }

.hotspot-pulse {
  width: 11px;
  height: 11px;
  border: 1px solid #45d9ff;
  border-radius: 50%;
  box-shadow: 0 0 0 0 rgba(69, 217, 255, 0.6);
  animation: pulse 1.8s ease-out infinite;
}

.hotspot-line {
  width: clamp(30px, 5vw, 78px);
  height: 1px;
  background: linear-gradient(90deg, rgba(70, 217, 255, 0.8), transparent);
}

.body-hotspot--oxygen .hotspot-line,
.body-hotspot--temperature .hotspot-line { transform: rotate(180deg); }

.hotspot-card {
  min-width: 92px;
  padding: 7px 9px;
  border: 1px solid rgba(64, 207, 246, 0.35);
  border-radius: 4px;
  background: rgba(4, 19, 32, 0.86);
  box-shadow: 0 0 20px rgba(29, 178, 219, 0.12);
}

.hotspot-card span,
.hotspot-card em { display: block; color: #77a7c4; font-size: 10px; font-style: normal; }
.hotspot-card strong { display: inline-block; margin: 2px 5px 0 0; color: #dff8ff; font: 700 18px/1.1 ui-monospace, SFMono-Regular, Menlo, monospace; }

.telemetry-panel {
  align-self: stretch;
  padding: 20px;
  border: 1px solid rgba(45, 101, 134, 0.68);
  border-radius: 8px;
  background: #091a2b;
}

.telemetry-heading,
.metric-card__head,
.safety-card__title {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.telemetry-heading h2 { margin-top: 6px; color: #e4f5ff; font-size: 23px; }
.online-pill { padding: 4px 9px; border: 1px solid rgba(68, 226, 162, 0.35); border-radius: 4px; color: #54e6ab; font-size: 11px; }
.telemetry-list { display: grid; gap: 10px; margin-top: 24px; }
.metric-card { padding: 13px 14px; border: 1px solid rgba(52, 111, 143, 0.5); border-radius: 6px; background: rgba(7, 26, 42, 0.84); }
.metric-card__head { color: #a9cbe0; font-size: 12px; }
.metric-card__head small { color: #56dca6; font-size: 10px; }
.metric-icon { width: 8px; height: 8px; margin-right: 7px; border-radius: 50%; background: #4bd9ff; box-shadow: 0 0 9px currentColor; }
.metric-icon--green { background: #4de3a3; }
.metric-icon--amber { background: #ffc557; }
.metric-icon--blue { background: #8faeff; }
.metric-card__value { margin-top: 10px; }
.metric-card__value strong { color: #effaff; font: 700 25px/1 ui-monospace, SFMono-Regular, Menlo, monospace; }
.metric-card__value span { margin-left: 6px; color: #6f99b6; font-size: 10px; }
.metric-track { height: 3px; margin-top: 11px; overflow: hidden; border-radius: 3px; background: #142f43; }
.metric-track__fill { display: block; height: 100%; border-radius: inherit; background: #4bd9ff; box-shadow: 0 0 10px currentColor; }
.metric-track__fill--green { background: #4de3a3; }
.metric-track__fill--amber { background: #ffc557; }
.metric-track__fill--blue { background: #8faeff; }

.safety-card,
.timeline-card { margin-top: 16px; padding: 14px; border: 1px solid rgba(52, 111, 143, 0.5); border-radius: 6px; background: rgba(7, 26, 42, 0.72); }
.safety-card__title,
.timeline-card__title { color: #7da5be; font-size: 11px; }
.safety-card > strong { display: block; margin-top: 13px; color: #4de3a3; font-size: 18px; }
.safety-card p { margin-top: 5px; color: #6f99b6; font-size: 11px; line-height: 1.6; }
.timeline-card__title { margin-bottom: 12px; }
.timeline-item { display: grid; grid-template-columns: 10px 1fr auto; align-items: center; gap: 8px; padding: 9px 0; border-top: 1px solid rgba(52, 111, 143, 0.22); color: #a9cbe0; font-size: 11px; }
.timeline-item time { color: #63869d; font: 10px ui-monospace, SFMono-Regular, Menlo, monospace; }

@keyframes scan { 0%, 100% { transform: translateX(0); opacity: 0; } 12% { opacity: 0.9; } 88% { opacity: 0.9; } 100% { transform: translateX(64vw); opacity: 0; } }
@keyframes rotate { to { transform: translate(-50%, -50%) rotate(360deg); } }
@keyframes rotate-reverse { to { transform: translate(-50%, -50%) rotate(-360deg); } }
@keyframes miner-float { 0%, 100% { transform: translate(-50%, 0); } 50% { transform: translate(-50%, -7px); } }
@keyframes pulse { 0% { box-shadow: 0 0 0 0 rgba(69, 217, 255, 0.65); } 70% { box-shadow: 0 0 0 12px rgba(69, 217, 255, 0); } 100% { box-shadow: 0 0 0 0 rgba(69, 217, 255, 0); } }

@media (max-width: 980px) {
  .miner-showcase-page { padding: 18px; }
  .showcase-workspace { grid-template-columns: 1fr; }
  .miner-frame { min-height: 620px; }
  .telemetry-panel { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
  .telemetry-heading { grid-column: 1 / -1; }
  .telemetry-list { grid-template-columns: repeat(2, minmax(0, 1fr)); margin-top: 0; }
  .safety-card,
  .timeline-card { margin-top: 0; }
}

@media (max-width: 620px) {
  .showcase-header { align-items: flex-start; flex-direction: column; }
  .back-button { align-self: flex-start; }
  .miner-frame { min-height: 560px; }
  .miner-figure { width: 72%; height: 72%; }
  .body-hotspot--heart { left: 5%; }
  .body-hotspot--oxygen,
  .body-hotspot--temperature { right: 5%; }
  .body-hotspot--pressure { left: 4%; }
  .hotspot-line { width: 20px; }
  .hotspot-card { min-width: 72px; padding: 5px 6px; }
  .hotspot-card strong { font-size: 15px; }
  .telemetry-panel { display: block; }
  .telemetry-list { grid-template-columns: 1fr; margin-top: 20px; }
}
</style>

<template>
  <div class="kpi">
    <div class="kcard kc-cyan" @click="$emit('detail','underground')">
      <div class="kc-glow"></div>
      <div class="kc-head"><span class="kc-lbl">井下人数</span><span class="kc-ico">UG</span></div>
      <div class="kc-num" style="color:#00d4ff;text-shadow:0 0 20px rgba(0,212,255,.5)">
        <AnimatedNumber :value="stats.underground" fontSize="34px" color="#00d4ff" :duration="1500" :separator="false"/>
      </div>
      <div class="kc-foot"><span class="ft ft-g">正常 {{ stats.normal }}</span><span class="dim">/ {{ stats.total }}</span></div>
    </div>

    <div class="kcard kc-red" @click="$emit('detail','sos')">
      <div class="kc-glow"></div>
      <div class="kc-head"><span class="kc-lbl">SOS 求救</span><span class="kc-ico">SOS</span></div>
      <div class="kc-num" style="color:#ff4757;text-shadow:0 0 20px rgba(255,71,87,.5)">
        <AnimatedNumber :value="stats.sos" fontSize="34px" color="#ff4757" :duration="1500" :separator="false"/>
      </div>
      <div v-if="stats.sos > 0" class="kc-foot"><span class="ft ft-r">已持续 {{ maxSosDuration }}分钟</span></div>
      <div v-else class="kc-foot dim">无紧急求救</div>
    </div>

    <div class="kcard kc-orange" @click="$emit('detail','fall')">
      <div class="kc-glow"></div>
      <div class="kc-head"><span class="kc-lbl">跌倒检测</span><span class="kc-ico">FALL</span></div>
      <div class="kc-num" style="color:#ff6b35;text-shadow:0 0 20px rgba(255,107,53,.4)">
        <AnimatedNumber :value="stats.fall" fontSize="34px" color="#ff6b35" :duration="1500" :separator="false"/>
      </div>
      <div v-if="stats.fall > 0" class="kc-foot"><span class="ft ft-o">已持续 {{ maxFallDuration }}分钟</span></div>
      <div v-else class="kc-foot dim">无跌倒事件</div>
    </div>

    <div class="kcard kc-yellow" @click="$emit('detail','alerts')">
      <div class="kc-glow"></div>
      <div class="kc-head"><span class="kc-lbl">其他预警</span><span class="kc-ico">WARN</span></div>
      <div class="kc-num" style="color:#ffd32a;text-shadow:0 0 20px rgba(255,211,42,.4)">
        <AnimatedNumber :value="stats.static + stats.abnormal" fontSize="34px" color="#ffd32a" :duration="1500" :separator="false"/>
      </div>
      <div class="kc-foot dim">静止{{ stats.static }} · 异常{{ stats.abnormal }}</div>
    </div>

    <div :class="['kcard', watchStatus.offline > 20 || watchStatus.lowBattery > 30 ? 'kc-orange' : 'kc-teal']" @click="$emit('detail','watch')">
      <div class="kc-glow"></div>
      <div class="kc-head"><span class="kc-lbl">手表状态</span><span class="kc-ico">WATCH</span></div>
      <div class="kc-num" :style="{color: watchStatus.offline>20||watchStatus.lowBattery>30?'#ff6b35':'#26c6da', textShadow:'0 0 20px rgba(38,198,218,.4)'}">
        <AnimatedNumber :value="watchStatus.online" fontSize="34px" :color="watchStatus.offline>20?'#ff6b35':'#26c6da'" :duration="1500" :separator="false"/>
        <span class="kc-total">/ {{ watchStatus.total }}</span>
      </div>
      <div class="kc-foot">
        <span v-if="watchStatus.lowBattery > 0" class="ft ft-o">低电{{ watchStatus.lowBattery }}</span>
        <span v-if="watchStatus.offline > 0" class="ft ft-r" style="margin-left:3px">离线{{ watchStatus.offline }}</span>
        <span v-if="!watchStatus.lowBattery && !watchStatus.offline" class="ft ft-g">全部正常</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { PropType } from 'vue'
import AnimatedNumber from '@/components/AnimatedNumber.vue'

interface SafetyStats {
  underground: number
  total: number
  sos: number
  fall: number
  static: number
  abnormal: number
  normal: number
}

interface WatchStatus {
  total: number
  online: number
  offline: number
  lowBattery: number
  onlineRate: number
}

type KpiDetail = 'underground' | 'sos' | 'fall' | 'alerts' | 'watch'

defineProps({
  stats: { type: Object as PropType<SafetyStats>, default: () => ({underground:0,total:0,sos:0,fall:0,static:0,abnormal:0,normal:0}) },
  watchStatus: { type: Object as PropType<WatchStatus>, default: () => ({total:0,online:0,offline:0,lowBattery:0,onlineRate:0}) },
  maxSosDuration: { type: Number, default: 0 },
  maxFallDuration: { type: Number, default: 0 }
})
defineEmits<{
  detail: [kind: KpiDetail]
}>()
</script>

<style scoped lang="scss">
@use 'sass:color';

$cyan:#00d4ff; $red:#ff4757; $orange:#ff6b35; $yellow:#ffd32a; $green:#2ed573; $teal:#26c6da;
$panel:rgba(10,22,42,.82); $border2:rgba(0,212,255,.07);
$mono:'JetBrains Mono','Courier New',monospace;

.kpi {
  grid-column: 1 / 5;
  grid-row: 1;
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 7px;
  flex-shrink: 0;
  min-width: 0;
}

.kcard {
  background:$panel; border:1px solid $border2; border-radius:5px;
  padding:11px 14px 10px; display:flex; flex-direction:column; gap:4px;
  height:108px; position:relative; overflow:hidden; cursor:pointer;
  backdrop-filter:blur(6px);
  // left accent strip
  &::before { content:''; position:absolute; left:0; top:0; bottom:0; width:2px; border-radius:2px 0 0 2px; }
  // top highlight
  &::after { content:''; position:absolute; top:0; left:14px; right:14px; height:1px; background:linear-gradient(90deg,transparent,rgba(255,255,255,.08),transparent); }
  transition:border-color .22s, box-shadow .22s;
}
.kc-cyan { border-color:rgba($cyan,.2); &::before{background:linear-gradient(180deg,$cyan,transparent)} &:hover{border-color:rgba($cyan,.45);box-shadow:0 0 18px rgba($cyan,.12)} .kc-glow{background:radial-gradient(circle,rgba($cyan,.14),transparent 70%)} }
.kc-red  { border-color:rgba($red,.2);  &::before{background:linear-gradient(180deg,$red,transparent)}  &:hover{border-color:rgba($red,.45);box-shadow:0 0 18px rgba($red,.12)}  .kc-glow{background:radial-gradient(circle,rgba($red,.14),transparent 70%)} }
.kc-orange{ border-color:rgba($orange,.2);&::before{background:linear-gradient(180deg,$orange,transparent)}&:hover{border-color:rgba($orange,.45);box-shadow:0 0 18px rgba($orange,.12)}.kc-glow{background:radial-gradient(circle,rgba($orange,.12),transparent 70%)}}
.kc-yellow{ border-color:rgba($yellow,.2);&::before{background:linear-gradient(180deg,$yellow,transparent)}&:hover{border-color:rgba($yellow,.45);box-shadow:0 0 18px rgba($yellow,.1)}.kc-glow{background:radial-gradient(circle,rgba($yellow,.1),transparent 70%)}}
.kc-teal { border-color:rgba($teal,.2);  &::before{background:linear-gradient(180deg,$teal,transparent)}  &:hover{border-color:rgba($teal,.45);box-shadow:0 0 18px rgba($teal,.12)}  .kc-glow{background:radial-gradient(circle,rgba($teal,.14),transparent 70%)} }

.kc-glow { position:absolute; right:-18px; bottom:-18px; width:80px; height:80px; border-radius:50%; pointer-events:none; filter:blur(14px); }

.kc-head { display:flex; align-items:center; justify-content:space-between; gap:8px; min-width:0; }
.kc-lbl  {
  flex: 1;
  min-width: 0;
  font-size: 11px;
  color: rgba(255,255,255,.58);
  letter-spacing: .4px;
  white-space: nowrap;
}
.kc-ico  {
  flex-shrink: 0;
  min-width: 38px;
  padding: 2px 6px;
  border-radius: 999px;
  background: rgba(255,255,255,.06);
  color: rgba(255,255,255,.72);
  font-size: 9px;
  font-weight: 700;
  letter-spacing: .04em;
  text-align: center;
}
.kc-num  { display:flex; align-items:baseline; gap:3px; line-height:1; font-family:$mono; }
.kc-total{ font-size:13px; color:rgba(255,255,255,.35); font-family:$mono; }
.kc-foot { display:flex; align-items:center; gap:4px; flex-wrap:wrap; font-size:10px; position:relative; z-index:1; }
.kc-foot .dim { display:inline-flex; align-items:center; min-width:0; }
.dim     { color:rgba(255,255,255,.38); }

@media (max-width: 768px) {
  .kpi {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .kcard:last-child {
    grid-column: 1 / -1;
  }
  .kcard { padding:10px 12px; }
  .kc-foot { line-height:1.25; }
  .kc-foot .dim { flex-basis:100%; margin-top:1px; }
}

.ft { font-size:9px; padding:1px 6px; border-radius:2px; }
.ft-g { background:rgba($green,.12); color:color.adjust($green, $lightness: 10%); border:1px solid rgba($green,.2); }
.ft-r { background:rgba($red,.15);   color:#ff8090;             border:1px solid rgba($red,.2);   }
.ft-o { background:rgba($orange,.12);color:#ff9a55;             border:1px solid rgba($orange,.2);}
.ft-c { background:rgba($cyan,.1);   color:color.adjust($cyan, $lightness: 10%);  border:1px solid rgba($cyan,.2);  }
</style>

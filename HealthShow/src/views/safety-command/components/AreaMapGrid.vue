<template>
  <div class="panel area-panel">
    <div class="ph">
      <span class="pt">
        <span class="pt-bar"></span>
        {{ title }}
      </span>
      <div class="ctrl">
        <span :class="['tb', viewMode==='grid'&&'on']" @click="viewMode='grid'">网格</span>
        <span :class="['tb', viewMode==='treemap'&&'on']" @click="viewMode='treemap'">图谱</span>
        <span class="tb" @click="$emit('refresh')">↻</span>
      </div>
    </div>
    <!-- Grid mode -->
    <div v-if="effectiveMode==='grid'" class="ag">
      <div
        v-for="a in areas" :key="a.id"
        :class="['ac', a.level==='danger'?'sos': a.level==='warning'?'warn':'']"
        @click="$emit('showArea', a)"
      >
        <div class="ac-top">
          <span class="ac-name">{{ a.name }}</span>
          <span :class="['ac-dot', a.level==='danger'?'dot-r':a.level==='warning'?'dot-o':'dot-n']"></span>
        </div>
        <div class="ac-cnt">人数 <b>{{ a.count }}</b></div>
        <div v-if="a.sos>0||a.fall>0||a.warning>0" class="ac-badges">
          <span v-if="a.sos>0"  class="ab ab-r">SOS×{{ a.sos }}</span>
          <span v-else-if="a.fall>0" class="ab ab-r">跌倒×{{ a.fall }}</span>
          <span v-else class="ab ab-o">预警×{{ a.warning }}</span>
        </div>
      </div>
    </div>
    <!-- Treemap mode -->
    <div v-else ref="treemapRef" class="treemap-body"></div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref, watch, type PropType } from 'vue'
import * as echarts from '@/utils/echarts-setup'

interface AreaItem {
  id: number | string
  name: string
  count: number
  sos: number
  fall: number
  warning: number
  level?: string
  [key: string]: unknown
}

const props = defineProps({
  areas: { type: Array as PropType<AreaItem[]>, default: () => [] },
  title: { type: String, default: '井下人员分布' }
})
defineEmits<{
  fullscreen: []
  refresh: []
  showArea: [area: AreaItem]
  callLeader: [area: AreaItem]
}>()

const viewMode = ref<'grid' | 'treemap'>('grid')
const treemapRef = ref<HTMLElement | null>(null)
let treemapChart: ReturnType<typeof echarts.init> | null = null

const effectiveMode = computed(() => {
  if (viewMode.value === 'treemap' && props.areas.every(a => (a.count||0) === 0)) return 'grid'
  return viewMode.value
})

const levelColor = (level?: string) => level === 'danger' ? '#ff4757' : level === 'warning' ? '#ff6b35' : '#2ed573'

const buildTreemap = () => {
  if (!treemapRef.value) return
  if (treemapChart) treemapChart.dispose()
  treemapChart = echarts.init(treemapRef.value)
  treemapChart.setOption({
    backgroundColor:'transparent',
    series:[{
      type:'treemap', width:'100%', height:'100%', roam:false, nodeClick:false,
      breadcrumb:{show:false},
      label:{show:true,formatter:(p)=>`${p.name}\n${p.value}人`,fontSize:11,color:'#fff',textShadowColor:'rgba(0,0,0,.5)',textShadowBlur:3},
      itemStyle:{borderColor:'rgba(10,22,42,.8)',borderWidth:2,gapWidth:2},
      data:props.areas.map(a=>({name:a.name,value:a.count||1,itemStyle:{color:levelColor(a.level)}}))
    }],
    tooltip:{backgroundColor:'rgba(10,22,42,.95)',borderColor:'rgba(0,212,255,.3)',textStyle:{color:'#fff',fontSize:11},formatter:(p)=>{const a=props.areas.find(x=>x.name===p.name);if(!a)return p.name;return `<b>${a.name}</b><br/>人数: ${a.count}人<br/>SOS: ${a.sos} | 跌倒: ${a.fall} | 预警: ${a.warning}`}}
  })
}

watch(effectiveMode, async (m) => { if (m==='treemap') { await nextTick(); buildTreemap() } })
watch(() => props.areas, async () => { if (viewMode.value==='treemap') { await nextTick(); buildTreemap() } }, { deep:true })
onUnmounted(() => { if (treemapChart) treemapChart.dispose() })
</script>

<style scoped lang="scss">
$cyan:#00d4ff; $red:#ff4757; $orange:#ff6b35; $green:#2ed573;
$panel:rgba(10,22,42,.82); $border2:rgba(0,212,255,.07);
$dim:rgba(255,255,255,.45);

.area-panel { flex:2; }

.panel { background:$panel; border:1px solid $border2; border-radius:5px; padding:10px 12px; display:flex; flex-direction:column; min-height:0; backdrop-filter:blur(6px); position:relative; overflow:hidden;
  &::before { content:''; position:absolute; top:0; left:14px; right:14px; height:1px; background:linear-gradient(90deg,transparent,rgba($cyan,.15),transparent); }
}
.ph { display:flex; justify-content:space-between; align-items:center; padding-bottom:8px; margin-bottom:8px; flex-shrink:0; border-bottom:1px solid rgba($cyan,.08); }
.pt { font-size:11px; font-weight:600; color:#fff; display:flex; align-items:center; gap:7px; letter-spacing:.5px; text-transform:uppercase; }
.pt-bar { width:2px; height:12px; border-radius:1px; flex-shrink:0; background:$cyan; box-shadow:0 0 6px $cyan; }
.ctrl { display:flex; gap:2px; }
.tb { font-size:9px; padding:2px 7px; border-radius:2px; border:1px solid rgba($cyan,.12); color:$dim; cursor:pointer; transition:all .15s;
  &.on, &:hover { background:rgba($cyan,.1); border-color:rgba($cyan,.35); color:$cyan; }
}

// Grid layout
.ag {
  display:grid; grid-template-columns:repeat(3,1fr); grid-auto-rows:min-content; gap:5px;
  overflow-y:auto; min-height:0; flex:1;
}

.ac {
  border-radius:3px; padding:8px 9px; min-height:52px;
  border:1px solid rgba($cyan,.08); background:rgba($cyan,.02);
  cursor:pointer; transition:all .2s; display:flex; flex-direction:column; gap:2px;
  position:relative; overflow:hidden;
  &::after { content:''; position:absolute; bottom:0; left:0; right:0; height:1px; background:linear-gradient(90deg,transparent,rgba($cyan,.15),transparent); opacity:0; transition:opacity .2s; }
  &:hover { border-color:rgba($cyan,.28); background:rgba($cyan,.06); &::after{opacity:1} }

  &.warn { border-color:rgba($orange,.22); background:rgba($orange,.04); &:hover{background:rgba($orange,.09)} }
  &.sos  { border-color:rgba($red,.4); background:rgba($red,.06); animation:sos-pulse 2s infinite; }
}
@keyframes sos-pulse { 0%,100%{box-shadow:none} 50%{box-shadow:0 0 12px rgba(255,71,87,.2),inset 0 0 8px rgba(255,71,87,.04)} }

.ac-top { display:flex; align-items:flex-start; justify-content:space-between; }
.ac-name { font-size:10px; font-weight:600; color:rgba(255,255,255,.82); letter-spacing:.3px; }
.ac-dot  { width:5px; height:5px; border-radius:50%; flex-shrink:0; margin-top:2px; }
.dot-n   { background:rgba($cyan,.3); }
.dot-o   { background:$orange; box-shadow:0 0 4px $orange; }
.dot-r   { background:$red; box-shadow:0 0 5px $red; }

.ac-cnt  { font-size:10px; color:$dim; b{color:$cyan;font-family:'JetBrains Mono','Courier New',monospace} }
.ac-badges { display:flex; gap:3px; flex-wrap:wrap; }
.ab { font-size:8px; padding:1px 4px; border-radius:1px; }
.ab-r { background:rgba($red,.22); color:#ff8090; }
.ab-o { background:rgba($orange,.2); color:#ffaa66; }

.treemap-body { flex:1; min-height:0; }

@media (max-width: 1500px) {
  .ag {
    grid-template-columns: 1fr;
  }

  .ac {
    min-height: 58px;
  }
}
</style>

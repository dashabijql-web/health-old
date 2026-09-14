<template>
  <div class="panel rp-panel">
    <div class="ph">
      <span class="pt">
        <span class="pt-bar red-bar"></span>
        重点关注人员
        <span class="pt-cnt">{{ persons.length }}</span>
      </span>
    </div>
    <div class="rp-list">
      <div v-if="persons.length === 0" class="rp-empty">暂无重点关注人员</div>
      <button
        v-for="p in persons"
        :key="p.id||p.name"
        type="button"
        class="rp"
        @click="$emit('showPerson', p)"
      >
        <div class="rp-av">{{ (p.name || '?').charAt(0) }}</div>
        <div class="rp-info">
          <div class="rp-name">{{ p.name }}</div>
          <div class="rp-tags">
            <span
              v-for="t in (p.tags || []).slice(0, 4)"
              :key="t"
              :class="['tg', getTagClass(t)]"
            >{{ t }}</span>
          </div>
        </div>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { PropType } from 'vue'

interface RiskPerson {
  id?: number | string
  name: string
  tags: string[]
  [key: string]: unknown
}

defineProps({ persons: { type: Array as PropType<RiskPerson[]>, default: () => [] } })
defineEmits<{
  showAll: []
  showPerson: [person: RiskPerson]
}>()

const getTagClass = (t: string) =>
  t.includes('SOS') ? 'tg-r' :
  t.includes('跌倒') ? 'tg-r' :
  (t.includes('队')||t.includes('室')||t.includes('科')||t.includes('部')) ? 'tg-c' :
  'tg-o'
</script>

<style scoped lang="scss">
$cyan:#00d4ff; $red:#ff4757; $orange:#ff6b35;
$panel:rgba(10,22,42,.82); $border2:rgba(0,212,255,.10);
$dim:rgba(255,255,255,.65); $dim2:rgba(255,255,255,.35);

.rp-panel { flex:2; min-height:80px; }

.panel {
  background:$panel; border:1px solid $border2; border-radius:10px; padding:12px 14px;
  display:flex; flex-direction:column; min-height:0; backdrop-filter:blur(10px); position:relative; overflow:hidden;
  &::before { content:''; position:absolute; top:0; left:14px; right:14px; height:1px; background:linear-gradient(90deg,transparent,rgba($cyan,.25),transparent); }
}
.ph { display:flex; justify-content:space-between; align-items:center; padding-bottom:10px; margin-bottom:10px; flex-shrink:0; border-bottom:1px solid rgba($cyan,.12); }
.pt { font-size:13px; font-weight:700; color:#fff; display:flex; align-items:center; gap:8px; letter-spacing:.5px; }
.pt-bar { width:3px; height:14px; border-radius:2px; flex-shrink:0; }
.red-bar { background:$red; box-shadow:0 0 8px $red; }
.pt-cnt { font-family:'JetBrains Mono','Courier New',monospace; color:$red; font-size:14px; font-weight:700; }

.rp-list { flex:1; overflow-y:auto; min-height:0; display:flex; flex-direction:column; gap:6px; }
.rp-empty { text-align:center; color:rgba(46,213,115,.8); font-size:13px; padding:14px 0; }

.rp {
  display:flex; align-items:center; gap:10px; padding:8px 10px; border-radius:6px;
  border:1px solid rgba($red,.18); background:rgba($red,.04);
  cursor:pointer; transition:all .15s; flex-shrink:0; text-align:left;
  width:100%; color:inherit; font:inherit;
  &:hover { background:rgba($red,.1); border-color:rgba($red,.38); }
}
.rp-av {
  width:34px; height:34px; border-radius:50%; flex-shrink:0;
  display:flex; align-items:center; justify-content:center;
  font-size:14px; font-weight:700; color:$cyan;
  background:linear-gradient(135deg, rgba($cyan,.15), rgba(24,144,255,.15));
  border:1px solid rgba($cyan,.35);
}
.rp-info { flex:1; min-width:0; }
.rp-name { font-size:13px; font-weight:700; color:#fff; margin-bottom:4px; }
.rp-tags { display:flex; gap:4px; flex-wrap:wrap; }
.tg { font-size:12px; padding:2px 7px; border-radius:3px; letter-spacing:.3px; font-weight:500; }
.tg-r { background:rgba($red,.22); color:#ff8090; border:1px solid rgba($red,.32); }
.tg-o { background:rgba($orange,.18); color:#ff9a55; border:1px solid rgba($orange,.28); }
.tg-c { background:rgba($cyan,.14); color:#80efff; border:1px solid rgba($cyan,.24); }
</style>

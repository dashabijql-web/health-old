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
      <div v-for="p in persons" :key="p.id||p.name" class="rp" @click="$emit('showPerson', p)">
        <div class="rp-av">{{ p.name.charAt(0) }}</div>
        <div class="rp-info">
          <div class="rp-name">{{ p.name }}</div>
          <div class="rp-tags">
            <span
              v-for="t in p.tags.slice(0, 4)" :key="t"
              :class="['tg', getTagClass(t)]"
            >{{ t }}</span>
          </div>
        </div>
      </div>
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
$panel:rgba(10,22,42,.82); $border2:rgba(0,212,255,.07);
$dim:rgba(255,255,255,.45); $dim2:rgba(255,255,255,.22);

.rp-panel { flex:2; min-height:80px; }

.panel { background:$panel; border:1px solid $border2; border-radius:5px; padding:10px 12px; display:flex; flex-direction:column; min-height:0; backdrop-filter:blur(6px); position:relative; overflow:hidden;
  &::before { content:''; position:absolute; top:0; left:14px; right:14px; height:1px; background:linear-gradient(90deg,transparent,rgba($cyan,.15),transparent); }
}
.ph { display:flex; justify-content:space-between; align-items:center; padding-bottom:8px; margin-bottom:8px; flex-shrink:0; border-bottom:1px solid rgba($cyan,.08); }
.pt { font-size:11px; font-weight:600; color:#fff; display:flex; align-items:center; gap:7px; letter-spacing:.5px; text-transform:uppercase; }
.pt-bar { width:2px; height:12px; border-radius:1px; flex-shrink:0; }
.red-bar { background:$red; box-shadow:0 0 6px $red; }
.pt-cnt { font-family:'JetBrains Mono','Courier New',monospace; color:$red; font-size:12px; }

.call-btn {
  font-size:9px; padding:3px 10px; border-radius:2px; cursor:pointer; letter-spacing:.3px;
  background:rgba($red,.1); border:1px solid rgba($red,.4); color:#ff8090;
  font-family:inherit; transition:all .15s;
  &:hover { background:rgba($red,.22); box-shadow:0 0 10px rgba($red,.25); }
}

.rp-list { flex:1; overflow-y:auto; min-height:0; display:flex; flex-direction:column; gap:4px; }
.rp-empty { text-align:center; color:rgba(46,213,115,.7); font-size:11px; padding:10px 0; }

.rp {
  display:flex; align-items:center; gap:9px; padding:7px 9px; border-radius:3px;
  border:1px solid rgba($red,.12); background:rgba($red,.03);
  cursor:pointer; transition:all .15s; flex-shrink:0;
  &:hover { background:rgba($red,.08); border-color:rgba($red,.28); }
}
.rp-av {
  width:30px; height:30px; border-radius:50%; flex-shrink:0;
  display:flex; align-items:center; justify-content:center;
  font-size:13px; font-weight:700; color:$cyan;
  background:linear-gradient(135deg, rgba($cyan,.1), rgba(24,144,255,.1));
  border:1px solid rgba($cyan,.3);
}
.rp-info { flex:1; min-width:0; }
.rp-name { font-size:11px; font-weight:600; color:#fff; margin-bottom:3px; }
.rp-tags { display:flex; gap:3px; flex-wrap:wrap; }
.tg { font-size:8px; padding:1px 5px; border-radius:1px; letter-spacing:.3px; }
.tg-r { background:rgba($red,.18); color:#ff8090; border:1px solid rgba($red,.22); }
.tg-o { background:rgba($orange,.15); color:#ff9a55; border:1px solid rgba($orange,.2); }
.tg-c { background:rgba($cyan,.1); color:#80efff; border:1px solid rgba($cyan,.18); }
</style>

<template>
  <el-drawer
    :model-value="visible"
    @update:model-value="$emit('update:visible', $event)"
    direction="rtl"
    size="min(620px, 100vw)"
    class="department-incident-drawer"
    style="--el-bg-color:#071426;--el-fill-color-blank:#071426;--el-text-color-primary:#e8f4ff;--el-text-color-regular:#9ab5ce;--el-border-color:#183754"
    :title="`${departmentName} · 部门事件`"
  >
    <div class="dept-incident-body">
      <div class="dept-incident-scope">部门预警来自今日统计；其余数量与列表基于当前已加载未闭环预警</div>
      <div class="dept-incident-summary">
        <div><span>今日部门预警</span><strong>{{ departmentTotal }}</strong></div>
        <div><span>已加载事件</span><strong>{{ departmentEvents.length }}</strong></div>
        <div class="tone-danger"><span>高危</span><strong>{{ criticalCount }}</strong></div>
        <div class="tone-warning"><span>未分派</span><strong>{{ unassignedCount }}</strong></div>
        <div class="tone-danger"><span>已超时</span><strong>{{ overdueCount }}</strong></div>
      </div>

      <div v-if="departmentEvents.length" class="dept-incident-list">
        <article v-for="event in departmentEvents" :key="`${event.id}-${event.occurredAt}`" class="dept-incident-item">
          <button type="button" class="dept-incident-main" @click="$emit('show-event', event)">
            <div class="dept-incident-title-row">
              <span :class="['dept-incident-tag', `dept-tag-${event.level || 'medium'}`]">{{ eventLevelLabel(event) }}</span>
              <span class="dept-incident-title">{{ event.user || '未知人员' }} · {{ event.type || '预警' }}</span>
            </div>
            <span>{{ event.time || '刚刚' }} · 责任人：{{ event.owner || '未分派' }}</span>
            <span>位置：{{ event.location || '未接入定位' }} · 状态：{{ eventStatusLabel(event) }} · SLA：{{ eventSlaLabel(event) }}</span>
          </button>
          <button type="button" class="dept-incident-person" @click="$emit('show-profile', event)">健康画像</button>
          <button type="button" class="dept-incident-handle" @click="$emit('handle-event', event)">处置</button>
        </article>
      </div>
      <el-empty v-else :image-size="64" description="当前已加载范围内无该部门未闭环预警" />
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, type PropType } from 'vue'

interface DepartmentSummary {
  name?: string
  warnings?: number
  abnormal?: number
  [key: string]: unknown
}

interface DepartmentEvent {
  id: number | string
  occurredAt?: string
  dept?: string
  level?: string
  owner?: string
  slaStatus?: string
  sla?: string
  status?: string
  statusLabel?: string
  user?: string
  type?: string
  time?: string
  location?: string
  [key: string]: unknown
}

const props = defineProps({
  visible: { type: Boolean, default: false },
  department: { type: [Object, String] as PropType<DepartmentSummary | string | null>, default: null },
  events: { type: Array as PropType<DepartmentEvent[]>, default: () => [] }
})

defineEmits<{
  'update:visible': [value: boolean]
  'show-event': [event: DepartmentEvent]
  'handle-event': [event: DepartmentEvent]
  'show-profile': [event: DepartmentEvent]
}>()

const departmentName = computed(() => typeof props.department === 'string'
  ? props.department
  : (props.department?.name || '未分组'))
const departmentTotal = computed(() => typeof props.department === 'object'
  ? Number(props.department?.warnings || props.department?.abnormal || 0)
  : departmentEvents.value.length)
const departmentEvents = computed(() => props.events.filter((event) => (event.dept || '未分组') === departmentName.value))
const criticalCount = computed(() => departmentEvents.value.filter((event) => (event.level || '') === 'critical').length)
const unassignedCount = computed(() => departmentEvents.value.filter((event) => !event.owner || event.owner === '未分派').length)
const overdueCount = computed(() => departmentEvents.value.filter((event) => event.slaStatus === 'OVERDUE').length)

const eventLevelLabel = (event: DepartmentEvent) => {
  if (event.level === 'critical') return '特急'
  if (event.level === 'high') return '紧急'
  if (event.level === 'low') return '轻微'
  return '一般'
}

const eventStatusLabel = (event: DepartmentEvent) => {
  const status = String(event.status || '').toUpperCase()
  return ({
    NEW: '待确认',
    ACKED: '已确认',
    DISPATCHED: '已派遣',
    PROCESSING: '处理中',
    RESOLVED: '已处理',
    FALSE_ALARM: '误报关闭'
  }[status] || (event.statusLabel as string) || '待确认')
}

const eventSlaLabel = (event: DepartmentEvent) => {
  if (event.slaStatus === 'OVERDUE') return '已超时'
  if (event.sla && event.sla !== '未配置') return String(event.sla)
  return '未配置'
}
</script>

<style scoped lang="scss">
.dept-incident-body { display: grid; gap: 14px; }
.dept-incident-scope { color: #6e94b0; font-size: 12px; }
.dept-incident-summary { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; }
.dept-incident-summary > div { min-height: 82px; display: grid; align-content: center; gap: 6px; padding: 12px; border: 1px solid rgba(0,200,255,.16); border-radius: 8px; background: rgba(0,200,255,.045); }
.dept-incident-summary span { color: #6e94b0; font-size: 12px; }
.dept-incident-summary strong { color: #dff0ff; font: 700 24px/1 'JetBrains Mono','Courier New',monospace; }
.dept-incident-summary .tone-danger { border-color: rgba(255,59,59,.28); strong { color: #ff3b3b; } }
.dept-incident-summary .tone-warning { border-color: rgba(255,140,0,.26); strong { color: #ff8c00; } }
.dept-incident-list { display: grid; gap: 8px; }
.dept-incident-item { display: grid; grid-template-columns: minmax(0, 1fr) auto auto; align-items: center; gap: 8px; padding: 12px; border: 1px solid rgba(255,140,0,.2); border-radius: 8px; background: rgba(255,255,255,.025); }
.dept-incident-main { min-width: 0; display: grid; gap: 5px; text-align: left; border: 0; background: transparent; color: #6e94b0; cursor: pointer; }
.dept-incident-title-row { display: flex; align-items: center; gap: 8px; }
.dept-incident-tag { font-size: 11px; padding: 2px 6px; border-radius: 3px; font-weight: 700; line-height: 1; }
.dept-tag-critical { background: rgba(255,59,59,.22); color: #ff6b7b; border: 1px solid rgba(255,59,59,.35); }
.dept-tag-high { background: rgba(255,140,0,.2); color: #ff9f43; border: 1px solid rgba(255,140,0,.32); }
.dept-tag-medium { background: rgba(0,200,255,.15); color: #70dfff; border: 1px solid rgba(0,200,255,.28); }
.dept-tag-low { background: rgba(0,230,118,.12); color: #00e676; border: 1px solid rgba(0,230,118,.25); }
.dept-incident-title { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #dff0ff; font-weight: 700; font-size: 13px; }
.dept-incident-person, .dept-incident-handle { min-height: 32px; padding: 0 10px; border-radius: 5px; border: 1px solid rgba(0,200,255,.28); background: rgba(0,200,255,.07); color: #00c8ff; cursor: pointer; font-weight: 600; font-size: 12px; }
.dept-incident-handle { border-color: rgba(255,140,0,.3); color: #ff8c00; background: rgba(255,140,0,.08); }
:global(.department-incident-drawer.el-drawer) {
  --el-bg-color: #071426;
  --el-fill-color-blank: #071426;
  --el-text-color-primary: #e8f4ff;
  --el-text-color-regular: #9ab5ce;
  background:
    linear-gradient(180deg, rgba(255,255,255,.025), transparent 180px),
    #071426;
  border-left: 1px solid rgba(0, 200, 255, .24);
  box-shadow: -18px 0 48px rgba(0, 0, 0, .56);
}
:global(.department-incident-drawer .el-drawer__header) {
  min-height: 62px;
  margin-bottom: 0;
  padding: 16px 22px;
  border-bottom: 1px solid rgba(0, 200, 255, .18);
  background: #081a30;
  color: #e8f4ff;
}
:global(.department-incident-drawer .el-drawer__title) {
  color: #e8f4ff;
  font-size: 16px;
  font-weight: 700;
}
:global(.department-incident-drawer .el-drawer__close-btn) { color: #82a8c8; }
:global(.department-incident-drawer .el-drawer__close-btn:hover) { color: #00c8ff; }
:global(.department-incident-drawer .el-drawer__body) {
  padding: 18px;
  background: #071426;
  color: #dceeff;
}
:global(.department-incident-drawer .el-empty__description p) { color: #6e94b0; }
@media (max-width: 520px) {
  .dept-incident-summary { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .dept-incident-item { grid-template-columns: minmax(0, 1fr) auto; }
  .dept-incident-handle { grid-column: 2; }
}
</style>

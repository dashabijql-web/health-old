<template>
  <el-drawer
    v-model="visibleModel"
    direction="rtl"
    size="min(620px, 100vw)"
    :append-to-body="true"
    :destroy-on-close="false"
    class="incident-command-drawer"
    style="--el-bg-color:#071426;--el-drawer-bg-color:#071426;--el-text-color-primary:#e8f4ff;--el-text-color-regular:#a8c4dc;--el-text-color-secondary:#7696b2;--el-border-color:#183754;--el-border-color-lighter:rgba(24,55,84,.7);--el-fill-color-blank:#0a1a30;--el-fill-color-light:rgba(0,200,255,.08)"
    @open="loadIncident"
  >
    <template #header>
      <div class="incident-command-heading">
        <div class="incident-command-heading__title">
          <span>{{ incident?.typeLabel || event?.type || '事件详情' }}</span>
          <el-tag :type="severityTagType" effect="dark">{{ severityLabel }}</el-tag>
        </div>
        <el-button v-if="sourcePage === 'dashboard'" text type="primary" @click="emit('open-command-center')">查看现场态势</el-button>
        <el-button v-else-if="returnAvailable" text type="primary" @click="emit('return-to-origin')">返回统一管控</el-button>
      </div>
    </template>

    <div v-loading="loading" class="incident-command-body">
      <template v-if="incident">
        <section class="incident-summary">
          <div><span>人员</span><strong>{{ incident.person?.name || '--' }}</strong></div>
          <div><span>部门</span><strong>{{ incident.person?.department || '--' }}</strong></div>
          <div><span>位置</span><strong>{{ incident.location?.label || '未接入定位' }}</strong></div>
          <div><span>发生时间</span><strong>{{ incident.occurredAt || '--' }}</strong></div>
          <div><span>当前状态</span><strong>{{ statusLabel }}</strong></div>
          <div><span>责任人</span><strong>{{ incident.owner?.name || '未分派' }}</strong></div>
          <div><span>处置时限</span><strong>{{ slaText }}</strong></div>
          <div><span>当前指标</span><strong>{{ vitalText }}</strong></div>
        </section>

        <section v-if="canAssign" class="incident-action-section">
          <h3>责任分派</h3>
          <div class="assign-controls">
            <el-select v-model="selectedOwnerId" filterable placeholder="选择责任人">
              <el-option
                v-for="operator in operators"
                :key="operator.id"
                :label="operatorLabel(operator)"
                :value="operator.id"
              />
            </el-select>
            <el-input-number v-model="slaMinutes" :min="1" :max="1440" :step="5" controls-position="right" />
            <el-button type="primary" :loading="submitting === 'assign'" @click="assignOwner">分派</el-button>
          </div>
        </section>

        <section v-if="hasActions" class="incident-action-section">
          <h3>处置动作</h3>
          <el-input
            v-model="remark"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            placeholder="填写本次操作说明"
          />
          <div class="action-controls">
            <el-button v-if="allows('ACK')" :loading="submitting === 'ack'" @click="acknowledge">确认事件</el-button>
            <el-button v-if="allows('RESOLVE')" type="success" :loading="submitting === 'resolve'" @click="resolve">完成处理</el-button>
            <el-button v-if="allows('FALSE_ALARM')" type="warning" :loading="submitting === 'false-alarm'" @click="markFalseAlarm">标记误报</el-button>
          </div>
          <div class="external-controls">
            <el-input v-model="target" placeholder="通信或区域目标" maxlength="200" />
            <el-button v-if="allows('CALL')" :loading="submitting === 'call'" @click="runExternal('call')">呼叫</el-button>
            <el-button v-if="allows('BROADCAST')" :loading="submitting === 'broadcast'" @click="runExternal('broadcast')">广播</el-button>
            <el-button v-if="allows('EVACUATE')" type="danger" :loading="submitting === 'evacuate'" @click="runExternal('evacuate')">撤离</el-button>
          </div>
          <p class="external-note">未接入的通信、广播和撤离设备会记录“未配置”，不会下发指令。</p>
        </section>

        <section class="incident-timeline">
          <h3>事件时间线</h3>
          <el-timeline v-if="timeline.length">
            <el-timeline-item
              v-for="item in timeline"
              :key="item.actionId"
              :timestamp="item.createdAt"
              :type="item.result === 'NOT_CONFIGURED' ? 'warning' : 'primary'"
            >
              <strong>{{ actionLabel(item.action) }}</strong>
              <span>{{ item.result === 'NOT_CONFIGURED' ? '未配置，未下发' : '已记录' }}</span>
              <p v-if="item.target">目标：{{ item.target }}</p>
              <p v-if="item.remark">说明：{{ item.remark }}</p>
              <p>操作人：{{ item.operator || '--' }}</p>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else :image-size="56" description="暂无处置记录" />
        </section>
      </template>
      <el-empty v-else-if="!loading" description="无法读取事件详情" />
    </div>
  </el-drawer>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  acknowledgeCommandCenterIncident,
  assignCommandCenterIncident,
  executeCommandCenterExternalAction,
  falseAlarmCommandCenterIncident,
  getCommandCenterIncident,
  getCommandCenterIncidentTimeline,
  resolveCommandCenterIncident
} from '@/api/command-center'
import { getUserList } from '@/api/user'

const props = defineProps({
  visible: { type: Boolean, default: false },
  event: { type: Object, default: null },
  sourcePage: { type: String, default: '' },
  returnAvailable: { type: Boolean, default: false }
})

const emit = defineEmits(['update:visible', 'updated', 'open-command-center', 'return-to-origin'])
const incident = ref(null)
const timeline = ref([])
const operators = ref([])
const loading = ref(false)
const submitting = ref('')
const selectedOwnerId = ref(null)
const slaMinutes = ref(30)
const remark = ref('')
const target = ref('')

const visibleModel = computed({
  get: () => props.visible,
  set: (value) => emit('update:visible', value)
})
const severityLabel = computed(() => {
  const sev = String(incident.value?.severity || props.event?.severity || '').toUpperCase()
  if (sev === 'CRITICAL' || props.event?.level === 'critical') return '特急'
  if (sev === 'HIGH' || props.event?.level === 'high') return '紧急'
  if (sev === 'MEDIUM' || props.event?.level === 'medium') return '一般'
  if (sev === 'LOW' || props.event?.level === 'low') return '轻微'
  return '一般'
})
const severityTagType = computed(() => {
  const sev = String(incident.value?.severity || props.event?.severity || '').toUpperCase()
  if (sev === 'CRITICAL' || props.event?.level === 'critical') return 'danger'
  if (sev === 'HIGH' || props.event?.level === 'high') return 'warning'
  if (sev === 'MEDIUM' || props.event?.level === 'medium') return 'primary'
  return 'info'
})
const statusLabel = computed(() => ({
  NEW: '待确认',
  ACKED: '已确认',
  DISPATCHED: '已派遣',
  PROCESSING: '处理中',
  RESOLVED: '已处理',
  FALSE_ALARM: '误报关闭'
}[incident.value?.status] || '--'))
const slaText = computed(() => {
  const sla = incident.value?.sla
  if (!sla?.configured) return '未配置'
  return (sla.deadlineAt || '--') + ' · ' + (sla.message || '--')
})
const vitalText = computed(() => {
  const vital = incident.value?.vitalSnapshot
  return vital?.indicator ? vital.indicator + ': ' + (vital.value ?? '--') : '--'
})
const hasActions = computed(() => (incident.value?.availableActions || []).length > 0)
const canAssign = computed(() => allows('ASSIGN'))

watch(() => props.event?.incidentId, () => {
  incident.value = null
  timeline.value = []
  remark.value = ''
  target.value = ''
})

function allows(action) {
  return (incident.value?.availableActions || []).includes(action)
}

function operatorLabel(operator) {
  return operator.realName || operator.username || '用户 ' + operator.id
}

function actionLabel(action) {
  return {
    ACK: '确认事件',
    ASSIGN: '责任分派',
    RESOLVE: '完成处理',
    FALSE_ALARM: '误报关闭',
    CALL: '人员呼叫',
    BROADCAST: '区域广播',
    EVACUATE: '紧急撤离'
  }[action] || action
}

async function loadIncident() {
  if (!props.event?.id || !props.event?.occurredAt) return
  loading.value = true
  try {
    const [detailResponse, timelineResponse] = await Promise.all([
      getCommandCenterIncident(props.event.id, props.event.occurredAt),
      getCommandCenterIncidentTimeline(props.event.id, props.event.occurredAt)
    ])
    if (detailResponse.code === 200) {
      incident.value = detailResponse.data
      selectedOwnerId.value = detailResponse.data.owner?.userId || null
    }
    if (timelineResponse.code === 200) timeline.value = timelineResponse.data || []
    await loadOperators()
  } catch {
    incident.value = null
    timeline.value = []
    ElMessage.error('事件详情加载失败')
  } finally {
    loading.value = false
  }
}

async function loadOperators() {
  if (operators.value.length) return
  try {
    const response = await getUserList({ page: 1, size: 200, status: 0 })
    if (response.code === 200) operators.value = response.data?.list || []
  } catch {}
}

async function assignOwner() {
  if (!selectedOwnerId.value) {
    ElMessage.warning('请选择责任人')
    return
  }
  await runAction('assign', () => assignCommandCenterIncident(props.event.id, {
    occurredAt: props.event.occurredAt,
    ownerUserId: selectedOwnerId.value,
    slaMinutes: slaMinutes.value,
    remark: remark.value
  }))
}

async function acknowledge() {
  await runAction('ack', () => acknowledgeCommandCenterIncident(props.event.id, {
    occurredAt: props.event.occurredAt,
    remark: remark.value
  }))
}

async function resolve() {
  await runConfirmedAction('确认将该事件标记为已处理？', 'resolve', () => resolveCommandCenterIncident(props.event.id, {
    occurredAt: props.event.occurredAt,
    remark: remark.value
  }))
}

async function markFalseAlarm() {
  await runConfirmedAction('确认将该事件关闭为误报？', 'false-alarm', () => falseAlarmCommandCenterIncident(props.event.id, {
    occurredAt: props.event.occurredAt,
    remark: remark.value
  }))
}

async function runExternal(action) {
  if (!target.value.trim()) {
    ElMessage.warning('请填写通信或区域目标')
    return
  }
  await runAction(action, () => executeCommandCenterExternalAction(props.event.id, action, {
    occurredAt: props.event.occurredAt,
    target: target.value.trim(),
    remark: remark.value
  }))
}

async function runConfirmedAction(message, action, task) {
  try {
    await ElMessageBox.confirm(message, '确认操作', {
      type: 'warning',
      customClass: 'incident-command-confirm'
    })
  } catch {
    return
  }
  await runAction(action, task)
}

async function runAction(action, task) {
  if (!props.event?.id || !props.event?.occurredAt || submitting.value) return
  submitting.value = action
  try {
    const response = await task()
    if (response.code !== 200) throw new Error(response.message)
    const result = response.data
    if (result.status === 'NOT_CONFIGURED') ElMessage.warning(result.message)
    else ElMessage.success(result.message)
    incident.value = result.incident
    await refreshTimeline()
    emit('updated', result.incident)
  } catch (error) {
    ElMessage.error(error?.message || '操作失败')
  } finally {
    submitting.value = ''
  }
}

async function refreshTimeline() {
  try {
    const response = await getCommandCenterIncidentTimeline(props.event.id, props.event.occurredAt)
    if (response.code === 200) timeline.value = response.data || []
  } catch {}
}
</script>

<style scoped lang="scss">
.incident-command-heading { display:flex; align-items:center; justify-content:space-between; gap:10px; font-weight:700; }
.incident-command-heading__title { display:flex; min-width:0; align-items:center; gap:10px; }
.incident-command-heading__title > span { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.incident-command-body { padding:0 4px 24px; }
.incident-summary { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:0; border:1px solid var(--el-border-color-lighter); border-radius:6px; overflow:hidden; }
.incident-summary > div { display:grid; gap:4px; padding:11px 12px; border-bottom:1px solid var(--el-border-color-lighter); }
.incident-summary > div:nth-last-child(-n+2) { border-bottom:0; }
.incident-summary span { color:var(--el-text-color-secondary); font-size:12px; }
.incident-summary strong { overflow-wrap:anywhere; font-size:13px; }
.incident-action-section, .incident-timeline { margin-top:20px; }
h3 { margin:0 0 10px; font-size:14px; }
.assign-controls, .action-controls, .external-controls { display:flex; flex-wrap:wrap; gap:8px; align-items:center; }
.assign-controls .el-select { flex:1 1 180px; }
.assign-controls .el-input-number { width:128px; }
.incident-action-section :deep(.el-textarea) { margin-bottom:10px; }
.external-controls { margin-top:10px; }
.external-controls .el-input { flex:1 1 180px; }
.external-note { margin:8px 0 0; color:var(--el-text-color-secondary); font-size:12px; line-height:1.5; }
.incident-timeline :deep(.el-timeline-item__content) { display:grid; gap:3px; }
.incident-timeline p { margin:0; color:var(--el-text-color-secondary); font-size:12px; }
:global(.incident-command-drawer.el-drawer) {
  color:#a8c4dc;
  background:#071426;
  border-left:1px solid rgba(0,200,255,.24);
  box-shadow:-18px 0 54px rgba(0,0,0,.58), 0 0 30px rgba(0,180,255,.07);
}
:global(.incident-command-drawer .el-drawer__header) {
  margin:0;
  padding:18px 20px 14px;
  color:#e8f4ff;
  background:#081a30;
  border-bottom:1px solid rgba(0,200,255,.16);
}
:global(.incident-command-drawer .el-drawer__close-btn) { color:#82a8c8; }
:global(.incident-command-drawer .el-drawer__close-btn:hover) { color:#00c8ff; }
:global(.incident-command-drawer .el-drawer__body) {
  padding:18px 20px 24px;
  color:#a8c4dc;
  background:#071426;
}
:global(.incident-command-drawer .el-input__wrapper),
:global(.incident-command-drawer .el-textarea__inner),
:global(.incident-command-drawer .el-input-number .el-input__wrapper),
:global(.incident-command-drawer .el-select__wrapper) {
  color:#d8e8f5;
  background:rgba(0,30,56,.72);
  box-shadow:0 0 0 1px rgba(0,200,255,.22) inset;
}
:global(.incident-command-drawer .el-input__inner),
:global(.incident-command-drawer .el-textarea__inner) { color:#d8e8f5; }
:global(.incident-command-drawer .el-input__inner::placeholder),
:global(.incident-command-drawer .el-textarea__inner::placeholder) { color:#587690; }
:global(.incident-command-drawer .el-timeline-item__timestamp),
:global(.incident-command-drawer .el-empty__description p) { color:#7696b2; }
:global(.incident-command-confirm.el-message-box) {
  --el-bg-color:#081a30;
  --el-bg-color-overlay:#081a30;
  --el-fill-color-blank:#0a1a30;
  --el-border-color:rgba(0,200,255,.24);
  --el-text-color-primary:#e8f4ff;
  --el-text-color-regular:#a8c4dc;
  width:min(420px, calc(100vw - 24px));
  background:#081a30;
  border:1px solid rgba(0,200,255,.24);
  box-shadow:0 18px 54px rgba(0,0,0,.62), 0 0 28px rgba(0,180,255,.08);
}
:global(.incident-command-confirm .el-message-box__title) { color:#e8f4ff; }
:global(.incident-command-confirm .el-message-box__content) { color:#a8c4dc; }
:global(.incident-command-confirm .el-message-box__headerbtn .el-message-box__close) { color:#82a8c8; }
:global(.el-overlay:has(.incident-command-confirm) .el-overlay-message-box) {
  position:fixed;
  inset:0;
  display:flex;
  align-items:center;
  justify-content:center;
  padding:16px;
}
:global(.el-overlay:has(.incident-command-confirm) .el-overlay-message-box::after) { display:none; }
@media (max-width: 480px) {
  .incident-summary { grid-template-columns:1fr; }
  .incident-summary > div { border-bottom:1px solid var(--el-border-color-lighter) !important; }
  .incident-summary > div:last-child { border-bottom:0 !important; }
  .assign-controls > * { width:100%; }
}
</style>

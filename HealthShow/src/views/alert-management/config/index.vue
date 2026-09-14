<template>
  <div class="page-container alert-config-page">
    <WarningCenterNav />

    <header class="ac-hd">
      <div class="ac-hd-left">
        <span class="ac-live-dot"></span>
        <h1 class="ac-hd-title">风险规则配置</h1>
      </div>
      <div class="ac-hd-kpis">
        <div class="ac-kpi">
          <span class="ac-kpi-n">{{ configList.length }}</span>
          <span class="ac-kpi-l">体征规则</span>
        </div>
        <div class="ac-kpi success">
          <span class="ac-kpi-n">{{ enabledCount }}</span>
          <span class="ac-kpi-l">已启用</span>
        </div>
        <div class="ac-kpi">
          <span class="ac-kpi-n">{{ currentRiskLabel }}</span>
          <span class="ac-kpi-l">当前视图</span>
        </div>
      </div>
      <div class="ac-hd-time">{{ currentTime }}</div>
    </header>

    <div class="risk-tabs">
      <button v-for="tab in riskTabs" :key="tab.value"
              class="risk-tab" :class="{ active: activeRisk === tab.value }"
              @click="activeRisk = tab.value">
        {{ tab.label }}
      </button>
    </div>

    <div class="ac-scope-note">
      <strong>当前规则范围</strong>
      <span>体征即时预警与趋势风险共用以下正常边界；SOS、跌倒、房颤等设备报警由手表主动上报，暂无数值阈值。</span>
    </div>

    <div v-loading="loading" class="config-grid">
      <div v-for="item in filteredConfigList" :key="item.id" class="config-card" :class="{ 'is-disabled': !item.enabled }">
        <div class="card-top">
          <div class="card-info">
            <span class="card-symbol">{{ getConfigSymbol(item.configType) }}</span>
            <div>
              <div class="card-name">{{ item.configName }}</div>
              <div class="card-unit">单位: {{ item.unit }}</div>
            </div>
          </div>
          <el-switch :model-value="!!item.enabled" active-color="#38ef7d" inactive-color="#4a5578" @change="handleToggle(item)" />
        </div>

        <div class="range-bar-wrap">
          <div class="range-legend">
            <span class="legend-item"><span class="legend-dot critical"></span>高危</span>
            <span class="legend-item"><span class="legend-dot mid"></span>中危</span>
            <span class="legend-item"><span class="legend-dot warn"></span>低危</span>
            <span class="legend-item"><span class="legend-dot normal"></span>正常</span>
          </div>
          <div class="range-bar">
            <div class="range-segment critical-low" :style="{ width: calcWidth(item, item.criticalLow, item.warnMidLow) }">
              <span class="range-label">{{ item.criticalLow }}</span>
            </div>
            <div class="range-segment warn-mid-low" :style="{ width: calcWidth(item, item.warnMidLow, item.warnLow) }">
              <span class="range-label">{{ item.warnMidLow }}</span>
            </div>
            <div class="range-segment warn-low" :style="{ width: calcWidth(item, item.warnLow, item.normalMin) }">
              <span class="range-label">{{ item.warnLow }}</span>
            </div>
            <div class="range-segment normal" :style="{ width: calcWidth(item, item.normalMin, item.normalMax) }">
              <span class="range-label">{{ item.normalMin }}-{{ item.normalMax }}</span>
            </div>
            <div class="range-segment warn-high" :style="{ width: calcWidth(item, item.normalMax, item.warnHigh) }">
              <span class="range-label">{{ item.warnHigh }}</span>
            </div>
            <div class="range-segment warn-mid-high" :style="{ width: calcWidth(item, item.warnHigh, item.warnMidHigh) }">
              <span class="range-label">{{ item.warnMidHigh }}</span>
            </div>
            <div class="range-segment critical-high" :style="{ width: calcWidth(item, item.warnMidHigh, item.criticalHigh) }">
              <span class="range-label">{{ item.criticalHigh }}</span>
            </div>
          </div>
        </div>

        <div class="card-bottom">
          <el-button type="primary" size="small" @click="openEdit(item)"><el-icon><Edit /></el-icon> 编辑阈值</el-button>
        </div>
      </div>
      <div v-if="!loading && filteredConfigList.length === 0" class="ac-empty">
        <div class="ac-empty-icon">CFG</div>
        <div class="ac-empty-title">该风险等级暂无预警配置</div>
        <div class="ac-empty-desc">可以切换岗位风险标签查看其他配置</div>
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="`编辑 ${editForm.configName} 阈值`" width="560px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="editForm" :rules="formRules" label-width="100px" class="form-body">
        <div class="range-section">
          <div class="section-header normal-header"><span class="section-dot normal"></span>正常</div>
          <el-row :gutter="16">
            <el-col :span="12"><el-form-item label="最小值" prop="normalMin"><el-input-number v-model="editForm.normalMin" :precision="1" :step="1" controls-position="right" style="width:100%" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="最大值" prop="normalMax"><el-input-number v-model="editForm.normalMax" :precision="1" :step="1" controls-position="right" style="width:100%" /></el-form-item></el-col>
          </el-row>
        </div>
        <div class="range-section">
          <div class="section-header warn-header"><span class="section-dot warn"></span>低危预警</div>
          <el-row :gutter="16">
            <el-col :span="12"><el-form-item label="低值" prop="warnLow"><el-input-number v-model="editForm.warnLow" :precision="1" :step="1" controls-position="right" style="width:100%" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="高值" prop="warnHigh"><el-input-number v-model="editForm.warnHigh" :precision="1" :step="1" controls-position="right" style="width:100%" /></el-form-item></el-col>
          </el-row>
        </div>
        <div class="range-section">
          <div class="section-header mid-header"><span class="section-dot mid"></span>中危预警</div>
          <el-row :gutter="16">
            <el-col :span="12"><el-form-item label="低值" prop="warnMidLow"><el-input-number v-model="editForm.warnMidLow" :precision="1" :step="1" controls-position="right" style="width:100%" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="高值" prop="warnMidHigh"><el-input-number v-model="editForm.warnMidHigh" :precision="1" :step="1" controls-position="right" style="width:100%" /></el-form-item></el-col>
          </el-row>
        </div>
        <div class="range-section">
          <div class="section-header critical-header"><span class="section-dot critical"></span>高危预警</div>
          <el-row :gutter="16">
            <el-col :span="12"><el-form-item label="低值" prop="criticalLow"><el-input-number v-model="editForm.criticalLow" :precision="1" :step="1" controls-position="right" style="width:100%" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="高值" prop="criticalHigh"><el-input-number v-model="editForm.criticalHigh" :precision="1" :step="1" controls-position="right" style="width:100%" /></el-form-item></el-col>
          </el-row>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import WarningCenterNav from '@/components/WarningCenterNav.vue'
import { ref, reactive, computed, onMounted } from 'vue'
import { Edit } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { getAlertConfigList, updateAlertConfig, toggleAlertConfig } from '@/api/alert-config'
import { useClock } from '@/composables/useClock'

const { currentTime } = useClock()

interface AlertConfigItem {
  id: number
  configType: number
  configName: string
  unit: string
  riskLevel: number | null
  enabled: boolean
  normalMin: number
  normalMax: number
  warnLow: number
  warnHigh: number
  warnMidLow: number
  warnMidHigh: number
  criticalLow: number
  criticalHigh: number
}

interface RiskTab {
  label: string
  value: number
}

interface AlertConfigForm {
  id: number | null
  configName: string
  normalMin: number
  normalMax: number
  warnLow: number
  warnHigh: number
  warnMidLow: number
  warnMidHigh: number
  criticalLow: number
  criticalHigh: number
}

interface AlertConfigResponse {
  code: number
  data?: AlertConfigItem[]
  message?: string
}

const riskTabs: RiskTab[] = [
  { label: '默认（无工种）', value: null as unknown as number },
  { label: '低危岗位', value: 1 },
  { label: '中危岗位', value: 2 },
  { label: '高危岗位', value: 3 }
]
const activeRisk = ref<number | null>(null)

const loading = ref(false)
const configList = ref<AlertConfigItem[]>([])

const filteredConfigList = computed(() => {
  return configList.value.filter(item => {
    if (activeRisk.value === null) return item.riskLevel == null
    return item.riskLevel === activeRisk.value
  })
})

const enabledCount = computed(() => configList.value.filter(item => item.enabled).length)
const currentRiskLabel = computed(() => {
  const currentTab = riskTabs.find(tab => tab.value === activeRisk.value)
  return currentTab ? currentTab.label : '默认（无工种）'
})
const loadConfigList = async () => {
  loading.value = true
  try {
    const res = await getAlertConfigList() as unknown as AlertConfigResponse
    if (res.code === 200) configList.value = res.data || []
  } catch (e) { ElMessage.error('加载预警配置失败') }
  finally { loading.value = false }
}

const getConfigSymbol = (type: number) => ({
  1: 'HR',
  2: 'PRS',
  3: 'TMP',
  4: 'FAT',
  5: 'O2'
}[type] || 'CFG')

const calcWidth = (item: AlertConfigItem, from: number, to: number) => {
  const total = item.criticalHigh - item.criticalLow
  if (total <= 0) return '20%'
  return Math.max((Math.abs(to - from) / total) * 100, 5) + '%'
}

const handleToggle = async (item: AlertConfigItem) => {
  try {
    await ElMessageBox.confirm(`确定要${item.enabled ? '禁用' : '启用'}「${item.configName}」预警吗？`, '状态确认', { type: 'warning' })
    const res = await toggleAlertConfig(item.id)
    if (res.code === 200) { ElMessage.success('操作成功'); await loadConfigList() }
    else ElMessage.error(res.message || '操作失败')
  } catch (e) { /* cancelled */ }
}

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance | null>(null)
const editForm = reactive<AlertConfigForm>({ id: null, configName: '', normalMin: 0, normalMax: 0, warnLow: 0, warnHigh: 0, warnMidLow: 0, warnMidHigh: 0, criticalLow: 0, criticalHigh: 0 })

const validateRange = (_rule: unknown, _value: unknown, callback: (error?: string | Error) => void) => {
  const { criticalLow, warnMidLow, warnLow, normalMin, normalMax, warnHigh, warnMidHigh, criticalHigh } = editForm
  if (criticalLow > warnMidLow || warnMidLow > warnLow || warnLow > normalMin ||
      normalMin > normalMax || normalMax > warnHigh || warnHigh > warnMidHigh || warnMidHigh > criticalHigh) {
    callback(new Error('应满足: 高危低 ≤ 中危低 ≤ 低危低 ≤ 正常低 ≤ 正常高 ≤ 低危高 ≤ 中危高 ≤ 高危高'))
  } else callback()
}

const formRules: FormRules<AlertConfigForm> = {
  normalMin:   [{ required: true, message: '必填' }, { validator: validateRange, trigger: 'blur' }],
  normalMax:   [{ required: true, message: '必填' }, { validator: validateRange, trigger: 'blur' }],
  warnLow:     [{ required: true, message: '必填' }, { validator: validateRange, trigger: 'blur' }],
  warnHigh:    [{ required: true, message: '必填' }, { validator: validateRange, trigger: 'blur' }],
  warnMidLow:  [{ required: true, message: '必填' }, { validator: validateRange, trigger: 'blur' }],
  warnMidHigh: [{ required: true, message: '必填' }, { validator: validateRange, trigger: 'blur' }],
  criticalLow:  [{ required: true, message: '必填' }, { validator: validateRange, trigger: 'blur' }],
  criticalHigh: [{ required: true, message: '必填' }, { validator: validateRange, trigger: 'blur' }]
}

const openEdit = (item: AlertConfigItem) => {
  Object.assign(editForm, { id: item.id, configName: item.configName, normalMin: item.normalMin, normalMax: item.normalMax, warnLow: item.warnLow, warnHigh: item.warnHigh, warnMidLow: item.warnMidLow, warnMidHigh: item.warnMidHigh, criticalLow: item.criticalLow, criticalHigh: item.criticalHigh })
  dialogVisible.value = true
}

const submitForm = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const res = await updateAlertConfig({ id: editForm.id, normalMin: editForm.normalMin, normalMax: editForm.normalMax, warnLow: editForm.warnLow, warnHigh: editForm.warnHigh, warnMidLow: editForm.warnMidLow, warnMidHigh: editForm.warnMidHigh, criticalLow: editForm.criticalLow, criticalHigh: editForm.criticalHigh })
    if (res.code === 200) { ElMessage.success('保存成功'); dialogVisible.value = false; loadConfigList() }
    else ElMessage.error(res.message || '保存失败')
  } catch (e) { ElMessage.error('保存失败') }
  finally { submitting.value = false }
}

onMounted(() => loadConfigList())
</script>

<style scoped lang="scss">
@import '@/styles/dark-admin.scss';

.page-container {
  @include da-container;
  min-height: calc(100vh - 50px);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 16px;
  box-sizing: border-box;
}

.ac-scope-note {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin: 12px 0 4px;
  padding: 10px 12px;
  border-left: 3px solid #38bdf8;
  background: rgba(56, 189, 248, 0.08);
  color: #9bb6cc;
  font-size: 12px;
}
.ac-scope-note strong { color: #d9f1ff; white-space: nowrap; }

// ── 紧凑 Header ──
.ac-hd {
  height: 56px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  padding: 0 22px;
  gap: 20px;
  background: rgba(0, 6, 24, 0.65);
  border-bottom: 1px solid $da-border;
  border-radius: 10px;
}
.ac-hd-left { display: flex; align-items: center; gap: 10px; flex-shrink: 0; }
.ac-live-dot {
  width: 9px; height: 9px; border-radius: 50%;
  background: $da-accent;
  box-shadow: 0 0 8px rgba(0, 212, 255, 0.7);
  animation: acPulse 2s ease-in-out infinite;
}
@keyframes acPulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.45; transform: scale(0.7); }
}
.ac-hd-title {
  font-size: 20px; font-weight: 700; color: $da-text-bright; margin: 0;
  letter-spacing: 2px;
  background: linear-gradient(90deg, #00d4ff, #4facfe);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}
.ac-hd-kpis {
  flex: 1; display: flex; justify-content: center; gap: 0;
}
.ac-kpi {
  display: flex; flex-direction: column; align-items: center;
  padding: 0 24px;
  border-right: 1px solid $da-border;
  &:first-child { border-left: 1px solid $da-border; }
  &.success .ac-kpi-n { color: $da-success; }
}
.ac-kpi-n {
  font-size: 18px; font-weight: 700; font-family: 'Consolas', monospace; line-height: 1.1;
  color: $da-accent;
}
.ac-kpi-l { font-size: 11px; color: $da-text-dim; margin-top: 2px; white-space: nowrap; }
.ac-hd-time { flex-shrink: 0; font-family: 'Consolas', monospace; font-size: 13px; color: $da-text-dim; }

// ── 自定义空状态 ──
.ac-empty {
  grid-column: 1 / -1;
  display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  gap: 8px; padding: 60px 20px;
}
.ac-empty-icon {
  width: 56px; height: 56px; border-radius: 16px;
  background: rgba(0,212,255,0.08); border: 1px solid rgba(0,212,255,0.2);
  display: flex; align-items: center; justify-content: center;
  font-size: 14px; font-weight: 700; color: $da-accent;
  font-family: 'Consolas', monospace; letter-spacing: 0.08em;
  margin-bottom: 8px;
}
.ac-empty-title { font-size: 15px; color: $da-text-bright; font-weight: 600; }
.ac-empty-desc { font-size: 12px; color: $da-text-dim; }

.risk-tabs { display: flex; gap: 8px; margin-top: 16px; flex-shrink: 0; }
.risk-tab { padding: 7px 18px; border-radius: 20px; border: 1px solid $da-border-light; background: $da-panel; color: $da-text-dim; font-size: 13px; cursor: pointer; transition: all .2s;
  &:hover { border-color: $da-accent; color: $da-accent; }
  &.active { background: $da-accent-dim; border-color: $da-accent; color: $da-accent; font-weight: 600; }
}
.config-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(440px, 1fr)); gap: 16px; margin-top: 16px; flex: 1; min-height: 0; overflow-y: auto; align-content: start; }
.config-card { background: $da-panel; border: 1px solid $da-border; border-left: 4px solid $da-success; border-radius: 10px; padding: 20px; transition: transform .25s, box-shadow .25s;
  &:hover { transform: translateY(-3px); box-shadow: 0 8px 24px rgba(0,212,255,.15); }
  &.is-disabled { border-left-color: #4a5578; opacity: .7; }
}
.card-top { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18px; }
.card-info { display: flex; align-items: center; gap: 12px; }
.card-symbol {
  width: 48px;
  height: 48px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(145deg, rgba(0,212,255,.12), rgba(13, 28, 48, .82));
  border: 1px solid $da-border-light;
  border-radius: 12px;
  color: $da-accent;
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: .08em;
}
.card-name { font-size: 16px; font-weight: 600; color: $da-text-bright; }
.card-unit { font-size: 12px; color: $da-text-dim; margin-top: 2px; }

.range-bar-wrap { margin-bottom: 16px; }
.range-legend { display: flex; gap: 16px; margin-bottom: 8px; }
.legend-item { display: flex; align-items: center; gap: 5px; font-size: 11px; color: $da-text-dim; }
.legend-dot { display: inline-block; width: 8px; height: 8px; border-radius: 2px; &.critical { background: $da-danger; } &.mid { background: #ff9800; } &.warn { background: $da-warning; } &.normal { background: $da-success; } }
.range-bar { display: flex; height: 32px; border-radius: 6px; overflow: hidden; border: 1px solid $da-border-light; }
.range-segment { display: flex; align-items: center; justify-content: center; min-width: 30px;
  &.critical-low, &.critical-high { background: rgba(255,82,82,.35); }
  &.warn-mid-low, &.warn-mid-high { background: rgba(255,152,0,.35); }
  &.warn-low, &.warn-high { background: rgba(255,210,0,.3); }
  &.normal { background: rgba(56,239,125,.3); }
}
.range-label { font-size: 11px; font-weight: 600; color: $da-text-bright; white-space: nowrap; text-shadow: 0 1px 3px rgba(0,0,0,.5); }
.card-bottom { display: flex; justify-content: flex-end; }

.form-body { padding: 8px 0; }
.range-section { margin-bottom: 16px; padding: 16px; background: $da-panel-alt; border: 1px solid $da-border; border-radius: 8px; }
.section-header { display: flex; align-items: center; gap: 8px; font-size: 14px; font-weight: 600; margin-bottom: 14px; padding-bottom: 10px; border-bottom: 1px solid $da-border;
  &.normal-header { color: $da-success; } &.warn-header { color: $da-warning; } &.mid-header { color: #ff9800; } &.critical-header { color: $da-danger; }
}
.section-dot { display: inline-block; width: 10px; height: 10px; border-radius: 50%;
  &.normal { background: $da-success; box-shadow: 0 0 6px rgba(56,239,125,.5); }
  &.warn { background: $da-warning; box-shadow: 0 0 6px rgba(255,210,0,.5); }
  &.mid { background: #ff9800; box-shadow: 0 0 6px rgba(255,152,0,.5); }
  &.critical { background: $da-danger; box-shadow: 0 0 6px rgba(255,82,82,.5); }
}

@include da-el-overrides;
:deep(.el-input-number) { .el-input__wrapper { background: #0d1228; box-shadow: 0 0 0 1px $da-border-light inset; &:hover { box-shadow: 0 0 0 1px $da-accent inset; } } .el-input__inner { color: $da-text; } }
</style>

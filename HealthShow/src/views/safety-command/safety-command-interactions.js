import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { buildKpiDetailText, buildRenderedDeptAiReport } from './safety-command-view-model'
import {
  generateSafetyCommandDeptAi,
  loadSafetyCommandDeptAi
} from './safety-command-runtime'

export function useSafetyCommandInteractions({
  statsRef = ref({}),
  watchStatusRef = ref({})
} = {}) {
  const incidentDrawerVisible = ref(false)
  const areaDialogVisible = ref(false)
  const deptDialogVisible = ref(false)
  const broadcastDialogVisible = ref(false)
  const contactDialogVisible = ref(false)
  const infoDialogVisible = ref(false)
  const broadcastContent = ref('')
  const infoDialogTitle = ref('')
  const infoDialogContent = ref('')
  const currentArea = ref(null)
  const currentDept = ref(null)
  const currentEvent = ref(null)

  const deptAiReport = ref('')
  const deptAiTime = ref('')
  const deptAiLoading = ref(false)
  const deptAiRendered = computed(() => buildRenderedDeptAiReport(deptAiReport.value))

  const personDrawerVisible = ref(false)
  const personDrawerUserCode = ref('')
  const personDrawerUserName = ref('')

  function showInfoDialog(title, content) {
    infoDialogTitle.value = title
    infoDialogContent.value = content
    infoDialogVisible.value = true
  }

  function handleKpiDetail(key) {
    showInfoDialog('KPI 详情', buildKpiDetailText(key, statsRef.value, watchStatusRef.value))
  }

  function showAreaDetail(area) {
    currentArea.value = area
    areaDialogVisible.value = true
  }

  function showDeptDetail(dept) {
    if (typeof dept === 'string') {
      showInfoDialog('部门', `<p>${dept}</p>`)
      return
    }
    currentDept.value = dept
    deptDialogVisible.value = true
  }

  function showEventDetail(event) {
    currentEvent.value = event
    incidentDrawerVisible.value = true
  }

  async function onDeptDialogOpen() {
    deptAiReport.value = ''
    deptAiTime.value = ''
    const reportData = await loadSafetyCommandDeptAi(currentDept.value?.name)
    if (!reportData) return
    deptAiReport.value = reportData.reportContent
    deptAiTime.value = reportData.generateTime
  }

  async function handleDeptAi(force = false) {
    if (deptAiLoading.value || !currentDept.value?.name) return
    deptAiLoading.value = true
    try {
      const result = await generateSafetyCommandDeptAi(currentDept.value.name, force)
      if (result.ok && result.data) {
        deptAiReport.value = result.data.reportContent
        deptAiTime.value = result.data.generateTime
        ElMessage.success('部门 AI 分析完成')
      } else {
        ElMessage.error(result.message || '生成失败')
      }
    } catch {
      ElMessage.error('AI 服务暂时不可用')
    } finally {
      deptAiLoading.value = false
    }
  }

  function emergencyCall() {
    contactDialogVisible.value = true
  }

  function emergencyBroadcast() {
    broadcastContent.value = ''
    broadcastDialogVisible.value = true
  }

  function confirmBroadcast() {
    if (!broadcastContent.value.trim()) {
      ElMessage.warning('请输入广播内容')
      return
    }
    broadcastDialogVisible.value = false
    showInfoDialog('广播未发送', '当前环境尚未配置真实广播设备接口，本次内容未下发。接入广播服务后，这里将显示发送状态和回执。')
  }

  function emergencyEvacuate() {
    return ElMessageBox.confirm(
      '确认启动紧急撤离？此操作将向所有井下人员下达撤离指令！',
      '紧急撤离确认',
      {
        confirmButtonText: '确认撤离',
        cancelButtonText: '取消',
        type: 'error'
      }
    )
      .then(() => showInfoDialog('撤离未下发', '当前环境尚未配置真实撤离指令接口，本次未向井下人员下发指令。'))
      .catch(() => {})
  }

  function onShowPerson(person) {
    personDrawerUserCode.value = person.userCode || ''
    personDrawerUserName.value = person.name || ''
    personDrawerVisible.value = true
  }

  function onShowPersonFromEvent(event) {
    personDrawerUserCode.value = event.userCode || ''
    personDrawerUserName.value = event.user || ''
    personDrawerVisible.value = true
  }

  function onHandleEvent(event) {
    showEventDetail(event)
  }

  return {
    areaDialogVisible,
    broadcastContent,
    broadcastDialogVisible,
    confirmBroadcast,
    contactDialogVisible,
    currentArea,
    currentDept,
    currentEvent,
    deptAiLoading,
    deptAiRendered,
    deptAiReport,
    deptAiTime,
    deptDialogVisible,
    emergencyBroadcast,
    emergencyCall,
    emergencyEvacuate,
    handleDeptAi,
    handleKpiDetail,
    incidentDrawerVisible,
    infoDialogContent,
    infoDialogTitle,
    infoDialogVisible,
    onDeptDialogOpen,
    onHandleEvent,
    onShowPerson,
    onShowPersonFromEvent,
    personDrawerUserCode,
    personDrawerUserName,
    personDrawerVisible,
    showAreaDetail,
    showDeptDetail,
    showEventDetail,
    showInfoDialog
  }
}

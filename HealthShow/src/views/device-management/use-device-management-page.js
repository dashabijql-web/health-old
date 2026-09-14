import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatDate } from '@/utils'
import { useClock } from '@/composables/useClock'
import { useIntervalTask } from '@/composables/useIntervalTask'
import { useTimeoutTask } from '@/composables/useTimeoutTask'
import {
  bindDevice,
  clearDeviceBuffer,
  fetchOnlineDeviceList,
  pushDeviceMessage,
  closeDeviceFault,
  registerDeviceFault,
  searchDeviceUsers,
  transferDeviceBuffer,
  unbindDeviceById
} from './device-management-runtime'
import {
  applyDeviceRouteFilters,
  buildDeviceStats,
  buildFilteredDeviceList,
  createBindFormState,
  createMessageFormState,
  createPaginationState,
  createTransferFormState,
  deviceOnlineLabel,
  deviceOnlineLevel,
  deviceAbnormalLabel,
  deviceAbnormalTagType,
  formatLostDuration,
  paginateDeviceList,
  resetBindForm,
  resetDeviceFilters,
  resetMessageForm,
  resetTransferForm
} from './device-management-view-model'

export function useDeviceManagementPage() {
  const { currentTime } = useClock()
  const route = useRoute()

  const deviceList = ref([])
  const loading = ref(false)

  const filters = reactive({
    searchImei: '',
    filterOnline: null,
    filterBind: null,
    filterWarning: false,
    filterLowBattery: false,
    filterWarningVal: '',
    filterBatteryVal: '',
    filterOperationalVal: ''
  })

  const pagination = reactive(createPaginationState())
  const currentDevice = ref({})

  const detailDrawerVisible = ref(false)
  const detailDevice = ref(null)

  const bindDialogVisible = ref(false)
  const bindForm = reactive(createBindFormState())

  const transferDialogVisible = ref(false)
  const transferForm = reactive(createTransferFormState())

  const messageDialogVisible = ref(false)
  const messageForm = reactive(createMessageFormState())

  const userSearchCache = new Map()
  let queuedUserSearch = { query: '', callback: () => {} }

  const filteredDeviceList = computed(() => {
    return buildFilteredDeviceList(deviceList.value, filters)
  })

  const deviceStats = computed(() => {
    return buildDeviceStats(deviceList.value)
  })

  const paginatedDeviceList = computed(() => {
    return paginateDeviceList(filteredDeviceList.value, pagination)
  })

  const getTableIndex = (index) => {
    return (pagination.page - 1) * pagination.size + index + 1
  }

  const refreshDevices = async () => {
    loading.value = true
    try {
      deviceList.value = await fetchOnlineDeviceList()
      pagination.total = deviceList.value.length
    } catch {
      ElMessage.error('获取设备列表失败')
    } finally {
      loading.value = false
    }
  }

  const { start: startDeviceRefresh } = useIntervalTask(refreshDevices, 30000)

  const { start: startUserSearchTask } = useTimeoutTask(async () => {
    const { query, callback } = queuedUserSearch

    if (userSearchCache.has(query)) {
      callback(userSearchCache.get(query))
      return
    }

    try {
      const suggestions = await searchDeviceUsers(query)
      userSearchCache.set(query, suggestions)
      callback(suggestions)
    } catch {
      callback([])
    }
  }, 300)

  const resetFilters = () => {
    resetDeviceFilters(filters, pagination)
  }

  function applyRouteFilters(query = route.query) {
    applyDeviceRouteFilters(query, filters)
  }

  const openDetail = (row) => {
    detailDevice.value = row
    detailDrawerVisible.value = true
  }

  const handleSendMessage = (row) => {
    currentDevice.value = row
    resetMessageForm(messageForm)
    messageDialogVisible.value = true
  }

  const confirmSendMessage = async () => {
    const text = messageForm.text.trim()
    if (!text) return

    try {
      const response = await pushDeviceMessage(currentDevice.value.imei, text)
      if (response.code === 200) {
        ElMessage.success('消息发送成功')
        messageDialogVisible.value = false
        return
      }
      ElMessage.error(response.message || '消息发送失败')
    } catch {
      ElMessage.error('消息发送失败')
    }
  }

  const searchUsers = (queryString, callback) => {
    if (!queryString || queryString.trim().length === 0) {
      callback([])
      return
    }

    const query = queryString.trim()
    queuedUserSearch = { query, callback }
    startUserSearchTask()
  }

  const handleSelectUser = (item) => {
    bindForm.userId = item.id
    bindForm.userName = item.realName
    bindForm.userDept = item.deptName
    bindForm.searchKey = item.realName
  }

  const handleBind = (row) => {
    currentDevice.value = row
    resetBindForm(bindForm)
    bindDialogVisible.value = true
  }

  const confirmBind = async () => {
    if (!bindForm.userId) {
      ElMessage.warning('请选择用户')
      return
    }

    try {
      const response = await bindDevice(currentDevice.value.id, bindForm.userId)
      if (response.code === 200) {
        ElMessage.success('绑定成功')
        bindDialogVisible.value = false
        await refreshDevices()
        return
      }
      ElMessage.error(response.message || '绑定失败')
    } catch {
      ElMessage.error('绑定设备失败')
    }
  }

  const handleUnbind = (row) => {
    ElMessageBox.confirm('确定要解绑此设备吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(async () => {
      try {
        const response = await unbindDeviceById(row.id)
        if (response.code === 200) {
          ElMessage.success('解绑成功')
          await refreshDevices()
          return
        }
        ElMessage.error(response.message || '解绑失败')
      } catch {
        ElMessage.error('解绑设备失败')
      }
    }).catch(() => {})
  }

  const handleSelectTransferUser = (item) => {
    transferForm.userId = item.id
    transferForm.userName = item.realName
    transferForm.userDept = item.deptName
    transferForm.searchKey = item.realName
  }

  const handleTransfer = (row) => {
    currentDevice.value = row
    resetTransferForm(transferForm)
    transferDialogVisible.value = true
  }

  const confirmTransfer = async () => {
    if (!transferForm.userId) {
      ElMessage.warning('请选择目标用户')
      return
    }

    try {
      const response = await transferDeviceBuffer(currentDevice.value.id, transferForm.userId)
      if (response.code === 200) {
        ElMessage.success(`成功转移 ${response.data.transferred_count} 条数据`)
        transferDialogVisible.value = false
        await refreshDevices()
        return
      }
      ElMessage.error(response.message || '转移失败')
    } catch {
      ElMessage.error('转移数据失败')
    }
  }

  const handleDeleteBuffer = (row) => {
    ElMessageBox.confirm(
      `确定要清空设备 ${row.imei} 的 ${row.bufferCount} 条缓冲数据吗？此操作不可恢复！`,
      '警告',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    ).then(async () => {
      try {
        const response = await clearDeviceBuffer(row.id)
        if (response.code === 200) {
          ElMessage.success('清空成功')
          await refreshDevices()
          return
        }
        ElMessage.error(response.message || '清空失败')
      } catch {
        ElMessage.error('清空缓冲数据失败')
      }
    }).catch(() => {})
  }

  const handleMarkFault = async (row) => {
    try {
      const { value } = await ElMessageBox.prompt(
        `登记设备 ${row.imei} 的故障事实，登记后将进入故障设备统计。`,
        '登记设备故障',
        {
          confirmButtonText: '登记',
          cancelButtonText: '取消',
          inputPlaceholder: '请输入具体故障现象',
          inputValidator: (text) => !!text?.trim() || '故障说明不能为空'
        }
      )
      const response = await registerDeviceFault(row.id, {
        faultCode: 'MANUAL',
        faultDescription: value.trim()
      })
      if (response.code !== 200) {
        ElMessage.error(response.message || '故障登记失败')
        return
      }
      ElMessage.success('设备故障已登记并分派给当前操作人')
      detailDrawerVisible.value = false
      await refreshDevices()
    } catch (error) {
      if (error !== 'cancel' && error !== 'close') ElMessage.error('故障登记失败')
    }
  }

  const handleResolveFault = async (row) => {
    try {
      const { value } = await ElMessageBox.prompt(
        `确认设备 ${row.imei} 已恢复正常。`,
        '关闭设备故障',
        {
          confirmButtonText: '确认恢复',
          cancelButtonText: '取消',
          inputPlaceholder: '填写恢复情况或处理结果',
          inputValidator: (text) => !!text?.trim() || '处理结果不能为空'
        }
      )
      const response = await closeDeviceFault(row.id, value.trim())
      if (response.code !== 200) {
        ElMessage.error(response.message || '故障关闭失败')
        return
      }
      ElMessage.success('设备故障已关闭')
      detailDrawerVisible.value = false
      await refreshDevices()
    } catch (error) {
      if (error !== 'cancel' && error !== 'close') ElMessage.error('故障关闭失败')
    }
  }

  const handleSizeChange = (size) => {
    pagination.size = size
    pagination.page = 1
  }

  const handleCurrentChange = (page) => {
    pagination.page = page
  }

  onMounted(async () => {
    applyRouteFilters()
    await refreshDevices()
    startDeviceRefresh()
  })

  watch(() => route.fullPath, async () => {
    if (route.name !== 'DeviceList') return
    pagination.page = 1
    applyRouteFilters()
    await refreshDevices()
  })

  return {
    bindDialogVisible,
    bindForm,
    confirmBind,
    confirmSendMessage,
    confirmTransfer,
    currentDevice,
    currentTime,
    detailDevice,
    detailDrawerVisible,
    deviceList,
    deviceOnlineLabel,
    deviceOnlineLevel,
    deviceAbnormalLabel,
    deviceAbnormalTagType,
    formatLostDuration,
    deviceStats,
    filteredDeviceList,
    formatDate,
    getTableIndex,
    handleBind,
    handleCurrentChange,
    handleDeleteBuffer,
    handleMarkFault,
    handleResolveFault,
    handleSelectTransferUser,
    handleSelectUser,
    handleSendMessage,
    handleSizeChange,
    handleTransfer,
    handleUnbind,
    loading,
    messageDialogVisible,
    messageForm,
    openDetail,
    paginatedDeviceList,
    pagination,
    refreshDevices,
    resetFilters,
    searchImei: computed({
      get: () => filters.searchImei,
      set: (value) => { filters.searchImei = value }
    }),
    searchUsers,
    filterOnline: computed({
      get: () => filters.filterOnline,
      set: (value) => { filters.filterOnline = value }
    }),
    filterBind: computed({
      get: () => filters.filterBind,
      set: (value) => { filters.filterBind = value }
    }),
    filterWarning: computed({
      get: () => filters.filterWarning,
      set: (value) => { filters.filterWarning = value }
    }),
    filterLowBattery: computed({
      get: () => filters.filterLowBattery,
      set: (value) => { filters.filterLowBattery = value }
    }),
    filterWarningVal: computed({
      get: () => filters.filterWarningVal,
      set: (value) => { filters.filterWarningVal = value }
    }),
    filterBatteryVal: computed({
      get: () => filters.filterBatteryVal,
      set: (value) => { filters.filterBatteryVal = value }
    }),
    filterOperationalVal: computed({
      get: () => filters.filterOperationalVal,
      set: (value) => { filters.filterOperationalVal = value }
    }),
    transferDialogVisible,
    transferForm
  }
}

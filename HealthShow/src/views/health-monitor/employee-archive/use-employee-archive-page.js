import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { renderMarkdown } from '@/utils/lazy-vendors'
import { useTimeoutTask } from '@/composables/useTimeoutTask'
import {
  createEmployeeArchiveEmployee,
  deleteEmployeeArchiveEmployee,
  fetchEmployeeArchiveEmployees,
  fetchEmployeeArchiveOptions,
  generateEmployeeArchiveAiReport,
  generateEmployeeArchiveDeptReport,
  updateEmployeeArchiveEmployee
} from './employee-archive-runtime'
import {
  applyEmployeeToArchiveForm,
  buildEmployeeArchiveDeptList,
  buildEmployeeArchivePortraitRoute,
  buildEmployeeArchivePrintHtml,
  buildEmployeeArchiveSubmitPayload,
  buildFilteredEmployees,
  calcEmployeeArchiveAge,
  createEmptyEmployeeArchiveForm,
  paginateEmployeeArchiveEmployees,
  resolveEmployeeHealthBarColor
} from './employee-archive-view-model'

const EMPLOYEE_ARCHIVE_FORM_RULES = {
  empName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  empCode: [{ required: true, message: '请输入工号', trigger: 'blur' }],
  gender: [{ required: true, message: '请选择性别', trigger: 'change' }],
  deptId: [{ required: true, message: '请选择部门', trigger: 'change' }]
}

export function useEmployeeArchivePage() {
  const router = useRouter()

  const loading = ref(false)
  const blockingLoading = ref(false)
  const employees = ref([])
  const keyword = ref('')
  const selectedDept = ref('')
  const currentPage = ref(1)
  const pageSize = 20
  const printWindowRef = ref(null)
  const { start: startPrintTask } = useTimeoutTask(() => printWindowRef.value?.print(), 600)

  const deptOptions = ref([])
  const jobTypeOptions = ref([])

  const deptList = computed(() => buildEmployeeArchiveDeptList(employees.value))
  const filtered = computed(() => buildFilteredEmployees(employees.value, keyword.value, selectedDept.value))
  const paginated = computed(() => paginateEmployeeArchiveEmployees(filtered.value, currentPage.value, pageSize))

  watch([keyword, selectedDept], () => {
    currentPage.value = 1
  })

  async function loadData() {
    loading.value = true
    blockingLoading.value = true
    const releaseBlockingLoading = setTimeout(() => {
      blockingLoading.value = false
    }, 900)
    try {
      employees.value = await fetchEmployeeArchiveEmployees()
    } catch {
      // keep silent to preserve current UX
    } finally {
      clearTimeout(releaseBlockingLoading)
      blockingLoading.value = false
      loading.value = false
    }
  }

  async function loadOptions() {
    try {
      const options = await fetchEmployeeArchiveOptions()
      deptOptions.value = options.deptOptions
      jobTypeOptions.value = options.jobTypeOptions
    } catch {
      // keep silent to preserve current UX
    }
  }

  function openPortrait(employee) {
    router.push(buildEmployeeArchivePortraitRoute(employee))
  }

  function openRealtime(employee) {
    router.push(buildEmployeeArchivePortraitRoute(employee))
  }

  const dialogVisible = ref(false)
  const dialogTitle = ref('新增职工')
  const submitting = ref(false)
  const formRef = ref(null)
  const isEdit = ref(false)
  const form = reactive(createEmptyEmployeeArchiveForm())

  function resetFormState() {
    Object.assign(form, createEmptyEmployeeArchiveForm())
  }

  function handleAdd() {
    isEdit.value = false
    dialogTitle.value = '新增职工'
    resetFormState()
    dialogVisible.value = true
  }

  function handleEdit(employee) {
    isEdit.value = true
    dialogTitle.value = '编辑职工'
    applyEmployeeToArchiveForm(form, employee)
    dialogVisible.value = true
  }

  async function submitForm() {
    const valid = await formRef.value.validate().catch(() => false)
    if (!valid) return

    submitting.value = true
    try {
      const payload = buildEmployeeArchiveSubmitPayload(form)
      const response = isEdit.value
        ? await updateEmployeeArchiveEmployee(payload)
        : await createEmployeeArchiveEmployee(payload)

      if (response.code === 200) {
        ElMessage.success(isEdit.value ? '修改成功' : '新增成功')
        dialogVisible.value = false
        await loadData()
        return
      }
      ElMessage.error(response.message || '操作失败')
    } catch {
      ElMessage.error('操作失败，请重试')
    } finally {
      submitting.value = false
    }
  }

  async function handleDelete(employee) {
    try {
      await ElMessageBox.confirm(
        `确定要删除职工「${employee.empName}」吗？删除后不可恢复。`,
        '删除确认',
        { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
      )

      const response = await deleteEmployeeArchiveEmployee(employee.id)
      if (response.code === 200) {
        ElMessage.success('删除成功')
        await loadData()
        return
      }
      ElMessage.error(response.message || '删除失败')
    } catch (error) {
      if (error !== 'cancel') {
        ElMessage.error('删除失败，请重试')
      }
    }
  }

  function resetForm() {
    formRef.value?.resetFields()
    resetFormState()
  }

  const reportVisible = ref(false)
  const reportLoading = ref(false)
  const reportContent = ref('')
  const reportCurrentEmp = ref(null)
  const reportHtml = ref('')

  async function openAiReport(employee) {
    reportCurrentEmp.value = employee
    reportContent.value = ''
    reportHtml.value = ''
    reportLoading.value = true
    reportVisible.value = true

    try {
      const response = await generateEmployeeArchiveAiReport(employee.empCode)
      if (response.code === 200) {
        reportContent.value = response.data.report || ''
        reportHtml.value = await renderMarkdown(reportContent.value)
        return
      }
      ElMessage.error(response.message || '报告生成失败')
      reportContent.value = ''
      reportHtml.value = ''
    } catch {
      ElMessage.error('报告生成失败，请稍后重试')
      reportContent.value = ''
      reportHtml.value = ''
    } finally {
      reportLoading.value = false
    }
  }

  function printReport() {
    if (!reportContent.value) return
    const employee = reportCurrentEmp.value
    const html = buildEmployeeArchivePrintHtml({
      title: `健康诊断报告 - ${employee?.empName || ''}`,
      bodyHtml: reportHtml.value,
      generatedAt: new Date().toLocaleString('zh-CN')
    })
    const win = window.open('', '_blank')
    if (!win) {
      ElMessage.warning('请允许弹出窗口以打印报告')
      return
    }
    win.document.write(html)
    win.document.close()
    printWindowRef.value = win
    startPrintTask()
  }

  const deptReportVisible = ref(false)
  const deptReportLoading = ref(false)
  const deptReportContent = ref('')
  const deptReportHtml = ref('')

  async function openDeptReport() {
    if (!selectedDept.value) return

    deptReportContent.value = ''
    deptReportHtml.value = ''
    deptReportLoading.value = true
    deptReportVisible.value = true

    try {
      const response = await generateEmployeeArchiveDeptReport(selectedDept.value)
      if (response.code === 200) {
        deptReportContent.value = response.data.report || ''
        deptReportHtml.value = await renderMarkdown(deptReportContent.value)
        return
      }
      ElMessage.error(response.message || '部门报告生成失败')
      deptReportContent.value = ''
      deptReportHtml.value = ''
    } catch {
      ElMessage.error('部门报告生成失败，请稍后重试')
      deptReportContent.value = ''
      deptReportHtml.value = ''
    } finally {
      deptReportLoading.value = false
    }
  }

  function printDeptReport() {
    if (!deptReportContent.value) return
    const html = buildEmployeeArchivePrintHtml({
      title: `部门健康报告 - ${selectedDept.value}`,
      bodyHtml: deptReportHtml.value,
      generatedAt: new Date().toLocaleString('zh-CN')
    })
    const win = window.open('', '_blank')
    if (!win) {
      ElMessage.warning('请允许弹出窗口')
      return
    }
    win.document.write(html)
    win.document.close()
    printWindowRef.value = win
    startPrintTask()
  }

  onMounted(() => {
    void loadData()
    void loadOptions()
  })

  return {
    blockingLoading,
    calcAge: calcEmployeeArchiveAge,
    currentPage,
    deptList,
    deptOptions,
    deptReportContent,
    deptReportHtml,
    deptReportLoading,
    deptReportVisible,
    dialogTitle,
    dialogVisible,
    filtered,
    form,
    formRef,
    handleAdd,
    handleDelete,
    handleEdit,
    healthBarColor: resolveEmployeeHealthBarColor,
    jobTypeOptions,
    keyword,
    loading,
    openAiReport,
    openDeptReport,
    openPortrait,
    openRealtime,
    pageSize,
    paginated,
    printDeptReport,
    printReport,
    reportContent,
    reportHtml,
    reportLoading,
    reportVisible,
    resetForm,
    rules: EMPLOYEE_ARCHIVE_FORM_RULES,
    selectedDept,
    submitForm,
    submitting
  }
}

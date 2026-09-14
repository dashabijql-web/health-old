import { computed, onMounted, reactive, ref } from 'vue'
import { useStore } from 'vuex'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatDate } from '@/utils'
import { useClock } from '@/composables/useClock'
import {
  createUserRecord,
  deleteUserRecord,
  fetchAvailableRoles,
  fetchUserDepartments,
  fetchUserDetail,
  fetchUserHealthStats,
  fetchUserList,
  fetchUserStats,
  updateUserRecord,
  updateUserRecordStatus
} from './user-list-runtime'
import {
  buildDepartmentOptionList,
  buildUserFormFromDetail,
  buildUserSubmitPayload,
  createEmptyUserForm
} from './user-list-view-model'

export function useUserListPage() {
  const store = useStore()
  const { currentTime } = useClock('YYYY/MM/DD HH:mm:ss')

  const loading = ref(false)
  const userList = ref([])
  const stats = reactive({
    totalUsers: 0,
    activeUsers: 0,
    onlineUsers: 0,
    inactiveUsers: 0,
    onlineRate: 0
  })
  const searchForm = reactive({
    keyword: '',
    status: null,
    deptId: null
  })
  const pagination = reactive({
    page: 1,
    size: 20,
    total: 0
  })
  const deptList = ref([])
  const availableRoles = ref([])
  const rolesLoading = ref(false)

  const formDialog = reactive({
    visible: false,
    isEdit: false,
    submitting: false,
    form: createEmptyUserForm()
  })

  const detailDialog = reactive({
    visible: false,
    data: null
  })

  const healthDialog = reactive({
    visible: false,
    data: null
  })

  const userFormRef = ref(null)

  const formRules = {
    realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
    phone: [
      { required: true, message: '请输入手机号', trigger: 'blur' },
      { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
    ],
    email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
    password: [{
      validator: (rule, value, callback) => {
        if (!formDialog.isEdit && !value) {
          callback(new Error('请输入密码'))
          return
        }
        if (value && value.length < 6) {
          callback(new Error('密码长度至少 6 位'))
          return
        }
        callback()
      },
      trigger: 'blur'
    }]
  }

  const buttons = computed(() => store.getters.buttons)

  function hasPerm(code) {
    const currentButtons = buttons.value
    if (!currentButtons || currentButtons.length === 0) {
      return true
    }
    return currentButtons.includes(code)
  }

  async function loadUserStats() {
    try {
      const response = await fetchUserStats()
      if (response.code === 200) {
        Object.assign(stats, response.data)
      }
    } catch {
      // ignore to preserve current UX
    }
  }

  async function loadUserList() {
    loading.value = true
    try {
      const response = await fetchUserList({
        ...searchForm,
        page: pagination.page,
        size: pagination.size
      })
      if (response.code === 200) {
        userList.value = response.data.list
        pagination.total = response.data.total
      }
    } catch {
      ElMessage.error('加载用户列表失败')
    } finally {
      loading.value = false
    }
  }

  async function loadAvailableRoles() {
    rolesLoading.value = true
    try {
      const response = await fetchAvailableRoles()
      if (response.code === 200) {
        availableRoles.value = response.data
      }
    } catch {
      // ignore to preserve current UX
    } finally {
      rolesLoading.value = false
    }
  }

  async function loadDepartments() {
    try {
      const departmentList = await fetchUserDepartments()
      deptList.value = buildDepartmentOptionList(departmentList)
    } catch {
      // ignore to preserve current UX
    }
  }

  function handleSearch() {
    pagination.page = 1
    void loadUserList()
  }

  function handleReset() {
    searchForm.keyword = ''
    searchForm.status = null
    searchForm.deptId = null
    handleSearch()
  }

  function handleSizeChange(size) {
    pagination.size = size
    void loadUserList()
  }

  function handleCurrentChange(page) {
    pagination.page = page
    void loadUserList()
  }

  function handleAdd() {
    formDialog.isEdit = false
    formDialog.form = createEmptyUserForm()
    formDialog.visible = true
  }

  async function handleEdit(row) {
    try {
      const response = await fetchUserDetail(row.id)
      if (response.code === 200) {
        formDialog.form = buildUserFormFromDetail(response.data)
        formDialog.isEdit = true
        formDialog.visible = true
      }
    } catch {
      ElMessage.error('加载用户信息失败')
    }
  }

  function handleFormSubmit() {
    userFormRef.value.validate(async (valid) => {
      if (!valid) {
        return
      }
      formDialog.submitting = true
      try {
        const payload = buildUserSubmitPayload(formDialog.form)
        const response = formDialog.isEdit
          ? await updateUserRecord(payload)
          : await createUserRecord(payload)

        if (response.code === 200) {
          ElMessage.success(formDialog.isEdit ? '修改成功' : '新增成功')
          userFormRef.value?.clearValidate()
          formDialog.visible = false
          await loadUserList()
          await loadUserStats()
          return
        }
        ElMessage.error(response.message || '操作失败')
      } catch {
        ElMessage.error('操作失败，请重试')
      } finally {
        formDialog.submitting = false
      }
    })
  }

  async function handleDelete(row) {
    try {
      await ElMessageBox.confirm(
        `确定要删除用户「${row.realName}」吗？删除后不可恢复。`,
        '删除确认',
        { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
      )
      const response = await deleteUserRecord(row.id)
      if (response.code === 200) {
        ElMessage.success('删除成功')
        await loadUserList()
        await loadUserStats()
        return
      }
      ElMessage.error(response.message || '删除失败')
    } catch (error) {
      if (error !== 'cancel') {
        ElMessage.error('删除失败，请重试')
      }
    }
  }

  async function handleStatusChange(row) {
    const newStatus = row.status === 0 ? 1 : 0
    const label = newStatus === 0 ? '启用' : '禁用'
    try {
      await ElMessageBox.confirm(
        `确定要${label}用户「${row.realName}」吗？`,
        '状态确认',
        { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
      )
      const response = await updateUserRecordStatus(row.id, newStatus)
      if (response.code === 200) {
        ElMessage.success(`${label}成功`)
        row.status = newStatus
        return
      }
      ElMessage.error(response.message || '操作失败')
    } catch {
      // ignore cancel
    }
  }

  async function handleViewDetail(row) {
    try {
      const response = await fetchUserDetail(row.id)
      if (response.code === 200) {
        detailDialog.data = response.data
        detailDialog.visible = true
      }
    } catch {
      ElMessage.error('加载用户详情失败')
    }
  }

  async function openHealthDialog(row) {
    try {
      const response = await fetchUserHealthStats(row.userCode)
      if (response.code === 200) {
        healthDialog.data = response.data
        healthDialog.visible = true
      }
    } catch {
      ElMessage.error('加载健康统计失败')
    }
  }

  function resetForm() {
    userFormRef.value?.resetFields()
    formDialog.form = createEmptyUserForm()
  }

  onMounted(() => {
    void loadUserStats()
    void loadUserList()
    void loadAvailableRoles()
    void loadDepartments()
  })

  return {
    availableRoles,
    currentTime,
    deptList,
    detailDialog,
    formDialog,
    formRules,
    formatDate,
    handleAdd,
    handleCurrentChange,
    handleDelete,
    handleEdit,
    handleFormSubmit,
    handleReset,
    handleSearch,
    handleSizeChange,
    handleStatusChange,
    handleViewDetail,
    hasPerm,
    healthDialog,
    loading,
    openHealthDialog,
    pagination,
    resetForm,
    rolesLoading,
    searchForm,
    stats,
    userFormRef,
    userList
  }
}

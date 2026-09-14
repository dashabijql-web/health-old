import { nextTick, onMounted, reactive, ref } from 'vue'
import { useStore } from 'vuex'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatDate } from '@/utils'
import { useClock } from '@/composables/useClock'
import {
  createRoleRecord,
  deleteRoleRecord,
  fetchPermissionTree,
  fetchRoleDetail,
  fetchRoleList,
  fetchRolePermissionIds,
  fetchRoleStats,
  fetchRoleUsers,
  saveRolePermissions,
  updateRoleRecord
} from './role-management-runtime'
import {
  buildRoleFormFromDetail,
  buildRoleSubmitPayload,
  createEmptyRoleForm,
  resolveExpandedPermissionKeys
} from './role-management-view-model'

export function useRoleManagementPage() {
  const store = useStore()
  const { currentTime } = useClock('YYYY/MM/DD HH:mm:ss')

  const loading = ref(false)
  const roleList = ref([])
  const stats = reactive({
    totalRoles: 0,
    activeRoles: 0,
    inactiveRoles: 0
  })
  const searchForm = reactive({
    keyword: '',
    status: null
  })
  const pagination = reactive({
    page: 1,
    size: 20,
    total: 0
  })

  const formDialog = reactive({
    visible: false,
    isEdit: false,
    submitting: false,
    form: createEmptyRoleForm()
  })

  const permDialog = reactive({
    visible: false,
    loading: false,
    submitting: false,
    roleId: null,
    roleName: '',
    roleCode: '',
    treeData: [],
    checkedKeys: [],
    expandedKeys: [],
    checkedCount: 0
  })

  const usersDialog = reactive({
    visible: false,
    loading: false,
    roleName: '',
    users: []
  })

  const roleFormRef = ref(null)
  const permTreeRef = ref(null)

  const formRules = {
    roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
    roleCode: [
      { required: true, message: '请输入角色编码', trigger: 'blur' },
      { pattern: /^[A-Z_]+$/, message: '角色编码只能包含大写字母和下划线', trigger: 'blur' }
    ]
  }

  function hasPerm(code) {
    const buttons = store.getters.buttons
    if (!buttons || buttons.length === 0) {
      return true
    }
    return buttons.includes(code)
  }

  async function loadRoleStats() {
    try {
      const response = await fetchRoleStats()
      if (response.code === 200) {
        Object.assign(stats, response.data)
      }
    } catch {
      // ignore to preserve current UX
    }
  }

  async function loadRoleList() {
    loading.value = true
    try {
      const response = await fetchRoleList({
        ...searchForm,
        page: pagination.page,
        size: pagination.size
      })
      if (response.code === 200) {
        roleList.value = response.data.list
        pagination.total = response.data.total
      }
    } catch {
      ElMessage.error('加载角色列表失败')
    } finally {
      loading.value = false
    }
  }

  function handleSearch() {
    pagination.page = 1
    void loadRoleList()
  }

  function handleReset() {
    searchForm.keyword = ''
    searchForm.status = null
    handleSearch()
  }

  function handleSizeChange(size) {
    pagination.size = size
    void loadRoleList()
  }

  function handleCurrentChange(page) {
    pagination.page = page
    void loadRoleList()
  }

  function handleAdd() {
    formDialog.isEdit = false
    formDialog.form = createEmptyRoleForm()
    formDialog.visible = true
  }

  async function handleEdit(row) {
    try {
      const response = await fetchRoleDetail(row.id)
      if (response.code === 200) {
        formDialog.form = buildRoleFormFromDetail(response.data)
        formDialog.isEdit = true
        formDialog.visible = true
      }
    } catch {
      ElMessage.error('加载角色信息失败')
    }
  }

  function handleFormSubmit() {
    roleFormRef.value.validate(async (valid) => {
      if (!valid) {
        return
      }
      formDialog.submitting = true
      try {
        const payload = buildRoleSubmitPayload(formDialog.form)
        const response = formDialog.isEdit
          ? await updateRoleRecord(payload)
          : await createRoleRecord(payload)
        if (response.code === 200) {
          ElMessage.success(formDialog.isEdit ? '修改成功' : '新增成功')
          roleFormRef.value?.clearValidate()
          formDialog.visible = false
          await loadRoleList()
          await loadRoleStats()
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
        `确定要删除角色「${row.roleName}」吗？删除后不可恢复，该角色下的用户权限将被清除。`,
        '删除确认',
        { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
      )
      const response = await deleteRoleRecord(row.id)
      if (response.code === 200) {
        ElMessage.success('删除成功')
        await loadRoleList()
        await loadRoleStats()
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
        `确定要${label}角色「${row.roleName}」吗？`,
        '状态确认',
        { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
      )
      const response = await updateRoleRecord({
        id: row.id,
        roleName: row.roleName,
        roleCode: row.roleCode,
        description: row.description,
        status: newStatus
      })
      if (response.code === 200) {
        ElMessage.success(`${label}成功`)
        row.status = newStatus
        await loadRoleStats()
        return
      }
      ElMessage.error(response.message || '操作失败')
    } catch {
      // ignore cancel
    }
  }

  async function handleAssignPermissions(row) {
    permDialog.roleId = row.id
    permDialog.roleName = row.roleName
    permDialog.roleCode = row.roleCode
    permDialog.treeData = []
    permDialog.checkedKeys = []
    permDialog.expandedKeys = []
    permDialog.checkedCount = 0
    permDialog.loading = true
    permDialog.visible = true

    try {
      const [treeResponse, permissionResponse] = await Promise.all([
        fetchPermissionTree(),
        fetchRolePermissionIds(row.id)
      ])

      if (treeResponse.code === 200) {
        permDialog.treeData = treeResponse.data
        permDialog.expandedKeys = resolveExpandedPermissionKeys(treeResponse.data)
      }

      if (permissionResponse.code === 200) {
        permDialog.checkedKeys = permissionResponse.data
        await nextTick()
        permTreeRef.value?.setCheckedKeys(permissionResponse.data)
        updatePermCheckedCount()
      }
    } catch {
      ElMessage.error('加载权限数据失败')
    } finally {
      permDialog.loading = false
    }
  }

  function updatePermCheckedCount() {
    const tree = permTreeRef.value
    if (!tree) {
      return
    }
    permDialog.checkedCount = tree.getCheckedKeys().length + tree.getHalfCheckedKeys().length
  }

  async function handleSavePermissions() {
    const tree = permTreeRef.value
    const permissionIds = tree.getCheckedKeys()

    permDialog.submitting = true
    try {
      const response = await saveRolePermissions(permDialog.roleId, permissionIds)
      if (response.code === 200) {
        ElMessage.success('权限保存成功')
        permDialog.visible = false
        return
      }
      ElMessage.error(response.message || '保存失败')
    } catch {
      ElMessage.error('保存失败，请重试')
    } finally {
      permDialog.submitting = false
    }
  }

  function checkAll() {
    const setChecked = (nodes) => {
      nodes.forEach((node) => {
        permTreeRef.value.setChecked(node.id, true, false)
        if (node.children) {
          setChecked(node.children)
        }
      })
    }
    setChecked(permDialog.treeData)
    updatePermCheckedCount()
  }

  function uncheckAll() {
    permTreeRef.value.setCheckedKeys([])
    updatePermCheckedCount()
  }

  function expandAll() {
    const expand = (nodes) => {
      nodes.forEach((node) => {
        permTreeRef.value.store.nodesMap[node.id].expanded = true
        if (node.children) {
          expand(node.children)
        }
      })
    }
    expand(permDialog.treeData)
  }

  async function handleViewUsers(row) {
    usersDialog.roleName = row.roleName
    usersDialog.visible = true
    usersDialog.loading = true
    try {
      const response = await fetchRoleUsers(row.id)
      if (response.code === 200) {
        usersDialog.users = response.data
      }
    } catch {
      ElMessage.error('加载角色用户失败')
    } finally {
      usersDialog.loading = false
    }
  }

  function resetForm() {
    roleFormRef.value?.resetFields()
    formDialog.form = createEmptyRoleForm()
  }

  onMounted(() => {
    void loadRoleStats()
    void loadRoleList()
  })

  return {
    currentTime,
    formDialog,
    formRules,
    formatDate,
    handleAdd,
    handleAssignPermissions,
    handleCurrentChange,
    handleDelete,
    handleEdit,
    handleFormSubmit,
    handleReset,
    handleSavePermissions,
    handleSearch,
    handleSizeChange,
    handleStatusChange,
    handleViewUsers,
    hasPerm,
    loading,
    pagination,
    permDialog,
    permTreeRef,
    resetForm,
    roleFormRef,
    roleList,
    searchForm,
    stats,
    updatePermCheckedCount,
    usersDialog,
    checkAll,
    uncheckAll,
    expandAll
  }
}

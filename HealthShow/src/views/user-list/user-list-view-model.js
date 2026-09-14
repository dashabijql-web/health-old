export function createEmptyUserForm() {
  return {
    id: null,
    realName: '',
    nickname: '',
    phone: '',
    email: '',
    password: '',
    deptId: null,
    gender: 0,
    statusBool: true,
    roleIds: [],
    remark: ''
  }
}

export function buildUserFormFromDetail(detail) {
  return {
    id: detail.id,
    realName: detail.realName || '',
    nickname: detail.nickname || '',
    phone: detail.phone || '',
    email: detail.email || '',
    password: '',
    deptId: detail.deptId || null,
    gender: detail.gender ?? 0,
    statusBool: detail.status === 0,
    roleIds: detail.roleIds || [],
    remark: detail.remark || ''
  }
}

export function buildUserSubmitPayload(formDialog) {
  const payload = {
    ...formDialog,
    status: formDialog.statusBool ? 0 : 1
  }
  delete payload.statusBool
  if (!payload.password) {
    delete payload.password
  }
  return payload
}

export function buildDepartmentOptionList(rawList) {
  return rawList.map((dept) => ({
    id: dept.id,
    name: dept.deptName || dept.dept_name || dept.name
  }))
}

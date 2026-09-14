export function createEmptyRoleForm() {
  return {
    id: null,
    roleName: '',
    roleCode: '',
    description: '',
    statusBool: true
  }
}

export function buildRoleFormFromDetail(detail) {
  return {
    id: detail.id,
    roleName: detail.roleName || '',
    roleCode: detail.roleCode || '',
    description: detail.description || '',
    statusBool: detail.status === 0
  }
}

export function buildRoleSubmitPayload(form) {
  return {
    ...form,
    status: form.statusBool ? 0 : 1
  }
}

export function resolveExpandedPermissionKeys(treeData) {
  return treeData.map((node) => node.id)
}

import {
  createEmployee,
  deleteEmployee,
  getEmployeeListDetail,
  updateEmployee
} from '@/api/employee'
import { getDepartmentList } from '@/api/department'
import { getJobTypeList } from '@/api/job-type'
import { generateDepartmentReport, generateEmployeeReport } from '@/api/ai'

export async function fetchEmployeeArchiveEmployees() {
  const response = await getEmployeeListDetail()
  if (response.code !== 200) {
    return []
  }
  return (response.data || []).map((employee) => ({
    ...employee,
    _healthScore: employee.healthScore ?? null
  }))
}

export async function fetchEmployeeArchiveOptions() {
  const [departmentResponse, jobTypeResponse] = await Promise.all([
    getDepartmentList(),
    getJobTypeList()
  ])

  return {
    deptOptions: departmentResponse.code === 200
      ? (departmentResponse.data?.list || departmentResponse.data || [])
      : [],
    jobTypeOptions: jobTypeResponse.code === 200
      ? (jobTypeResponse.data?.list || jobTypeResponse.data || [])
      : []
  }
}

export function createEmployeeArchiveEmployee(payload) {
  return createEmployee(payload)
}

export function updateEmployeeArchiveEmployee(payload) {
  return updateEmployee(payload)
}

export function deleteEmployeeArchiveEmployee(id) {
  return deleteEmployee(id)
}

export function generateEmployeeArchiveAiReport(empCode) {
  return generateEmployeeReport(empCode)
}

export function generateEmployeeArchiveDeptReport(deptName) {
  return generateDepartmentReport(deptName)
}

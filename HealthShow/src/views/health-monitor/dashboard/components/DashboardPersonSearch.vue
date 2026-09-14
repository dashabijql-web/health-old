<template>
  <section class="person-command-search" aria-label="人员快速检索">
    <div class="person-command-search__label">
      <el-icon><Search /></el-icon>
      <span>人员健康画像</span>
    </div>
    <el-select
      v-model="selectedCode"
      class="person-command-search__select"
      filterable
      remote
      clearable
      reserve-keyword
      :loading="loading"
      :remote-method="search"
      placeholder="输入姓名、工号、手机号或 IMEI"
      no-data-text="未找到匹配人员"
      @visible-change="handleVisibleChange"
      @change="handleSelect"
    >
      <el-option
        v-for="person in results"
        :key="person.empCode"
        :label="`${person.empName || '--'} ${person.empCode || ''}`"
        :value="person.empCode"
      >
        <div class="person-search-option">
          <div class="person-search-option__identity">
            <strong>{{ person.empName || '--' }}</strong>
            <code>{{ person.empCode || '--' }}</code>
          </div>
          <div class="person-search-option__meta">
            <span>{{ person.deptName || '未分配部门' }}</span>
            <span :class="['person-search-option__status', person.online ? 'is-online' : 'is-offline']">
              {{ person.online ? '设备在线' : person.imei ? '设备离线' : '未绑定设备' }}
            </span>
          </div>
        </div>
      </el-option>
    </el-select>
    <span class="person-command-search__hint">选中后进入完整健康画像，可继续联系或应急处置</span>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { searchEmployeesForCommand } from '@/api/employee'

interface CommandEmployee {
  empCode: string
  empName?: string
  deptName?: string
  online?: boolean
  imei?: string
  [key: string]: unknown
}

interface ApiResult<T> {
  code: number
  data?: T
}

const fetchCommandEmployees = searchEmployeesForCommand as unknown as (
  query: string,
  limit: number
) => Promise<ApiResult<CommandEmployee[]>>

const emit = defineEmits<{
  select: [person: CommandEmployee]
}>()

const selectedCode = ref('')
const results = ref<CommandEmployee[]>([])
const loading = ref(false)
let requestSequence = 0

async function search(query = '') {
  const sequence = ++requestSequence
  loading.value = true
  try {
    const response = await fetchCommandEmployees(query.trim(), 12)
    if (sequence === requestSequence) {
      results.value = response.code === 200 ? (response.data || []) : []
    }
  } catch {
    if (sequence === requestSequence) results.value = []
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}

function handleVisibleChange(visible: boolean) {
  if (visible && results.value.length === 0) void search('')
}

function handleSelect(empCode?: string) {
  if (!empCode) return
  const person = results.value.find((item) => item.empCode === empCode)
  if (person) emit('select', person)
  selectedCode.value = ''
}
</script>

<style scoped lang="scss">
.person-command-search {
  min-height: 52px;
  display: grid;
  grid-template-columns: auto minmax(280px, 520px) minmax(0, 1fr);
  align-items: center;
  gap: 14px;
  padding: 8px 16px;
  border: 1px solid rgba(0, 200, 255, .18);
  border-radius: 8px;
  background: rgba(5, 16, 34, .9);
}

.person-command-search__label {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: #dceeff;
  font-size: 13px;
  font-weight: 700;
  white-space: nowrap;

  .el-icon { color: #00c8ff; }
}

.person-command-search__select { width: 100%; }

.person-command-search__hint {
  min-width: 0;
  color: rgba(139, 166, 200, .72);
  font-size: 11px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.person-search-option {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.person-search-option__identity,
.person-search-option__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.person-search-option__identity strong { color: #e8f4ff; }
.person-search-option__identity code { color: #6ca6c8; font-size: 11px; }
.person-search-option__meta { color: #7895ae; font-size: 11px; }
.person-search-option__status.is-online { color: #35d07f; }
.person-search-option__status.is-offline { color: #e0a24b; }

@media (max-width: 768px) {
  .person-command-search {
    grid-template-columns: 1fr;
    gap: 7px;
    padding: 10px 12px;
  }

  .person-command-search__hint { white-space: normal; }
}
</style>

<template>
  <PersonDetailDrawer
    :visible="personVisible"
    :user-code="person.empCode || ''"
    :user-name="person.empName || ''"
    :dept-name="person.deptName || ''"
    :imei="person.imei || ''"
    :online="online"
    mode="contact"
    @update:visible="emit('update:personVisible', $event)"
    @emergency="emit('emergency', $event)"
  />

  <IncidentCommandDrawer
    :visible="incidentVisible"
    :event="incident ?? undefined"
    source-page="employee-profile"
    @update:visible="emit('update:incidentVisible', $event)"
    @updated="emit('updated')"
  />
</template>

<script setup lang="ts">
import type { PropType } from 'vue'
import IncidentCommandDrawer from '@/views/safety-command/components/IncidentCommandDrawer.vue'
import PersonDetailDrawer from '@/views/safety-command/components/PersonDetailDrawer.vue'

interface EmployeeIdentity {
  empCode?: string
  empName?: string
  deptName?: string
  imei?: string
  [key: string]: unknown
}

type IncidentEvent = Record<string, unknown>

defineProps({
  personVisible: { type: Boolean, default: false },
  incidentVisible: { type: Boolean, default: false },
  person: { type: Object as PropType<EmployeeIdentity>, default: () => ({}) },
  incident: { type: Object as PropType<IncidentEvent | null>, default: null },
  online: { type: Boolean, default: false }
})

const emit = defineEmits<{
  'update:personVisible': [value: boolean]
  'update:incidentVisible': [value: boolean]
  emergency: [event: unknown]
  updated: []
}>()
</script>

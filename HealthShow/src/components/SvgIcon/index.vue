<template><svg :class="svgClass" aria-hidden="true"><use :xlink:href="iconName" /></svg></template>
<script setup lang="ts">
import { computed } from 'vue'

interface SvgIconProps {
  iconClass: string
  className?: string
}

const props = withDefaults(defineProps<SvgIconProps>(), {
  className: ''
})

const iconName = computed<string>(() => '#icon-' + props.iconClass)
const svgClass = computed<string>(() => props.className ? 'svg-icon ' + props.className : 'svg-icon')
</script>
<style scoped>.svg-icon { width:1em;height:1em;vertical-align:-0.15em;fill:currentColor;overflow:hidden }</style>

<template>
  <el-breadcrumb class="app-breadcrumb" separator="/">
    <transition-group name="breadcrumb">
      <el-breadcrumb-item v-for="(item,index) in levelList" :key="item.path">
        <span v-if="item.redirect==='noRedirect'||index==levelList.length-1" class="no-redirect">{{ item.meta.title }}</span>
        <a v-else @click.prevent="handleLink(item)">{{ item.meta.title }}</a>
      </el-breadcrumb-item>
    </transition-group>
  </el-breadcrumb>
</template>

<script setup lang="ts">
import { compile } from 'path-to-regexp'
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { RouteLocationRaw, RouteRecordName, RouteRecordRedirectOption } from 'vue-router'

interface BreadcrumbItem {
  path: string
  name?: RouteRecordName
  redirect?: RouteRecordRedirectOption
  meta: {
    title: string
    breadcrumb?: boolean
  }
}

const route = useRoute()
const router = useRouter()
const levelList = ref<BreadcrumbItem[]>([])

function isDashboard(item?: BreadcrumbItem) {
  const name = item?.name
  if (!name) {
    return false
  }
  return String(name).trim().toLocaleLowerCase() === 'HealthMonitor'.toLocaleLowerCase()
}

function getBreadcrumb() {
  // Only show routes with meta.title.
  let matched: BreadcrumbItem[] = route.matched
    .filter(item => item.meta && item.meta.title)
    .map(item => ({
      path: item.path,
      name: item.name,
      redirect: item.redirect,
      meta: {
        title: String(item.meta.title),
        breadcrumb: item.meta.breadcrumb as boolean | undefined
      }
    }))
  const first = matched[0]

  if (!isDashboard(first)) {
    matched = [{ path: '/health-monitor/dashboard', meta: { title: '健康监测' } }, ...matched]
  }

  levelList.value = matched.filter(item => item.meta.title && item.meta.breadcrumb !== false)
}

function pathCompile(path: string) {
  // To solve this problem https://github.com/PanJiaChen/vue-element-admin/issues/561
  const toPath = compile(path)
  return toPath(route.params as Record<string, string | string[]>)
}

function handleLink(item: BreadcrumbItem) {
  const { redirect, path } = item
  if (redirect) {
    router.push(redirect as RouteLocationRaw)
    return
  }
  router.push(pathCompile(path))
}

watch(() => route.fullPath, getBreadcrumb, { immediate: true })
</script>

<style lang="scss" scoped>
.app-breadcrumb.el-breadcrumb {
  display: inline-block;
  font-size: 14px;
  line-height: 50px;
  margin-left: 8px;
  .no-redirect {
    color: #97a8be;
    cursor: text;
  }
  a {
    color: #00d4ff;
    transition: color 0.3s ease;
    &:hover { color: #00ffff; }
  }
  :deep(.el-breadcrumb__separator) {
    color: #5a7ba6;
  }
}
</style>

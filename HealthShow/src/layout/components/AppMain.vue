<template>
  <section class="app-main">
    <router-view v-slot="{ Component, route }">
      <!-- Keep the outgoing page until the next view is ready. out-in fades the shell to blank. -->
      <component :is="Component" :key="route.path" />
    </router-view>
  </section>
</template>

<script setup lang="ts">
defineOptions({ name: 'AppMain' })
</script>

<style scoped>
.app-main {
  min-height: calc(100vh - 50px);
  width: 100%;
  position: relative;
  overflow-x: hidden;
  overflow-y: auto;
}

@media (max-width: 768px) {
  .app-main {
    padding-bottom: calc(var(--layout-mobile-nav-height) + env(safe-area-inset-bottom, 8px));
  }
}

.fixed-header + .app-main {
  padding-top: 50px;
}
</style>

<style lang="scss">
.el-popup-parent--hidden {
  .fixed-header {
    padding-right: 15px;
  }
}
</style>

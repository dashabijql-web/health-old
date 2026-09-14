<template>
  <div>
    <div id="stage"></div>
    <main class="hud" v-if="state.current">
      <section class="top">
        <div>
          <div class="eyebrow">HOLOGRAPHIC SPECIMEN / BROWSER LAB</div>
          <h1>{{ state.current.name }}</h1>
          <div class="subtitle">{{ state.current.sub }}</div>
        </div>
        <div class="status"><b>LIVE</b><br>REALTIME PARTICLE FIELD</div>
      </section>
      <section class="bottom">
        <div class="hint">DRAG TO ROTATE · WHEEL TO ZOOM · AUTO ROTATION {{ state.auto ? 'ON' : 'OFF' }}</div>
        <div class="controls"><button @click="toggle">{{ state.auto ? 'PAUSE ROTATION' : 'AUTO ROTATE' }}</button></div>
      </section>
    </main>
    <nav class="switcher">
      <button v-for="(model, i) in models" :key="model.name" :class="{ active: i === state.index }" @click="select(i)">0{{ i + 1 }}</button>
    </nav>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { models, state, boot, showModel } from './scene.js'
onMounted(() => boot())
function select(i) { showModel(i) }
function toggle() { state.auto = !state.auto }
</script>

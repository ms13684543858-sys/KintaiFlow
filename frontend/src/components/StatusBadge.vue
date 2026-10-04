<script setup>
// 状態バッジ。map は utils/format.js の REQUEST_STATUS / STEP_STATUS を渡す。
import { REQUEST_STATUS } from '../utils/format'
const props = defineProps({
  status: { type: String, default: '' },
  map: { type: Object, default: () => REQUEST_STATUS }
})
</script>

<template>
  <span class="badge" :class="'badge-' + (map[status]?.tone ?? 'gray')">{{ map[status]?.label ?? status }}</span>
</template>

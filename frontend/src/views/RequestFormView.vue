<script setup>
// 新規申請フォーム。使用 API: GET /api/leave-types, GET /api/leave-balances, POST /api/requests
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AppLayout from '../components/AppLayout.vue'
import { api } from '../api/client'
import { dateOf, UNIT } from '../utils/format'

const router = useRouter()
const today = dateOf(new Date())

const requestType = ref('LEAVE')
const leaveTypeId = ref('')
const unit = ref('FULL')
const startDate = ref('')
const endDate = ref('')
const targetDate = ref('')
const correctedClockIn = ref('')
const correctedClockOut = ref('')
const reason = ref('')

const leaveTypes = ref([])
const balances = ref([])
const loading = ref(true)
const busy = ref(false)
const error = ref('')

const selectedType = computed(() => leaveTypes.value.find(t => String(t.id) === String(leaveTypeId.value)))
const halfAllowed = computed(() => selectedType.value?.allowHalfDay !== false)
const isHalf = computed(() => unit.value !== 'FULL')
const balance = computed(() => {
  if (selectedType.value?.maxDaysRule !== 'LIMITED') return null
  return balances.value.find(b => String(b.leaveTypeId) === String(leaveTypeId.value)) ?? null
})

watch(leaveTypeId, () => { if (!halfAllowed.value) unit.value = 'FULL' })
watch([unit, startDate], () => { if (isHalf.value && startDate.value) endDate.value = startDate.value })

onMounted(async () => {
  try {
    const res = await api('GET', '/api/leave-types')
    leaveTypes.value = res?.leaveTypes ?? []
  } catch (e) { error.value = e.message } finally { loading.value = false }
  // 残日数は参考表示。失敗しても画面は使える
  try {
    const res = await api('GET', '/api/leave-balances')
    balances.value = res?.balances ?? []
  } catch { balances.value = [] }
})

async function submit(asDraft) {
  error.value = ''
  let body
  if (requestType.value === 'LEAVE') {
    if (!leaveTypeId.value) { error.value = '休暇種別を選択してください。'; return }
    if (!startDate.value) { error.value = '開始日を入力してください。'; return }
    const end = isHalf.value ? startDate.value : (endDate.value || startDate.value)
    body = {
      requestType: 'LEAVE', leaveTypeId: Number(leaveTypeId.value), unit: unit.value,
      startDate: startDate.value, endDate: end, reason: reason.value.trim() || null, asDraft
    }
  } else {
    if (!targetDate.value) { error.value = '対象日を入力してください。'; return }
    if (!correctedClockIn.value && !correctedClockOut.value) { error.value = '修正後の出勤時刻または退勤時刻を入力してください。'; return }
    if (!reason.value.trim()) { error.value = '理由を入力してください。'; return }
    body = {
      requestType: 'CLOCK_CORRECTION', startDate: targetDate.value, endDate: targetDate.value,
      correctedClockIn: correctedClockIn.value || null, correctedClockOut: correctedClockOut.value || null,
      reason: reason.value.trim(), asDraft
    }
  }
  busy.value = true
  try {
    const res = await api('POST', '/api/requests', body)
    router.push(`/requests/${res.requestId}`)
  } catch (e) { error.value = e.message } finally { busy.value = false }
}
</script>

<template>
  <AppLayout>
    <div class="page-head">
      <h2>新規申請</h2>
      <router-link to="/requests" class="btn btn-ghost">一覧へ戻る</router-link>
    </div>

    <section class="card">
      <p v-if="loading" class="muted">読み込み中…</p>
      <form v-else class="form-grid" @submit.prevent="submit(false)">
        <label class="field wide">
          申請種別<span class="req">必須</span>
          <select v-model="requestType">
            <option value="LEAVE">休暇申請</option>
            <option value="CLOCK_CORRECTION">打刻修正申請</option>
          </select>
        </label>

        <template v-if="requestType === 'LEAVE'">
          <label class="field">
            休暇種別<span class="req">必須</span>
            <select v-model="leaveTypeId">
              <option value="" disabled>選択してください</option>
              <option v-for="t in leaveTypes" :key="t.id" :value="t.id">{{ t.name }}</option>
            </select>
          </label>
          <label class="field">
            取得単位<span class="req">必須</span>
            <select v-model="unit">
              <option value="FULL">{{ UNIT.FULL }}</option>
              <option value="AM" :disabled="!halfAllowed">{{ UNIT.AM }}</option>
              <option value="PM" :disabled="!halfAllowed">{{ UNIT.PM }}</option>
            </select>
            <span v-if="!halfAllowed" class="hint">この休暇種別は半日単位で取得できません。</span>
          </label>
          <label class="field">
            開始日<span class="req">必須</span>
            <input v-model="startDate" type="date" />
          </label>
          <label class="field">
            終了日
            <input v-model="endDate" type="date" :min="startDate" :disabled="isHalf" />
            <span v-if="isHalf" class="hint">半日休は開始日と同日になります。</span>
          </label>
          <p v-if="balance" class="alert alert-info wide">
            {{ balance.leaveTypeName }} の残日数：<strong>{{ Number(balance.remainingDays).toFixed(1) }}日</strong>
            （付与 {{ Number(balance.grantedDays).toFixed(1) }}日／取得済み {{ Number(balance.usedDays).toFixed(1) }}日）
          </p>
        </template>

        <template v-else>
          <label class="field wide">
            対象日<span class="req">必須</span>
            <input v-model="targetDate" type="date" :max="today" />
          </label>
          <label class="field">
            修正後 出勤
            <input v-model="correctedClockIn" type="time" />
          </label>
          <label class="field">
            修正後 退勤
            <input v-model="correctedClockOut" type="time" />
          </label>
        </template>

        <label class="field wide">
          <span>理由<span v-if="requestType === 'CLOCK_CORRECTION'" class="req">必須</span></span>
          <textarea v-model="reason" rows="3" maxlength="200" />
          <span class="hint">200文字以内</span>
        </label>

        <p v-if="error" class="alert alert-error wide" role="alert">{{ error }}</p>

        <div class="actions-row wide">
          <button type="submit" class="btn" :disabled="busy">申請する</button>
          <button type="button" class="btn btn-ghost" :disabled="busy" @click="submit(true)">下書き保存</button>
        </div>
      </form>
    </section>
  </AppLayout>
</template>

<style scoped>
a.btn { text-decoration: none; }
</style>

<script setup>
// 部下の月次集計画面。使用 API: GET /api/manager/subordinates/summary?month=yyyy-MM
import { ref, computed, onMounted } from 'vue'
import AppLayout from '../components/AppLayout.vue'
import { api } from '../api/client'
import { fmtMinutes, fmtDays, ymOf } from '../utils/format'

const OVERTIME_LIMIT = 45 * 60

const month = ref(ymOf(new Date()))
const rows = ref([])
const loading = ref(false)
const error = ref('')

const total = computed(() => rows.value.reduce((s, r) => ({
  workDays: s.workDays + r.workDays,
  workMinutes: s.workMinutes + r.workMinutes,
  overtimeMinutes: s.overtimeMinutes + r.overtimeMinutes,
  leaveDays: s.leaveDays + Number(r.leaveDays)
}), { workDays: 0, workMinutes: 0, overtimeMinutes: 0, leaveDays: 0 }))

async function load() {
  if (!month.value) return
  loading.value = true; error.value = ''
  try {
    const res = await api('GET', `/api/manager/subordinates/summary?month=${month.value}`)
    rows.value = res.rows
  } catch (e) { error.value = e.message; rows.value = [] } finally { loading.value = false }
}
onMounted(load)
</script>

<template>
  <AppLayout>
    <div class="page-head"><h2>部下の月次集計</h2></div>

    <div class="toolbar">
      <div class="field">
        <label>
          対象月
          <input v-model="month" type="month" @change="load" />
        </label>
      </div>
      <button class="btn btn-ghost" :disabled="loading" @click="load">再読込</button>
    </div>

    <p v-if="loading" class="muted">読み込み中…</p>
    <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>

    <section class="card">
      <p class="muted legend">残業時間が45時間を超える場合は赤字で表示します。</p>
      <div class="table-wrap">
        <table class="table">
          <thead>
            <tr>
              <th>氏名</th><th>部署</th>
              <th class="num">出勤日数</th><th class="num">勤務時間</th>
              <th class="num">残業時間</th><th class="num">休暇日数</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in rows" :key="r.userId">
              <td>{{ r.userName }}</td>
              <td>{{ r.departmentName }}</td>
              <td class="num">{{ r.workDays }}日</td>
              <td class="num">{{ fmtMinutes(r.workMinutes) }}</td>
              <td class="num" :class="{ over: r.overtimeMinutes > OVERTIME_LIMIT }">
                {{ fmtMinutes(r.overtimeMinutes) }}<span v-if="r.overtimeMinutes > OVERTIME_LIMIT" class="warn"> ⚠ 45時間超</span>
              </td>
              <td class="num">{{ fmtDays(r.leaveDays) }}</td>
            </tr>
            <tr v-if="!rows.length && !loading"><td colspan="6" class="empty">該当する部下のデータがありません。</td></tr>
          </tbody>
          <tfoot v-if="rows.length">
            <tr>
              <th colspan="2">合計</th>
              <th class="num">{{ total.workDays }}日</th>
              <th class="num">{{ fmtMinutes(total.workMinutes) }}</th>
              <th class="num">{{ fmtMinutes(total.overtimeMinutes) }}</th>
              <th class="num">{{ fmtDays(total.leaveDays) }}</th>
            </tr>
          </tfoot>
        </table>
      </div>
    </section>
  </AppLayout>
</template>

<style scoped>
.legend { margin: 0 0 12px; font-size: 13px; }
.over { color: var(--danger); font-weight: 700; }
.warn { font-size: 12px; }
.table tfoot th { padding: 10px 12px; text-align: left; background: #f8fafd; border-top: 2px solid var(--border); }
.table tfoot th.num { text-align: right; }
</style>

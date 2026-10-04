<script setup>
// 祝日・会社休日管理（管理者専用）
// 使用 API: GET /api/admin/holidays?year= / POST /api/admin/holidays / DELETE /api/admin/holidays/{id}
import { ref, onMounted } from 'vue'
import AppLayout from '../components/AppLayout.vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import { api } from '../api/client'
import { fmtDate, dowOf } from '../utils/format'

const year = ref(new Date().getFullYear())
const holidays = ref([])
const loading = ref(false)
const error = ref('')
const message = ref('')

const newDate = ref('')
const newName = ref('')
const adding = ref(false)
const addError = ref('')

const delTarget = ref(null)
const delBusy = ref(false)

async function load() {
  const y = Number(year.value)
  if (!Number.isInteger(y) || y < 1900 || y > 2100) { error.value = '年は1900〜2100の数値で入力してください。'; return }
  loading.value = true; error.value = ''
  try {
    const res = await api('GET', `/api/admin/holidays?year=${y}`)
    holidays.value = res.holidays ?? []
  } catch (e) { error.value = e.message } finally { loading.value = false }
}

function shift(d) { year.value = Number(year.value) + d; message.value = ''; load() }

async function add() {
  addError.value = ''; message.value = ''
  if (!newDate.value) { addError.value = '日付は必須入力です。'; return }
  if (!newName.value.trim()) { addError.value = '名称は必須入力です。'; return }
  adding.value = true
  try {
    await api('POST', '/api/admin/holidays', { holidayDate: newDate.value, name: newName.value.trim() })
    message.value = `${fmtDate(newDate.value)} を登録しました。`
    const y = Number(newDate.value.substring(0, 4))
    newDate.value = ''; newName.value = ''
    if (y !== Number(year.value)) year.value = y
    await load()
  } catch (e) { addError.value = e.message } finally { adding.value = false }
}

async function doDelete() {
  const h = delTarget.value
  delBusy.value = true
  try {
    await api('DELETE', `/api/admin/holidays/${h.id}`)
    message.value = `${fmtDate(h.holidayDate)}（${h.name}）を削除しました。`
    delTarget.value = null
    await load()
  } catch (e) { error.value = e.message; delTarget.value = null } finally { delBusy.value = false }
}

onMounted(load)
</script>

<template>
  <AppLayout>
    <div class="page-head">
      <h2>祝日・会社休日</h2>
    </div>

    <p v-if="message" class="alert alert-ok" role="status">{{ message }}</p>
    <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>

    <section class="card">
      <h3>休日を追加</h3>
      <form class="toolbar" @submit.prevent="add">
        <label class="field">日付<span class="req">必須</span>
          <input v-model="newDate" type="date" />
        </label>
        <label class="field">名称<span class="req">必須</span>
          <input v-model="newName" type="text" maxlength="100" placeholder="例：創立記念日" />
        </label>
        <button class="btn" type="submit" :disabled="adding">追加する</button>
      </form>
      <p v-if="addError" class="alert alert-error" role="alert">{{ addError }}</p>
    </section>

    <section class="card">
      <form class="toolbar" @submit.prevent="load">
        <button class="btn btn-ghost" type="button" :disabled="loading" @click="shift(-1)">‹ 前年</button>
        <label class="field">年
          <input v-model.number="year" type="number" min="1900" max="2100" style="width: 120px" />
        </label>
        <button class="btn" type="submit" :disabled="loading">表示</button>
        <button class="btn btn-ghost" type="button" :disabled="loading" @click="shift(1)">翌年 ›</button>
      </form>

      <p v-if="loading" class="muted">読み込み中…</p>
      <div class="table-wrap">
        <table class="table">
          <thead><tr><th>日付</th><th>曜日</th><th>名称</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="h in holidays" :key="h.id">
              <td>{{ fmtDate(h.holidayDate) }}</td>
              <td>{{ dowOf(h.holidayDate) }}</td>
              <td>{{ h.name }}</td>
              <td><button class="btn btn-danger btn-sm" @click="delTarget = h">削除</button></td>
            </tr>
            <tr v-if="!loading && !holidays.length"><td colspan="4" class="empty">{{ year }}年の登録はありません。</td></tr>
          </tbody>
        </table>
      </div>
    </section>

    <ConfirmDialog
      v-if="delTarget"
      title="休日の削除"
      :message="`${fmtDate(delTarget.holidayDate)}（${delTarget.name}）を削除します。よろしいですか？`"
      ok-label="削除する"
      danger
      :busy="delBusy"
      @ok="doDelete"
      @cancel="delTarget = null"
    />
  </AppLayout>
</template>

<script setup>
// 休暇付与管理（管理者専用）
// 使用 API: GET /api/admin/users?keyword= / GET /api/admin/leave-types
//           GET /api/admin/leave-grants?userId=&leaveTypeId= / POST /api/admin/leave-grants / PUT /api/admin/leave-grants/{id}
import { ref, reactive, computed, onMounted } from 'vue'
import AppLayout from '../components/AppLayout.vue'
import { api } from '../api/client'
import { fmtDate, fmtDays, dateOf } from '../utils/format'

const keyword = ref('')
const users = ref([])
const usersLoading = ref(false)
const userId = ref('')
const leaveTypes = ref([])
const filterTypeId = ref('')

const info = ref(null) // LeaveGrantListResponse
const loading = ref(false)
const error = ref('')
const message = ref('')

const addForm = reactive({ leaveTypeId: '', grantedOn: dateOf(new Date()), grantedDays: '', note: '' })
const showAdd = ref(false)
const adding = ref(false)
const addError = ref('')

const adjTarget = ref(null)
const adjForm = reactive({ grantedDays: '', note: '' })
const adjBusy = ref(false)
const adjError = ref('')

// 付与対象にできるのは有効かつ「上限あり」の種別のみ（API 仕様）
const grantableTypes = computed(() => leaveTypes.value.filter(t => t.isActive && t.maxDaysRule === 'LIMITED'))

async function searchUsers() {
  usersLoading.value = true; error.value = ''
  try {
    const p = new URLSearchParams({ status: 'ACTIVE' })
    if (keyword.value.trim()) p.set('keyword', keyword.value.trim())
    const res = await api('GET', `/api/admin/users?${p}`)
    users.value = res.users ?? []
    if (userId.value !== '' && !users.value.some(u => u.id === Number(userId.value))) {
      userId.value = ''; info.value = null; showAdd.value = false
    }
  } catch (e) { error.value = e.message } finally { usersLoading.value = false }
}

async function loadTypes() {
  try {
    const res = await api('GET', '/api/admin/leave-types')
    leaveTypes.value = res.leaveTypes ?? []
  } catch (e) { error.value = e.message }
}

async function loadGrants() {
  if (userId.value === '') { info.value = null; return }
  loading.value = true; error.value = ''
  try {
    const p = new URLSearchParams({ userId: userId.value })
    if (filterTypeId.value !== '') p.set('leaveTypeId', filterTypeId.value)
    info.value = await api('GET', `/api/admin/leave-grants?${p}`)
  } catch (e) { error.value = e.message; info.value = null } finally { loading.value = false }
}

function onSelectUser() { message.value = ''; showAdd.value = false; loadGrants() }

function openAdd() {
  Object.assign(addForm, { leaveTypeId: '', grantedOn: dateOf(new Date()), grantedDays: '', note: '' })
  addError.value = ''; message.value = ''; showAdd.value = true
}

async function submitAdd() {
  addError.value = ''
  if (addForm.leaveTypeId === '') { addError.value = '休暇種別は必須入力です。'; return }
  if (!addForm.grantedOn) { addError.value = '付与日は必須入力です。'; return }
  if (addForm.grantedDays === '' || addForm.grantedDays == null) { addError.value = '付与日数は必須入力です。'; return }
  adding.value = true
  try {
    await api('POST', '/api/admin/leave-grants', {
      userId: Number(userId.value),
      leaveTypeId: Number(addForm.leaveTypeId),
      grantedOn: addForm.grantedOn,
      grantedDays: Number(addForm.grantedDays),
      note: addForm.note.trim() || null
    })
    message.value = '休暇を付与しました。'
    showAdd.value = false
    await loadGrants()
  } catch (e) { addError.value = e.message } finally { adding.value = false }
}

function openAdjust(g) {
  adjTarget.value = g
  adjForm.grantedDays = Number(g.grantedDays); adjForm.note = ''
  adjError.value = ''; message.value = ''
}

async function submitAdjust() {
  if (adjForm.grantedDays === '' || adjForm.grantedDays == null) { adjError.value = '付与日数は必須入力です。'; return }
  adjBusy.value = true; adjError.value = ''
  try {
    await api('PUT', `/api/admin/leave-grants/${adjTarget.value.id}`, {
      grantedDays: Number(adjForm.grantedDays),
      note: adjForm.note.trim() || null
    })
    message.value = '付与日数を調整しました。'
    adjTarget.value = null
    await loadGrants()
  } catch (e) { adjError.value = e.message } finally { adjBusy.value = false }
}

onMounted(() => { searchUsers(); loadTypes() })
</script>

<template>
  <AppLayout>
    <div class="page-head">
      <h2>休暇付与</h2>
    </div>

    <p v-if="message" class="alert alert-ok" role="status">{{ message }}</p>
    <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>

    <section class="card">
      <form class="toolbar" @submit.prevent="searchUsers">
        <label class="field">従業員を検索
          <input v-model="keyword" type="text" placeholder="氏名・メール" />
        </label>
        <button class="btn btn-ghost" type="submit" :disabled="usersLoading">検索</button>
        <label class="field">従業員<span class="req">必須</span>
          <select v-model="userId" @change="onSelectUser">
            <option value="">選択してください</option>
            <option v-for="u in users" :key="u.id" :value="u.id">{{ u.name }}（{{ u.email }}）</option>
          </select>
        </label>
        <label class="field">休暇種別で絞込
          <select v-model="filterTypeId" :disabled="userId === ''" @change="loadGrants">
            <option value="">すべて</option>
            <option v-for="t in leaveTypes" :key="t.id" :value="t.id">{{ t.name }}</option>
          </select>
        </label>
      </form>
      <p v-if="usersLoading" class="muted">読み込み中…</p>
      <p v-else-if="!users.length" class="muted">該当する従業員がいません。</p>
    </section>

    <template v-if="userId !== ''">
      <p v-if="loading" class="muted">読み込み中…</p>

      <template v-if="info">
        <section class="card">
          <h3>{{ info.userName }} さん</h3>
          <dl class="kv">
            <dt>入社日</dt><dd>{{ fmtDate(info.hireDate) }}</dd>
            <dt>次回付与予定日</dt><dd>{{ fmtDate(info.nextGrantDate) }}</dd>
            <dt>次回法定付与日数</dt><dd>{{ fmtDays(info.nextStatutoryDays) }}</dd>
          </dl>
        </section>

        <section v-if="showAdd" class="card">
          <h3>付与を追加</h3>
          <form @submit.prevent="submitAdd">
            <div class="form-grid">
              <label class="field">休暇種別<span class="req">必須</span>
                <select v-model="addForm.leaveTypeId">
                  <option value="">選択してください</option>
                  <option v-for="t in grantableTypes" :key="t.id" :value="t.id">{{ t.name }}</option>
                </select>
              </label>
              <label class="field">付与日<span class="req">必須</span>
                <input v-model="addForm.grantedOn" type="date" />
              </label>
              <label class="field">付与日数<span class="req">必須</span>
                <input v-model="addForm.grantedDays" type="number" step="0.5" min="0" />
              </label>
              <label class="field">メモ
                <input v-model="addForm.note" type="text" maxlength="200" />
              </label>
            </div>
            <p v-if="addError" class="alert alert-error" role="alert" style="margin-top: 16px">{{ addError }}</p>
            <div class="actions-row" style="margin-top: 16px">
              <button class="btn" type="submit" :disabled="adding">付与する</button>
              <button class="btn btn-ghost" type="button" :disabled="adding" @click="showAdd = false">キャンセル</button>
            </div>
          </form>
        </section>

        <section class="card">
          <div class="page-head">
            <h3>付与履歴</h3>
            <button class="btn" :disabled="showAdd" @click="openAdd">付与を追加</button>
          </div>
          <div class="table-wrap">
            <table class="table">
              <thead>
                <tr>
                  <th>種別</th><th>付与日</th>
                  <th class="num">付与日数</th><th class="num">使用</th><th class="num">残</th>
                  <th>失効日</th><th>状態</th><th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="g in info.grants" :key="g.id">
                  <td>{{ g.leaveTypeName }}</td>
                  <td>{{ fmtDate(g.grantedOn) }}</td>
                  <td class="num">{{ fmtDays(g.grantedDays) }}</td>
                  <td class="num">{{ fmtDays(g.usedDays) }}</td>
                  <td class="num">{{ fmtDays(g.remainingDays) }}</td>
                  <td>{{ fmtDate(g.expiresOn) }}</td>
                  <td><span v-if="g.expired" class="badge badge-gray">失効済み</span><span v-else class="badge badge-green">有効</span></td>
                  <td><button class="btn btn-ghost btn-sm" @click="openAdjust(g)">日数を調整</button></td>
                </tr>
                <tr v-if="!info.grants.length"><td colspan="8" class="empty">付与履歴はありません。</td></tr>
              </tbody>
            </table>
          </div>
        </section>
      </template>
    </template>
    <p v-else class="muted">従業員を選択すると付与履歴を表示します。</p>

    <div v-if="adjTarget" class="modal-back" @click.self="adjTarget = null">
      <form class="modal" role="dialog" aria-modal="true" @submit.prevent="submitAdjust">
        <h3>付与日数の調整</h3>
        <p class="modal-msg">
          {{ adjTarget.leaveTypeName }}（付与日 {{ fmtDate(adjTarget.grantedOn) }}／現在 {{ fmtDays(adjTarget.grantedDays) }}、使用 {{ fmtDays(adjTarget.usedDays) }}）
        </p>
        <div class="stack">
          <label class="field">新しい付与日数<span class="req">必須</span>
            <input v-model="adjForm.grantedDays" type="number" step="0.5" min="0" />
          </label>
          <label class="field">メモ
            <input v-model="adjForm.note" type="text" maxlength="200" />
          </label>
        </div>
        <p v-if="adjError" class="alert alert-error" role="alert" style="margin-top: 12px">{{ adjError }}</p>
        <div class="modal-actions">
          <button class="btn btn-ghost" type="button" :disabled="adjBusy" @click="adjTarget = null">キャンセル</button>
          <button class="btn" type="submit" :disabled="adjBusy">調整する</button>
        </div>
      </form>
    </div>
  </AppLayout>
</template>

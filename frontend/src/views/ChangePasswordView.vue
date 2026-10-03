<script setup>
// API-023 POST /api/auth/password
import { ref, computed } from 'vue'
import AppLayout from '../components/AppLayout.vue'
import { api, auth } from '../api/client'

const current = ref('')
const next = ref('')
const confirm = ref('')
const errorMessage = ref('')
const done = ref(false)
const loading = ref(false)
const forced = computed(() => auth.user()?.mustChangePassword === true)

async function onSubmit() {
  errorMessage.value = ''
  done.value = false
  if (!current.value || !next.value) { errorMessage.value = 'すべての項目を入力してください。'; return }
  if (next.value !== confirm.value) { errorMessage.value = '新しいパスワードが一致しません。'; return }
  loading.value = true
  try {
    await api('POST', '/api/auth/password', { currentPassword: current.value, newPassword: next.value })
    auth.markPasswordChanged()
    done.value = true
    current.value = next.value = confirm.value = ''
  } catch (e) {
    errorMessage.value = e.message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AppLayout>
    <h2>パスワード変更</h2>
    <p v-if="forced" class="alert alert-info" style="margin-top: 16px">
      初期パスワードのままです。先にパスワードを変更してください。
    </p>
    <form class="form" @submit.prevent="onSubmit" novalidate>
      <label class="field">現在のパスワード
        <input v-model="current" type="password" autocomplete="current-password" />
      </label>
      <label class="field">新しいパスワード
        <input v-model="next" type="password" autocomplete="new-password" />
        <span class="hint">12文字以上、英字と数字を両方含める</span>
      </label>
      <label class="field">新しいパスワード（確認）
        <input v-model="confirm" type="password" autocomplete="new-password" />
      </label>
      <p v-if="errorMessage" class="alert alert-error" role="alert">{{ errorMessage }}</p>
      <p v-if="done" class="alert alert-ok" role="status">パスワードを変更しました。</p>
      <button class="btn" type="submit" :disabled="loading">{{ loading ? '変更中…' : '変更する' }}</button>
    </form>
  </AppLayout>
</template>

<style scoped>
.form { display: flex; flex-direction: column; gap: 18px; max-width: 420px; margin-top: 24px; }
</style>

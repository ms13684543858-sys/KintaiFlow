<script setup>
// SCR-001 ログイン画面 / API-001 POST /api/auth/login
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { api, auth } from '../api/client'

const router = useRouter()
const email = ref('')
const password = ref('')
const errorMessage = ref('')
const loading = ref(false)

async function onSubmit() {
  errorMessage.value = ''
  if (!email.value.trim() || !password.value) {
    errorMessage.value = 'メールアドレスとパスワードを入力してください。'
    return
  }
  loading.value = true
  try {
    const res = await api('POST', '/api/auth/login', { email: email.value.trim(), password: password.value })
    auth.save(res)
    // 初期パスワードのままのユーザーは、先にパスワード変更へ誘導する
    router.push(res.mustChangePassword ? '/password' : '/home')
  } catch (e) {
    errorMessage.value = e.message
    password.value = ''
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page">
    <section class="intro">
      <h1>勤怠フロー</h1>
      <p class="tag">打刻・休暇申請・承認を、ひとつに。</p>
    </section>
    <form class="card" @submit.prevent="onSubmit" novalidate>
      <h2>ログイン</h2>
      <label class="field">メールアドレス
        <input v-model="email" type="email" autocomplete="username" placeholder="name@example.com" />
      </label>
      <label class="field">パスワード
        <input v-model="password" type="password" autocomplete="current-password" />
      </label>
      <p v-if="errorMessage" class="alert alert-error" role="alert">{{ errorMessage }}</p>
      <button class="btn" type="submit" :disabled="loading">{{ loading ? '認証中…' : 'ログイン' }}</button>
    </form>
  </div>
</template>

<style scoped>
.page { min-height: 100vh; display: grid; grid-template-columns: 1fr 1fr; }
.intro {
  display: flex; flex-direction: column; justify-content: center; padding: 0 64px; color: #fff;
  background: linear-gradient(160deg, var(--navy-900), var(--navy-700));
}
.intro h1 { font-size: 40px; letter-spacing: .06em; }
.tag { margin-top: 12px; color: #c9d5ee; font-size: 17px; }
.card { align-self: center; justify-self: center; width: 380px; display: flex; flex-direction: column; gap: 18px; padding: 8px; }
.card h2 { font-size: 24px; color: var(--navy-900); }
@media (max-width: 760px) {
  .page { grid-template-columns: 1fr; }
  .intro { padding: 40px 24px; }
  .card { width: 100%; padding: 24px; }
}
</style>

<script setup>
// ログイン後の共通外枠（ヘッダー＋本文）。画面ごとのメニューは機能追加時にここへ足す。
import { useRouter } from 'vue-router'
import { auth } from '../api/client'

const router = useRouter()
const user = auth.user()
const roleLabel = { EMPLOYEE: '社員', MANAGER: '上長', ADMIN: '管理者' }

function logout() { auth.clear(); router.push('/login') }
</script>

<template>
  <div class="shell">
    <header class="bar">
      <div class="brand">勤怠フロー</div>
      <nav class="menu">
        <router-link to="/home">ホーム</router-link>
        <router-link to="/password">パスワード変更</router-link>
      </nav>
      <div class="who">
        <span class="name">{{ user?.name }}</span>
        <span class="role">{{ roleLabel[user?.role] ?? user?.role }}</span>
        <button class="btn btn-ghost out" @click="logout">ログアウト</button>
      </div>
    </header>
    <main class="body"><slot /></main>
  </div>
</template>

<style scoped>
.bar { display: flex; align-items: center; gap: 24px; padding: 0 24px; height: 56px; color: #fff; background: var(--navy-900); }
.brand { font-size: 18px; font-weight: 700; letter-spacing: .05em; }
.menu { display: flex; gap: 4px; flex: 1; }
.menu a { padding: 6px 12px; color: #c9d5ee; text-decoration: none; border-radius: 6px; font-size: 14px; }
.menu a.router-link-active { color: #fff; background: var(--navy-700); }
.who { display: flex; align-items: center; gap: 10px; font-size: 14px; }
.role { padding: 1px 8px; font-size: 12px; color: var(--navy-900); background: #c9d5ee; border-radius: 10px; }
.out { padding: 4px 12px; font-size: 13px; color: #fff; border-color: #4a5f8a; }
.out:hover:not(:disabled) { background: var(--navy-700); }
.body { max-width: 960px; margin: 32px auto; padding: 0 24px; }
</style>

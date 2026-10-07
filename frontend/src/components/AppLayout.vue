<script setup>
// ログイン後の共通外枠：上部バー（通知ベル・ユーザー）＋左メニュー（ロール別）＋本文。
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { auth, api } from '../api/client'
import { ROLE } from '../utils/format'

const router = useRouter()
const route = useRoute()
const user = auth.user()
const locked = user?.mustChangePassword === true // 初期パスワードのまま：変更が終わるまで他の画面は使えない
const role = user?.role
const open = ref(false)           // モバイルのメニュー開閉
const unread = ref(0)
let timer

const menu = computed(() => {
  const groups = [
    { title: '勤怠', items: [
      { to: '/home', label: '打刻・勤怠' },
      { to: '/requests', label: '申請' },
      { to: '/leave', label: '休暇残日数' },
      { to: '/notifications', label: '通知', badge: unread.value }
    ] }
  ]
  if (role === 'MANAGER' || role === 'ADMIN') {
    const items = [{ to: '/approvals', label: '承認待ち' }]
    if (role === 'MANAGER') items.push({ to: '/manager/summary', label: '部下の勤怠集計' })
    groups.push({ title: '承認', items })
  }
  if (role === 'ADMIN') {
    groups.push({ title: '管理', items: [
      { to: '/admin/reports', label: '月次集計' },
      { to: '/admin/users', label: 'ユーザー管理' },
      { to: '/admin/leave-grants', label: '休暇付与' },
      { to: '/admin/leave-types', label: '休暇種別' },
      { to: '/admin/holidays', label: '祝日・会社休日' }
    ] })
  }
  return groups
})

async function loadUnread() {
  try {
    const res = await api('GET', '/api/notifications')
    unread.value = res.notifications.filter(n => !n.isRead).length
  } catch { /* 通知の取得失敗は画面操作を妨げない */ }
}

function logout() { auth.clear(); router.push('/login') }

onMounted(() => { loadUnread(); timer = setInterval(loadUnread, 60000) })
onBeforeUnmount(() => clearInterval(timer))
watch(() => route.fullPath, () => { open.value = false; loadUnread() })
</script>

<template>
  <div class="shell">
    <header class="bar">
      <button class="burger" aria-label="メニュー" @click="open = !open">☰</button>
      <div class="brand">勤怠フロー</div>
      <div class="grow" />
      <router-link to="/notifications" class="bell" aria-label="通知">
        🔔<span v-if="unread" class="dot">{{ unread > 99 ? '99+' : unread }}</span>
      </router-link>
      <div class="who">
        <span class="name">{{ user?.name }}</span>
        <span class="role">{{ ROLE[role] ?? role }}</span>
        <router-link to="/password" class="pw">パスワード変更</router-link>
        <button class="btn btn-ghost out" @click="logout">ログアウト</button>
      </div>
    </header>
    <div class="layout">
      <nav class="side" :class="{ open, locked }">
        <div v-if="locked" class="lock-note">パスワードを変更するとメニューが使えるようになります。</div>
        <div v-for="g in menu" :key="g.title" class="group">
          <div class="gtitle">{{ g.title }}</div>
          <router-link v-for="i in g.items" :key="i.to" :to="i.to" class="item">
            {{ i.label }}<span v-if="i.badge" class="count">{{ i.badge }}</span>
          </router-link>
        </div>
      </nav>
      <main class="body"><slot /></main>
    </div>
  </div>
</template>

<style scoped>
.bar { position: sticky; top: 0; z-index: 20; display: flex; align-items: center; gap: 16px; padding: 0 20px; height: 56px; color: #fff; background: var(--navy-900); }
.brand { font-size: 18px; font-weight: 700; letter-spacing: .05em; }
.grow { flex: 1; }
.burger { display: none; padding: 4px 10px; font-size: 20px; color: #fff; background: transparent; border: 0; cursor: pointer; }
.bell { position: relative; font-size: 18px; text-decoration: none; }
.dot { position: absolute; top: -8px; right: -12px; min-width: 18px; padding: 0 5px; font-size: 11px; line-height: 18px; text-align: center; color: #fff; background: var(--danger); border-radius: 9px; }
.who { display: flex; align-items: center; gap: 10px; font-size: 14px; }
.role { padding: 1px 8px; font-size: 12px; color: var(--navy-900); background: #c9d5ee; border-radius: 10px; }
.pw { color: #c9d5ee; font-size: 13px; text-decoration: none; }
.pw:hover { text-decoration: underline; }
.out { padding: 4px 12px; font-size: 13px; color: #fff; border-color: #4a5f8a; }
.out:hover:not(:disabled) { background: var(--navy-700); }
.layout { display: flex; min-height: calc(100vh - 56px); }
.side { flex: 0 0 210px; padding: 16px 12px; background: #fff; border-right: 1px solid var(--border); }
.group + .group { margin-top: 18px; }
.gtitle { padding: 0 10px 4px; font-size: 12px; font-weight: 600; color: var(--muted); }
.item { display: flex; align-items: center; justify-content: space-between; padding: 8px 10px; color: var(--text); text-decoration: none; font-size: 14px; border-radius: 6px; }
.item:hover { background: var(--navy-100); }
.side.locked .group { opacity: .4; pointer-events: none; }
.lock-note { margin: 0 4px 12px; padding: 8px 10px; font-size: 12px; color: var(--muted); background: var(--navy-100); border-radius: 8px; }
.item.router-link-active { color: #fff; background: var(--navy-700); }
.count { min-width: 20px; padding: 0 6px; font-size: 12px; text-align: center; color: #fff; background: var(--danger); border-radius: 10px; }
.body { flex: 1; min-width: 0; max-width: 1100px; margin: 0 auto; padding: 28px 24px; }
@media (max-width: 900px) {
  .burger { display: block; }
  .name, .pw { display: none; }
  .side { position: fixed; top: 56px; bottom: 0; left: 0; z-index: 30; transform: translateX(-100%); transition: transform .2s; }
  .side.open { transform: none; box-shadow: 4px 0 20px rgba(0, 0, 0, .2); }
  .body { padding: 20px 14px; }
}
</style>

<script setup>
import AppLayout from '../components/AppLayout.vue'
import { auth } from '../api/client'

const user = auth.user()
</script>

<template>
  <AppLayout>
    <h2>ようこそ、{{ user?.name }} さん</h2>
    <p class="alert alert-info" style="margin-top: 16px">
      仮のホーム画面です。打刻画面（SCR-102）などは、次の機能で順に追加します。
    </p>
  </AppLayout>
</template>

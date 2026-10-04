<script setup>
// 確認ダイアログ。コメント入力欄（commentLabel を渡すと表示）付き。
// 使い方: <ConfirmDialog v-if="dlg" title="..." :comment-label="..." :comment-required="true" @ok="(c) => ..." @cancel="dlg = null" />
import { ref } from 'vue'
const props = defineProps({
  title: { type: String, required: true },
  message: { type: String, default: '' },
  okLabel: { type: String, default: '実行する' },
  danger: { type: Boolean, default: false },
  commentLabel: { type: String, default: '' },
  commentRequired: { type: Boolean, default: false },
  busy: { type: Boolean, default: false }
})
const emit = defineEmits(['ok', 'cancel'])
const comment = ref('')
const tried = ref(false)

function ok() {
  tried.value = true
  if (props.commentLabel && props.commentRequired && !comment.value.trim()) return
  emit('ok', comment.value.trim())
}
</script>

<template>
  <div class="modal-back" @click.self="emit('cancel')">
    <div class="modal" role="dialog" aria-modal="true">
      <h3>{{ title }}</h3>
      <p v-if="message" class="modal-msg">{{ message }}</p>
      <label v-if="commentLabel" class="field">
        {{ commentLabel }}<span v-if="commentRequired" class="req">必須</span>
        <textarea v-model="comment" rows="3" maxlength="500" />
        <span v-if="tried && commentRequired && !comment.trim()" class="field-error">コメントを入力してください。</span>
      </label>
      <div class="modal-actions">
        <button class="btn btn-ghost" :disabled="busy" @click="emit('cancel')">キャンセル</button>
        <button class="btn" :class="{ 'btn-danger': danger }" :disabled="busy" @click="ok">{{ okLabel }}</button>
      </div>
    </div>
  </div>
</template>
